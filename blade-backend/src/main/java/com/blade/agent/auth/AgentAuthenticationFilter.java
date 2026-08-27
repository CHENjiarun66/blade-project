package com.blade.agent.auth;

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
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    public static final String AGENT_KEY_HEADER = "X-Agent-Key";

    private final AgentKeyAuthenticationService authenticationService;
    private final AgentCallAuditRecorder auditRecorder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/agent/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader(AGENT_KEY_HEADER);
        AgentPrincipal principal = null;
        long start = System.currentTimeMillis();
        if (rawKey == null || rawKey.isBlank()) {
            writeUnauthorized(response);
            return;
        }
        if (rawKey != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                principal = authenticationService.authenticate(rawKey);
                var authentication = new PreAuthenticatedAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                TenantContext.clear();
                writeUnauthorized(response);
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                if (principal != null) {
                    auditRecorder.record(principal, buildAuditEvent(request, response, start));
                }
            } finally {
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"Agent Key无效\",\"data\":null}");
    }

    private AgentCallAuditEvent buildAuditEvent(HttpServletRequest request,
                                                HttpServletResponse response,
                                                long start) {
        AgentCallAuditEvent event = new AgentCallAuditEvent();
        event.setMethod(request.getMethod());
        event.setPath(request.getRequestURI());
        event.setQueryString(request.getQueryString());
        event.setStatus(response.getStatus());
        event.setDurationMs(System.currentTimeMillis() - start);
        event.setIp(resolveIp(request));
        event.setUserAgent(request.getHeader("User-Agent"));
        return event;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
