package com.blade.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
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
            "orders:write",
            "analytics:read",
            "whatsapp:analyze"
    );

    private final AgentKeyMapper keyMapper;
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
        return issue(request.name(), request.scopes(), request.expiresInDays(), null);
    }

    @Transactional
    public AgentKeyManagementDTO.Credential rotate(Long id, AgentKeyManagementDTO.RotateRequest request) {
        AgentKey previous = requiredKey(id);
        if (!Integer.valueOf(AgentKey.STATUS_ACTIVE).equals(previous.getStatus())) {
            throw BusinessException.of(400, "已停用的Key不能轮换，请创建新Key");
        }
        AgentKeyManagementDTO.Credential credential = issue(
                previous.getName(), splitScopes(previous.getScopes()), request.expiresInDays(), previous.getId());
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
        return List.of("catalog:read", "orders:write", "analytics:read", "whatsapp:analyze");
    }

    private AgentKeyManagementDTO.Credential issue(String rawName,
                                                    List<String> requestedScopes,
                                                    Integer expiresInDays,
                                                    Long rotatedFromKeyId) {
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
        return List.copyOf(normalized);
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
