package com.blade.auth;

import com.blade.auth.dto.LoginResponse;
import com.blade.auth.service.AuthService;
import com.blade.auth.service.JwtTokenProvider;
import com.blade.system.tenant.mapper.TenantMapper;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceRefreshTokenTest {

    @Test
    void logoutRevokesAccessSessionAndTenantMapping() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        AuthService authService = new AuthService(
                authenticationManager,
                jwtTokenProvider,
                userDetailsService,
                redisTemplate,
                userMapper,
                tenantMapper,
                3_600_000L,
                604_800_000L,
                2_592_000_000L
        );

        authService.logout("Bearer access-token", "Bearer refresh-token");

        verify(redisTemplate).delete("token:access-token");
        verify(redisTemplate).delete("token:tenant:access-token");
        verify(redisTemplate).delete("token:tenant:refresh-token");
        verify(redisTemplate, never()).delete("token:refresh-token");
    }

    @Test
    void refreshTokenRejectsCryptographicallyValidTokenWhenRedisSessionIsMissing() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("token:tenant:oldRefresh")).thenReturn(null);
        when(jwtTokenProvider.validateToken("oldRefresh")).thenReturn(true);
        when(jwtTokenProvider.isTokenExpired("oldRefresh")).thenReturn(false);
        when(jwtTokenProvider.getTokenTypeFromToken("oldRefresh")).thenReturn("refresh");

        AuthService authService = new AuthService(
                authenticationManager,
                jwtTokenProvider,
                userDetailsService,
                redisTemplate,
                userMapper,
                tenantMapper,
                3_600_000L,
                604_800_000L,
                2_592_000_000L
        );

        assertThatThrownBy(() -> authService.refreshToken("Bearer oldRefresh"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已失效或已被使用");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);

        when(jwtTokenProvider.validateToken("accessToken")).thenReturn(true);
        when(jwtTokenProvider.isTokenExpired("accessToken")).thenReturn(false);
        when(jwtTokenProvider.getTokenTypeFromToken("accessToken")).thenReturn("access");

        AuthService authService = new AuthService(
                authenticationManager,
                jwtTokenProvider,
                userDetailsService,
                redisTemplate,
                userMapper,
                tenantMapper,
                3_600_000L,
                604_800_000L,
                2_592_000_000L
        );

        assertThatThrownBy(() -> authService.refreshToken("Bearer accessToken"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只允许使用 Refresh token");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void refreshTokenWithoutRememberUsesSevenDayRefreshWindow() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        UserDetails userDetails = User.withUsername("admin")
                .password("password")
                .authorities("data:catalog:view")
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("token:tenant:oldRefresh")).thenReturn(18L);
        when(jwtTokenProvider.validateToken("oldRefresh")).thenReturn(true);
        when(jwtTokenProvider.isTokenExpired("oldRefresh")).thenReturn(false);
        when(jwtTokenProvider.getTokenTypeFromToken("oldRefresh")).thenReturn("refresh");
        when(jwtTokenProvider.getUsernameFromToken("oldRefresh")).thenReturn("admin");
        when(jwtTokenProvider.getRememberFromToken("oldRefresh")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(userDetails, 18L)).thenReturn("newAccess");
        when(jwtTokenProvider.generateRefreshToken(userDetails, 604_800_000L, false, 18L))
                .thenReturn("newRefresh");

        AuthService authService = new AuthService(
                authenticationManager,
                jwtTokenProvider,
                userDetailsService,
                redisTemplate,
                userMapper,
                tenantMapper,
                3_600_000L,
                604_800_000L,
                2_592_000_000L
        );

        LoginResponse response = authService.refreshToken("Bearer oldRefresh");

        assertThat(response.getRefreshToken()).isEqualTo("newRefresh");
        verify(jwtTokenProvider).generateRefreshToken(userDetails, 604_800_000L, false, 18L);
        verify(valueOperations).set("token:tenant:newRefresh", 18L, 604_800_000L, TimeUnit.MILLISECONDS);
    }
}
