package com.blade.agent.auth;

import com.blade.agent.entity.AgentKey;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AgentPrincipal implements Principal {

    private final Long keyId;
    private final Long tenantId;
    private final String keyPrefix;
    private final String displayName;
    private final List<GrantedAuthority> authorities;

    private AgentPrincipal(Long keyId,
                           Long tenantId,
                           String keyPrefix,
                           String displayName,
                           List<GrantedAuthority> authorities) {
        this.keyId = keyId;
        this.tenantId = tenantId;
        this.keyPrefix = keyPrefix;
        this.displayName = displayName;
        this.authorities = authorities;
    }

    public static AgentPrincipal from(AgentKey key) {
        return new AgentPrincipal(
                key.getId(),
                key.getTenantId(),
                key.getKeyPrefix(),
                key.getName(),
                parseAuthorities(key.getScopes()));
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

    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    private static List<GrantedAuthority> parseAuthorities(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .map(scope -> new SimpleGrantedAuthority("agent:" + scope))
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
