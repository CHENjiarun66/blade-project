package com.blade.config;

import com.blade.auth.service.JwtTokenProvider;
import com.blade.common.tenant.TenantContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearThreadLocals() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void filePreviewRequestAcceptsActivePreviewTokenAndClearsContextsAfterRequest()
            throws ServletException, IOException {
        Fixture fixture = activeToken("preview-token", 18L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/123/preview");
        request.setParameter("previewToken", "preview-token");

        AtomicReference<Authentication> authenticationDuringRequest = new AtomicReference<>();
        AtomicReference<Long> tenantDuringRequest = new AtomicReference<>();
        fixture.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            authenticationDuringRequest.set(SecurityContextHolder.getContext().getAuthentication());
            tenantDuringRequest.set(TenantContext.getTenantId());
        });

        assertThat(authenticationDuringRequest.get()).isNotNull();
        assertThat(authenticationDuringRequest.get().getName()).isEqualTo("admin");
        assertThat(tenantDuringRequest.get()).isEqualTo(18L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void nonPreviewRequestIgnoresPreviewTokenQueryParam() throws ServletException, IOException {
        Fixture fixture = fixture();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setParameter("previewToken", "preview-token");

        fixture.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(TenantContext.getTenantId()).isNull();
        });

        verify(fixture.tokenProvider, never()).validateToken(anyString());
        verifyNoInteractions(fixture.userDetailsService);
    }

    @Test
    void variantRequestAcceptsActivePreviewToken() throws ServletException, IOException {
        Fixture fixture = activeToken("variant-token", 18L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/123/variant");
        request.setParameter("type", "thumb");
        request.setParameter("previewToken", "variant-token");

        AtomicReference<Authentication> authenticationDuringRequest = new AtomicReference<>();
        fixture.filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                authenticationDuringRequest.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(authenticationDuringRequest.get()).isNotNull();
        verify(fixture.tokenProvider).validateToken("variant-token");
    }

    @Test
    void revokedTokenDoesNotAuthenticateEvenWhenJwtSignatureIsValid() throws ServletException, IOException {
        Fixture fixture = fixture();
        when(fixture.tokenProvider.validateToken("revoked-token")).thenReturn(true);
        when(fixture.tokenProvider.getUsernameFromToken("revoked-token")).thenReturn("admin");
        when(fixture.values.get("token:revoked-token")).thenReturn(null);
        MockHttpServletRequest request = bearerRequest("/api/products", "revoked-token");

        fixture.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(TenantContext.getTenantId()).isNull();
        });

        verifyNoInteractions(fixture.userDetailsService);
    }

    @Test
    void tenantClaimAndRedisTenantMismatchFailsClosed() throws ServletException, IOException {
        Fixture fixture = activeToken("mismatch-token", 18L);
        when(fixture.values.get("token:tenant:mismatch-token")).thenReturn(19L);
        MockHttpServletRequest request = bearerRequest("/api/products", "mismatch-token");

        fixture.filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());

        verifyNoInteractions(fixture.userDetailsService);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    private Fixture activeToken(String token, Long tenantId) {
        Fixture fixture = fixture();
        when(fixture.tokenProvider.validateToken(token)).thenReturn(true);
        when(fixture.tokenProvider.getUsernameFromToken(token)).thenReturn("admin");
        when(fixture.tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
        when(fixture.values.get("token:" + token)).thenReturn("admin");
        when(fixture.values.get("token:tenant:" + token)).thenReturn(tenantId);
        when(fixture.userDetailsService.loadUserByUsername("admin"))
                .thenReturn(new User("admin", "", List.of()));
        return fixture;
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        SecurityConfig.JwtAuthenticationFilter filter =
                new SecurityConfig.JwtAuthenticationFilter(tokenProvider, userDetailsService, redisTemplate);
        return new Fixture(tokenProvider, userDetailsService, values, filter);
    }

    private MockHttpServletRequest bearerRequest(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private record Fixture(
            JwtTokenProvider tokenProvider,
            UserDetailsService userDetailsService,
            ValueOperations<String, Object> values,
            SecurityConfig.JwtAuthenticationFilter filter) {
    }
}
