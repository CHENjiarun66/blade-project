package com.blade.agent;

import com.blade.agent.auth.AgentKeyAuthenticationService;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKeyAuthenticationServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void authenticate_setsTenantAndAuthoritiesForActiveScopedKey() {
        AgentKeyAuthenticationService service = new AgentKeyAuthenticationService(
                fakeMapper(activeKey()), passwordEncoder);

        AgentPrincipal principal = service.authenticate("agent_demo.top-secret");

        assertEquals(7L, TenantContext.getTenantId());
        assertEquals("agent_demo", principal.getName());
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("agent:analytics:read")));
    }

    @Test
    void authenticate_rejectsExpiredKey() {
        AgentKey expiredKey = activeKey();
        expiredKey.setExpiresTime(LocalDateTime.now().minusMinutes(1));
        AgentKeyAuthenticationService service = new AgentKeyAuthenticationService(
                fakeMapper(expiredKey), passwordEncoder);

        assertThrows(BadCredentialsException.class,
                () -> service.authenticate("agent_demo.top-secret"));
    }

    private AgentKey activeKey() {
        AgentKey key = new AgentKey();
        key.setId(15L);
        key.setKeyPrefix("agent_demo");
        key.setKeyHash(passwordEncoder.encode("top-secret"));
        key.setName("趋势分析 Agent");
        key.setTenantId(7L);
        key.setScopes("analytics:read,customers:read");
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
}
