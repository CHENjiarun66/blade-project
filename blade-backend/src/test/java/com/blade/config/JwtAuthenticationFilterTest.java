package com.blade.config;

import com.blade.auth.service.JwtTokenProvider;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @Test
    void filePreviewRequestAcceptsPreviewTokenQueryParam() throws ServletException, IOException {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(tokenProvider.validateToken("preview-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("preview-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin"))
                .thenReturn(new User("admin", "", List.of()));

        SecurityConfig.JwtAuthenticationFilter filter =
                new SecurityConfig.JwtAuthenticationFilter(tokenProvider, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/123/preview");
        request.setParameter("previewToken", "preview-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void nonPreviewRequestIgnoresPreviewTokenQueryParam() throws ServletException, IOException {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        SecurityConfig.JwtAuthenticationFilter filter =
                new SecurityConfig.JwtAuthenticationFilter(tokenProvider, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setParameter("previewToken", "preview-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(tokenProvider, never()).validateToken(anyString());
            verifyNoInteractions(userDetailsService);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
