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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("agent_demo", SecurityContextHolder.getContext().getAuthentication().getName());
        assertNull(SecurityContextHolder.getContext().getAuthentication().getCredentials());
        assertEquals(7L, TenantContext.getTenantId());
        assertEquals(15L, auditRecorder.principal.getKeyId());
        assertEquals("GET", auditRecorder.event.getMethod());
        assertEquals("/api/agent/analytics/style-trends", auditRecorder.event.getPath());
        assertEquals(200, auditRecorder.event.getStatus());
        assertEquals("10.0.0.8", auditRecorder.event.getIp());
        assertEquals("Hermes-Agent/1.0", auditRecorder.event.getUserAgent());
        assertNull(auditRecorder.event.getRawKey());
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
                    case "selectOne" -> key;
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
