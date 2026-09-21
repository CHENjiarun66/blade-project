package com.blade.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentKeyManagementService {
    private static final int DEFAULT_EXPIRY_DAYS = 90;
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            "catalog:read",
            "products:read",
            "orders:read",
            "customers:read",
            "orders:write",
            "products:create",
            "products:cost:write",
            "orders:cost:write",
            "customers:create",
            "analytics:read",
            "whatsapp:analyze",
            "outlets:read"
    );

    private final AgentKeyMapper keyMapper;
    private final AgentKeyOutletMapper outletMapper;
    private final SalesOutletMapper salesOutletMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<AgentKeyManagementDTO.View> list() {
        LocalDateTime now = LocalDateTime.now();
        return keyMapper.selectList(Wrappers.<AgentKey>lambdaQuery()
                        .orderByDesc(AgentKey::getCreateTime))
                .stream()
                .map(key -> toView(key, now))
                .toList();
    }

    /** 管理者可配置档口：本租户全部未删除档口（含禁用），不受当前账号自身档口范围限制。 */
    public List<AgentKeyManagementDTO.AdminOutletOption> listConfigurableOutlets() {
        Long tenantId = requiredTenantId();
        return salesOutletMapper.selectList(new LambdaQueryWrapper<SalesOutlet>()
                        .eq(SalesOutlet::getTenantId, tenantId)
                        .eq(SalesOutlet::getDeleted, 0)
                        .orderByAsc(SalesOutlet::getSort)
                        .orderByAsc(SalesOutlet::getId))
                .stream()
                .map(outlet -> new AgentKeyManagementDTO.AdminOutletOption(
                        outlet.getId(), outlet.getOutletCode(), outlet.getOutletName(), outlet.getStatus(),
                        Integer.valueOf(1).equals(outlet.getIsTenantDefault())))
                .toList();
    }

    @Transactional
    public AgentKeyManagementDTO.Credential create(AgentKeyManagementDTO.CreateRequest request) {
        // 默认 null 范围安全解释为 NONE，兼容旧客户端；绝不默认 ALL。
        OutletConfig config = normalizeOutletConfig(
                request.outletScopeType(), request.outletIds(), request.defaultOutletId());
        return issue(request.name(), request.scopes(), request.expiresInDays(), null, config);
    }

    @Transactional
    public AgentKeyManagementDTO.Credential rotate(Long id, AgentKeyManagementDTO.RotateRequest request) {
        AgentKey previous = requiredKey(id);
        if (!Integer.valueOf(AgentKey.STATUS_ACTIVE).equals(previous.getStatus())) {
            throw BusinessException.of(400, "已停用的Key不能轮换，请创建新Key");
        }
        OutletConfig config = resolveRotationOutletConfig(previous, request);
        // 先签发新 Key 与绑定，再停旧 Key；同为单事务，任一步失败整体回滚。
        AgentKeyManagementDTO.Credential credential = issue(
                previous.getName(), requestedRotationScopes(previous, request), request.expiresInDays(),
                previous.getId(), config);
        disableEntity(previous);
        keyMapper.updateById(previous);
        return credential;
    }

    @Transactional
    public void disable(Long id) {
        AgentKey key = requiredKey(id);
        if (Integer.valueOf(AgentKey.STATUS_DISABLED).equals(key.getStatus())) {
            return;
        }
        disableEntity(key);
        keyMapper.updateById(key);
    }

    public List<String> allowedScopes() {
        return List.of("catalog:read", "products:read", "orders:read", "customers:read", "analytics:read",
                "orders:write", "products:create", "products:cost:write", "orders:cost:write",
                "customers:create", "whatsapp:analyze", "outlets:read");
    }

    private List<String> requestedRotationScopes(AgentKey previous, AgentKeyManagementDTO.RotateRequest request) {
        if (request.scopes() == null) {
            return splitScopes(previous.getScopes());
        }
        return request.scopes();
    }

    /**
     * 轮换时的档口配置：outlet 相关字段为 null 时分别继承旧 Key；
     * scopeType 被显式改变时，不继承旧绑定/默认（避免 ALL 携带旧 ASSIGNED 绑定等脏配置）。
     */
    private OutletConfig resolveRotationOutletConfig(AgentKey previous,
                                                      AgentKeyManagementDTO.RotateRequest request) {
        OutletConfig previousConfig = readOutletConfig(previous);
        if (request.outletScopeType() != null) {
            return normalizeOutletConfig(request.outletScopeType(), request.outletIds(), request.defaultOutletId());
        }
        List<Long> ids = request.outletIds() != null ? request.outletIds() : previousConfig.outletIds();
        Long defaultOutletId = request.defaultOutletId() != null
                ? request.defaultOutletId() : previousConfig.defaultOutletId();
        return normalizeOutletConfig(previousConfig.scopeType(), ids, defaultOutletId);
    }

    private OutletConfig readOutletConfig(AgentKey key) {
        String scopeType = key.getOutletScopeType() != null
                ? key.getOutletScopeType() : AgentKey.OUTLET_SCOPE_NONE;
        List<AgentKeyOutlet> bindings = activeBindings(key.getId());
        List<Long> ids = bindings.stream()
                .map(AgentKeyOutlet::getOutletId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Long defaultOutletId = bindings.stream()
                .filter(binding -> Integer.valueOf(1).equals(binding.getIsDefault()))
                .map(AgentKeyOutlet::getOutletId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new OutletConfig(scopeType, ids, defaultOutletId);
    }

    private List<AgentKeyOutlet> activeBindings(Long keyId) {
        return outletMapper.selectList(Wrappers.<AgentKeyOutlet>lambdaQuery()
                .eq(AgentKeyOutlet::getAgentKeyId, keyId)
                .eq(AgentKeyOutlet::getStatus, 1));
    }

    private String normalizeOutletScope(String outletScopeType) {
        if (outletScopeType == null || outletScopeType.isBlank()) {
            return AgentKey.OUTLET_SCOPE_NONE;
        }
        String normalized = outletScopeType.trim().toUpperCase();
        return switch (normalized) {
            case AgentKey.OUTLET_SCOPE_ALL, AgentKey.OUTLET_SCOPE_ASSIGNED, AgentKey.OUTLET_SCOPE_NONE -> normalized;
            default -> throw BusinessException.of(400, "不允许的档口范围类型: " + outletScopeType);
        };
    }

    /**
     * 统一校验并去重、稳定排序档口配置：
     * 同租户未删除；NONE 禁止绑定/默认；ALL 不保存冗余绑定，默认须为本租户启用档口；
     * ASSIGNED 至少一个，默认须在集合内且启用（仅一个启用档口时可自动默认）。
     */
    private OutletConfig normalizeOutletConfig(String rawScopeType, List<Long> rawOutletIds, Long rawDefaultOutletId) {
        String scopeType = normalizeOutletScope(rawScopeType);
        Long tenantId = requiredTenantId();
        LinkedHashSet<Long> requested = new LinkedHashSet<>();
        if (rawOutletIds != null) {
            for (Long outletId : rawOutletIds) {
                if (outletId != null) {
                    requested.add(outletId);
                }
            }
        }
        List<Long> ids = requested.stream().sorted().toList();

        if (AgentKey.OUTLET_SCOPE_NONE.equals(scopeType)) {
            if (!ids.isEmpty()) {
                throw BusinessException.of(400, "不开放档口数据时不能指定档口");
            }
            if (rawDefaultOutletId != null) {
                throw BusinessException.of(400, "不开放档口数据时不能设置默认档口");
            }
            return new OutletConfig(scopeType, List.of(), null);
        }

        Map<Long, SalesOutlet> tenantOutlets = loadTenantOutlets(tenantId);

        if (AgentKey.OUTLET_SCOPE_ALL.equals(scopeType)) {
            if (!ids.isEmpty()) {
                throw BusinessException.of(400, "全部档口范围不需要绑定具体档口");
            }
            Long defaultOutletId = null;
            if (rawDefaultOutletId != null) {
                SalesOutlet outlet = tenantOutlets.get(rawDefaultOutletId);
                if (outlet == null || !Integer.valueOf(1).equals(outlet.getStatus())) {
                    throw BusinessException.of(400, "默认档口不存在或未启用");
                }
                defaultOutletId = outlet.getId();
            }
            return new OutletConfig(scopeType, List.of(), defaultOutletId);
        }

        // ASSIGNED
        if (ids.isEmpty()) {
            throw BusinessException.of(400, "指定档口范围至少选择一个档口");
        }
        for (Long outletId : ids) {
            if (!tenantOutlets.containsKey(outletId)) {
                throw BusinessException.of(400, "档口不存在或不属于当前租户: " + outletId);
            }
            // 禁用档口允许保留为历史读绑定，但不能设为默认或用于新建。
        }
        Long defaultOutletId;
        if (rawDefaultOutletId != null) {
            if (!ids.contains(rawDefaultOutletId)) {
                throw BusinessException.of(400, "默认档口必须属于指定档口集合");
            }
            SalesOutlet outlet = tenantOutlets.get(rawDefaultOutletId);
            if (outlet == null || !Integer.valueOf(1).equals(outlet.getStatus())) {
                throw BusinessException.of(400, "默认档口不存在或未启用");
            }
            defaultOutletId = rawDefaultOutletId;
        } else if (ids.size() == 1) {
            SalesOutlet only = tenantOutlets.get(ids.get(0));
            defaultOutletId = only != null && Integer.valueOf(1).equals(only.getStatus()) ? only.getId() : null;
        } else {
            defaultOutletId = null;
        }
        return new OutletConfig(scopeType, ids, defaultOutletId);
    }

    private Map<Long, SalesOutlet> loadTenantOutlets(Long tenantId) {
        if (tenantId == null) {
            return Map.of();
        }
        return salesOutletMapper.selectList(new LambdaQueryWrapper<SalesOutlet>()
                        .eq(SalesOutlet::getTenantId, tenantId)
                        .eq(SalesOutlet::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(SalesOutlet::getId, outlet -> outlet, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private AgentKeyManagementDTO.Credential issue(String rawName,
                                                    List<String> requestedScopes,
                                                    Integer expiresInDays,
                                                    Long rotatedFromKeyId,
                                                    OutletConfig config) {
        String name = rawName == null ? null : rawName.trim();
        if (name == null || name.isEmpty()) {
            throw BusinessException.of(400, "Key名称不能为空");
        }
        List<String> scopes = normalizeScopes(requestedScopes);
        int days = expiresInDays == null ? DEFAULT_EXPIRY_DAYS : expiresInDays;
        if (days < 1 || days > 365) {
            throw BusinessException.of(400, "Key有效期必须为1到365天");
        }

        String prefix = "agk_" + randomToken(12);
        String secret = randomToken(32);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(days);

        AgentKey key = new AgentKey();
        key.setTenantId(requiredTenantId());
        key.setName(name);
        key.setKeyPrefix(prefix);
        key.setKeyHash(passwordEncoder.encode(secret));
        key.setScopes(String.join(",", scopes));
        key.setOutletScopeType(config.scopeType());
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(expiresAt);
        key.setCreatedByUserId(currentUserId());
        key.setRotatedFromKeyId(rotatedFromKeyId);
        keyMapper.insert(key);

        writeOutletBindings(key.getId(), config);

        return new AgentKeyManagementDTO.Credential(
                key.getId(), key.getName(), prefix + "." + secret, prefix,
                scopes, config.scopeType(), config.defaultOutletId(),
                summariesFor(config, key.getTenantId()), expiresAt, rotatedFromKeyId);
    }

    /** 新 Key 独立写入绑定，不复制旧 Key 行，避免重复。 */
    private void writeOutletBindings(Long keyId, OutletConfig config) {
        Long tenantId = requiredTenantId();
        if (AgentKey.OUTLET_SCOPE_ASSIGNED.equals(config.scopeType())) {
            for (Long outletId : config.outletIds()) {
                insertBinding(tenantId, keyId, outletId, outletId.equals(config.defaultOutletId()));
            }
        } else if (AgentKey.OUTLET_SCOPE_ALL.equals(config.scopeType()) && config.defaultOutletId() != null) {
            // ALL 不保存冗余绑定，仅落一条默认档口标记供读取时解析。
            insertBinding(tenantId, keyId, config.defaultOutletId(), true);
        }
    }

    private void insertBinding(Long tenantId, Long keyId, Long outletId, boolean isDefault) {
        AgentKeyOutlet binding = new AgentKeyOutlet();
        binding.setTenantId(tenantId);
        binding.setAgentKeyId(keyId);
        binding.setOutletId(outletId);
        binding.setIsDefault(isDefault ? 1 : 0);
        binding.setStatus(1);
        outletMapper.insert(binding);
    }

    private List<AgentKeyManagementDTO.OutletSummary> summariesFor(OutletConfig config, Long tenantId) {
        List<Long> ids;
        if (AgentKey.OUTLET_SCOPE_ASSIGNED.equals(config.scopeType())) {
            ids = config.outletIds();
        } else if (config.defaultOutletId() != null) {
            ids = List.of(config.defaultOutletId());
        } else {
            return List.of();
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, SalesOutlet> outlets = loadTenantOutlets(tenantId);
        List<AgentKeyManagementDTO.OutletSummary> summaries = new ArrayList<>();
        for (Long outletId : ids) {
            SalesOutlet outlet = outlets.get(outletId);
            if (outlet == null) {
                continue;
            }
            summaries.add(new AgentKeyManagementDTO.OutletSummary(
                    outlet.getId(), outlet.getOutletCode(), outlet.getOutletName(), outlet.getStatus(),
                    outletId.equals(config.defaultOutletId())));
        }
        return summaries;
    }

    private List<String> normalizeScopes(List<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            throw BusinessException.of(400, "至少选择一个scope");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : requestedScopes) {
            String scope = raw == null ? "" : raw.trim();
            if (!ALLOWED_SCOPES.contains(scope)) {
                throw BusinessException.of(400, "不允许的Agent scope: " + scope);
            }
            normalized.add(scope);
        }
        requireScopeDependency(normalized, "products:cost:write", "products:create");
        requireScopeDependency(normalized, "orders:cost:write", "orders:write");
        return List.copyOf(normalized);
    }

    private void requireScopeDependency(Set<String> scopes, String sensitiveScope, String baseScope) {
        if (scopes.contains(sensitiveScope) && !scopes.contains(baseScope)) {
            throw BusinessException.of(400, sensitiveScope + " 必须与 " + baseScope + " 一起授权");
        }
    }

    private AgentKey requiredKey(Long id) {
        AgentKey key = keyMapper.selectById(id);
        if (key == null) {
            throw BusinessException.of(404, "Agent Key不存在");
        }
        return key;
    }

    private void disableEntity(AgentKey key) {
        key.setStatus(AgentKey.STATUS_DISABLED);
        key.setDisabledTime(LocalDateTime.now());
    }

    private AgentKeyManagementDTO.View toView(AgentKey key, LocalDateTime now) {
        OutletConfig config = readOutletConfig(key);
        return new AgentKeyManagementDTO.View(
                key.getId(), key.getName(), key.getKeyPrefix(), splitScopes(key.getScopes()),
                config.scopeType(), config.defaultOutletId(), summariesFor(config, key.getTenantId()),
                key.getStatus(), key.getExpiresTime(),
                key.getExpiresTime() != null && key.getExpiresTime().isBefore(now),
                key.getLastUsedTime(), key.getLastUsedIp(), key.getCreatedByUserId(),
                key.getDisabledTime(), key.getRotatedFromKeyId(), key.getCreateTime());
    }

    private List<String> splitScopes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .toList();
    }

    private Long requiredTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw BusinessException.of(401, "缺少租户上下文");
        return tenantId;
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.selectByUsername(username);
        if (user == null) throw BusinessException.of(401, "当前用户不存在");
        return user.getId();
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record OutletConfig(String scopeType, List<Long> outletIds, Long defaultOutletId) {
    }
}
