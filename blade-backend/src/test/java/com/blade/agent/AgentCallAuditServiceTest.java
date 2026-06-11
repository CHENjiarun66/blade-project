package com.blade.agent;

import com.blade.agent.auth.AgentCallAuditEvent;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentCallLog;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentCallLogMapper;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.agent.service.AgentCallAuditService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentCallAuditServiceTest {

    @Test
    void record_insertsCallLogAndUpdatesKeyLastUsed() {
        CapturingMapperHandler logHandler = new CapturingMapperHandler();
        CapturingMapperHandler keyHandler = new CapturingMapperHandler();
        AgentCallAuditService service = new AgentCallAuditService(
                fakeMapper(AgentCallLogMapper.class, logHandler),
                fakeMapper(AgentKeyMapper.class, keyHandler));

        service.record(principal(), event());

        AgentCallLog log = (AgentCallLog) logHandler.inserted;
        assertEquals(7L, log.getTenantId());
        assertEquals(15L, log.getAgentKeyId());
        assertEquals("agent_demo", log.getKeyPrefix());
        assertEquals("/api/agent/analytics/style-trends", log.getPath());
        assertEquals(200, log.getStatus());
        assertEquals("10.0.0.8", log.getIp());

        AgentKey updatedKey = (AgentKey) keyHandler.updated;
        assertEquals(15L, updatedKey.getId());
        assertEquals("10.0.0.8", updatedKey.getLastUsedIp());
        assertNotNull(updatedKey.getLastUsedTime());
    }

    private AgentPrincipal principal() {
        AgentKey key = new AgentKey();
        key.setId(15L);
        key.setTenantId(7L);
        key.setKeyPrefix("agent_demo");
        key.setName("趋势分析 Agent");
        key.setScopes("analytics:read");
        return AgentPrincipal.from(key);
    }

    private AgentCallAuditEvent event() {
        AgentCallAuditEvent event = new AgentCallAuditEvent();
        event.setMethod("GET");
        event.setPath("/api/agent/analytics/style-trends");
        event.setQueryString("periodType=MONTH");
        event.setStatus(200);
        event.setDurationMs(12L);
        event.setIp("10.0.0.8");
        event.setUserAgent("Hermes-Agent/1.0");
        return event;
    }

    @SuppressWarnings("unchecked")
    private <T> T fakeMapper(Class<T> mapperType, CapturingMapperHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[] { mapperType },
                handler
        );
    }

    private static class CapturingMapperHandler implements java.lang.reflect.InvocationHandler {
        private Object inserted;
        private Object updated;

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> {
                    inserted = args[0];
                    yield 1;
                }
                case "updateById" -> {
                    updated = args[0];
                    yield 1;
                }
                case "toString" -> "CapturingMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
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
}
