package com.blade.agent.auth;

import com.blade.agent.entity.AgentKey;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AgentPrincipal implements Principal {

    private final Long keyId;
    private final Long tenantId;
    private final String keyPrefix;
    private final String displayName;
    private final List<String> scopes;
    private final LocalDateTime expiresTime;
    private final List<GrantedAuthority> authorities;

    private AgentPrincipal(Long keyId,
                           Long tenantId,
                           String keyPrefix,
                           String displayName,
                           List<String> scopes,
                           LocalDateTime expiresTime,
                           List<GrantedAuthority> authorities) {
        this.keyId = keyId;
        this.tenantId = tenantId;
        this.keyPrefix = keyPrefix;
        this.displayName = displayName;
        this.scopes = scopes;
        this.expiresTime = expiresTime;
        this.authorities = authorities;
    }

    public static AgentPrincipal from(AgentKey key) {
        List<String> scopes = parseScopes(key.getScopes());
        return new AgentPrincipal(
                key.getId(),
                key.getTenantId(),
                key.getKeyPrefix(),
                key.getName(),
                scopes,
                key.getExpiresTime(),
                parseAuthorities(scopes));
    }

    @Override
    public String getName() {
        return keyPrefix;
    }

    public Long getKeyId() {
        return keyId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public LocalDateTime getExpiresTime() {
        return expiresTime;
    }

    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    private static List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .distinct()
                .toList();
    }

    private static List<GrantedAuthority> parseAuthorities(List<String> scopes) {
        return scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("agent:" + scope))
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
