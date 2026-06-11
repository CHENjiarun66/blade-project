package com.blade.auth;

import com.blade.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void refreshTokenStoresRememberAndTenantIdClaims() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "blade-project-jwt-secret-key-2026");
        ReflectionTestUtils.setField(provider, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(provider, "refreshExpiration", 604_800_000L);
        UserDetails userDetails = User.withUsername("admin")
                .password("password")
                .authorities("data:catalog:view")
                .build();

        String token = provider.generateRefreshToken(userDetails, 2_592_000_000L, true, 18L);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getRememberFromToken(token)).isTrue();
        assertThat(provider.getTenantIdFromToken(token)).isEqualTo(18L);
    }
}
