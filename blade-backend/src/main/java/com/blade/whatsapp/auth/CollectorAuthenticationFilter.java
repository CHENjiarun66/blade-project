package com.blade.whatsapp.auth;

import com.blade.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CollectorAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Collector-Key";
    private final CollectorAuthenticationService authenticationService;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/whatsapp/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        try {
            CollectorPrincipal principal = authenticationService.authenticate(request.getHeader(HEADER),
                    request.getHeader("X-Forwarded-For") == null ? request.getRemoteAddr() : request.getHeader("X-Forwarded-For"));
            SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken(
                    principal, null, principal.getAuthorities()));
            chain.doFilter(request, response);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid collector key");
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
