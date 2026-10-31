package com.blade.auth.service;

import com.blade.system.user.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;

/** Database-backed login identity, retaining user/tenant IDs alongside Spring Security authorities. */
public final class AuthenticatedUserDetails extends org.springframework.security.core.userdetails.User {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final Long tenantId;

    public AuthenticatedUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        super(user.getUsername(), user.getPassword(), Integer.valueOf(1).equals(user.getStatus()),
                true, true, true, authorities);
        this.userId = Objects.requireNonNull(user.getId(), "Authenticated user ID is required");
        this.tenantId = Objects.requireNonNull(user.getTenantId(), "Authenticated tenant ID is required");
    }

    public Long getUserId() { return userId; }
    public Long getTenantId() { return tenantId; }
}
