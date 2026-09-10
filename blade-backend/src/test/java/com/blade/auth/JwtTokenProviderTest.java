package com.blade.auth;

import com.blade.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void accessTokenStoresTenantIdAndTypeClaims() {
        JwtTokenProvider provider = provider();
        UserDetails userDetails = userDetails();

        String token = provider.generateToken(userDetails, 18L);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getTenantIdFromToken(token)).isEqualTo(18L);
        assertThat(provider.getTokenTypeFromToken(token)).isEqualTo("access");
    }

    @Test
    void refreshTokenStoresRememberAndTenantIdClaims() {
        JwtTokenProvider provider = provider();
        UserDetails userDetails = userDetails();

        String token = provider.generateRefreshToken(userDetails, 2_592_000_000L, true, 18L);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getRememberFromToken(token)).isTrue();
        assertThat(provider.getTenantIdFromToken(token)).isEqualTo(18L);
        assertThat(provider.getTokenTypeFromToken(token)).isEqualTo("refresh");
    }

    private JwtTokenProvider provider() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "blade-project-jwt-secret-key-2026");
        ReflectionTestUtils.setField(provider, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(provider, "refreshExpiration", 604_800_000L);
        return provider;
    }

    private UserDetails userDetails() {
        return User.withUsername("admin")
                .password("password")
                .authorities("data:catalog:view")
                .build();
    }
}
