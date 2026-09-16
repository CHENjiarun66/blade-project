package com.blade.agent;

import com.blade.agent.auth.AgentAuthenticationFilter;
import com.blade.agent.auth.AgentCallAuditEvent;
import com.blade.agent.auth.AgentCallAuditRecorder;
import com.blade.agent.auth.AgentKeyAuthenticationService;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAuthenticationFilterTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void filterAuthenticatesAgentRequestsFromAgentKeyHeader() throws Exception {
        CapturingAuditRecorder auditRecorder = new CapturingAuditRecorder();
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(
                new AgentKeyAuthenticationService(fakeMapper(activeKey()), passwordEncoder),
                auditRecorder);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/agent/analytics/style-trends");
        request.addHeader(AgentAuthenticationFilter.AGENT_KEY_HEADER, "agent_demo.top-secret");
        request.addHeader("User-Agent", "Hermes-Agent/1.0");
        request.setRemoteAddr("10.0.0.8");

        AtomicReference<String> authenticatedName = new AtomicReference<>();
        AtomicLong tenantInsideChain = new AtomicLong();
        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            authenticatedName.set(SecurityContextHolder.getContext().getAuthentication().getName());
            tenantInsideChain.set(TenantContext.getTenantId());
        });

        assertEquals("agent_demo", authenticatedName.get());
        assertEquals(7L, tenantInsideChain.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(TenantContext.getTenantId());
        assertEquals(15L, auditRecorder.principal.getKeyId());
        assertEquals("GET", auditRecorder.event.getMethod());
        assertEquals("/api/agent/analytics/style-trends", auditRecorder.event.getPath());
        assertEquals(200, auditRecorder.event.getStatus());
        assertEquals("10.0.0.8", auditRecorder.event.getIp());
        assertEquals("Hermes-Agent/1.0", auditRecorder.event.getUserAgent());
        assertNull(auditRecorder.event.getRawKey());
    }

    @Test
    void filterReturnsJson401WhenAgentKeyIsMissing() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(
                new AgentKeyAuthenticationService(fakeMapper(activeKey()), passwordEncoder),
                new CapturingAuditRecorder());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/agent/catalog/skus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("missing key must not enter the application");
        });

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

    @Test
    void filterReturnsJson401WhenAgentKeyIsInvalid() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(
                new AgentKeyAuthenticationService(fakeMapper(activeKey()), passwordEncoder),
                new CapturingAuditRecorder());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/agent/catalog/skus");
        request.addHeader(AgentAuthenticationFilter.AGENT_KEY_HEADER, "agent_demo.wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("invalid key must not enter the application");
        });

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Agent Key无效"));
    }

    private AgentKey activeKey() {
        AgentKey key = new AgentKey();
        key.setId(15L);
        key.setKeyPrefix("agent_demo");
        key.setKeyHash(passwordEncoder.encode("top-secret"));
        key.setName("趋势分析 Agent");
        key.setTenantId(7L);
        key.setScopes("analytics:read");
        key.setStatus(AgentKey.STATUS_ACTIVE);
        return key;
    }

    @SuppressWarnings("unchecked")
    private AgentKeyMapper fakeMapper(AgentKey key) {
        return (AgentKeyMapper) Proxy.newProxyInstance(
                AgentKeyMapper.class.getClassLoader(),
                new Class<?>[] { AgentKeyMapper.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectActiveByPrefixForAuthentication" -> key;
                    case "toString" -> "FakeAgentKeyMapper";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == void.class) {
            return null;
        }
        return 0;
    }

    private static class CapturingAuditRecorder implements AgentCallAuditRecorder {
        private AgentPrincipal principal;
        private AgentCallAuditEvent event;

        @Override
        public void record(AgentPrincipal principal, AgentCallAuditEvent event) {
            this.principal = principal;
            this.event = event;
        }
    }
}
