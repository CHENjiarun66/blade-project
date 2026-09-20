package com.blade.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
            "whatsapp:analyze"
    );

    private final AgentKeyMapper keyMapper;
    private final AgentKeyOutletMapper outletMapper;
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

    public AgentKeyManagementDTO.Credential create(AgentKeyManagementDTO.CreateRequest request) {
        // 新建 Key 在 Series B/E UI 接入前保持 NONE，不授予任何档口。
        return issue(request.name(), request.scopes(), request.expiresInDays(), null, null);
    }

    @Transactional
    public AgentKeyManagementDTO.Credential rotate(Long id, AgentKeyManagementDTO.RotateRequest request) {
        AgentKey previous = requiredKey(id);
        if (!Integer.valueOf(AgentKey.STATUS_ACTIVE).equals(previous.getStatus())) {
            throw BusinessException.of(400, "已停用的Key不能轮换，请创建新Key");
        }
        AgentKeyManagementDTO.Credential credential = issue(
                previous.getName(), requestedRotationScopes(previous, request), request.expiresInDays(),
                previous.getId(), previous.getOutletScopeType());
        // 在同一事务内复制旧 Key 的档口范围与有效绑定到新 Key，保留默认档口标记。
        copyOutletScopeAndBindings(previous, credential.id());
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
                "customers:create", "whatsapp:analyze");
    }

    private List<String> requestedRotationScopes(AgentKey previous, AgentKeyManagementDTO.RotateRequest request) {
        if (request.scopes() == null) {
            return splitScopes(previous.getScopes());
        }
        return request.scopes();
    }

    /**
     * 复制旧 Key 的有效档口绑定到新 Key（与 rotate 同事务）。
     * 保留每条绑定的默认档口标记；绑定与新旧 Key 均受租户拦截约束，不跨租户。
     */
    private void copyOutletScopeAndBindings(AgentKey previous, Long newKeyId) {
        Long tenantId = requiredTenantId();
        List<AgentKeyOutlet> bindings = outletMapper.selectList(
                Wrappers.<AgentKeyOutlet>lambdaQuery()
                        .eq(AgentKeyOutlet::getAgentKeyId, previous.getId())
                        .eq(AgentKeyOutlet::getStatus, 1));
        for (AgentKeyOutlet binding : bindings) {
            AgentKeyOutlet copy = new AgentKeyOutlet();
            copy.setTenantId(tenantId);
            copy.setAgentKeyId(newKeyId);
            copy.setOutletId(binding.getOutletId());
            copy.setIsDefault(binding.getIsDefault());
            copy.setStatus(1);
            outletMapper.insert(copy);
        }
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

    private AgentKeyManagementDTO.Credential issue(String rawName,
                                                    List<String> requestedScopes,
                                                    Integer expiresInDays,
                                                    Long rotatedFromKeyId,
                                                    String outletScopeType) {
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
        key.setOutletScopeType(normalizeOutletScope(outletScopeType));
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(expiresAt);
        key.setCreatedByUserId(currentUserId());
        key.setRotatedFromKeyId(rotatedFromKeyId);
        keyMapper.insert(key);

        return new AgentKeyManagementDTO.Credential(
                key.getId(), key.getName(), prefix + "." + secret, prefix,
                scopes, expiresAt, rotatedFromKeyId);
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
        return new AgentKeyManagementDTO.View(
                key.getId(), key.getName(), key.getKeyPrefix(), splitScopes(key.getScopes()),
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
}
