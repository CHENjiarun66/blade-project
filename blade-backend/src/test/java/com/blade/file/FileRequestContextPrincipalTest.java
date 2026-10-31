package com.blade.file;

import com.blade.auth.service.AuthenticatedUserDetails;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileRequestContext;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileRequestContextPrincipalTest {
    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private AuthenticatedUserDetails identity() {
        User user = new User();
        user.setId(42L);
        user.setTenantId(7L);
        user.setUsername("uploader");
        user.setPassword("hashed-password");
        user.setStatus(1);
        return new AuthenticatedUserDetails(user, List.of());
    }

    @Test
    void databaseIdentityResolvesActualUserId() {
        TenantContext.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(identity(), null, List.of()));
        assertEquals(42L, FileRequestContext.requireOperatorId());
    }

    @Test
    void usernameOnlyPrincipalCannotInventAnOperatorId() {
        TenantContext.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new org.springframework.security.core.userdetails.User("uploader", "n/a", List.of()), null, List.of()));
        assertEquals(403, assertThrows(BusinessException.class, FileRequestContext::requireOperatorId).getCode());
    }

    @Test
    void mismatchedOrMissingTenantCannotResolveDatabaseIdentity() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(identity(), null, List.of()));
        TenantContext.setTenantId(8L);
        assertEquals(403, assertThrows(BusinessException.class, FileRequestContext::requireOperatorId).getCode());
        TenantContext.clear();
        assertNull(FileRequestContext.resolveOperatorId());
    }

    @Test
    void anonymousAndUnauthenticatedRequestsHaveNoOperator() {
        TenantContext.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous", identity(), List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertNull(FileRequestContext.resolveOperatorId());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(identity(), "n/a"));
        assertNull(FileRequestContext.resolveOperatorId());
    }
}
