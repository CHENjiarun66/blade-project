package com.blade.whatsapp.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public record CollectorPrincipal(Long keyId, Long tenantId, Long accountId, Set<String> scopes) {
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        return scopes.stream().map(scope -> new SimpleGrantedAuthority("collector:" + scope)).toList();
    }

    static CollectorPrincipal of(Long keyId, Long tenantId, Long accountId, String scopes) {
        Set<String> parsed = Arrays.stream(scopes.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        return new CollectorPrincipal(keyId, tenantId, accountId, parsed);
    }
}
