package com.blade.agent;

import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.agent.service.AgentKeyManagementService;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKeyManagementServiceTest {
    private final AgentKeyMapper keyMapper = mock(AgentKeyMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AgentKeyManagementService service =
            new AgentKeyManagementService(keyMapper, userMapper, passwordEncoder);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", "n/a"));
        User user = new User();
        user.setId(23L);
        user.setUsername("owner");
        user.setTenantId(7L);
        when(userMapper.selectByUsername("owner")).thenReturn(user);
        when(keyMapper.insert(any(AgentKey.class))).thenAnswer(invocation -> {
            AgentKey key = invocation.getArgument(0);
            key.setId(101L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void createReturnsSecretOnceAndPersistsOnlyHashForCurrentTenant() {
        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "Mac 纸单 Agent", List.of("catalog:read", "orders:write"), 90));

        ArgumentCaptor<AgentKey> captor = ArgumentCaptor.forClass(AgentKey.class);
        verify(keyMapper).insert(captor.capture());
        AgentKey stored = captor.getValue();
        String secret = credential.agentKey().substring(credential.agentKey().indexOf('.') + 1);

        assertEquals(7L, stored.getTenantId());
        assertEquals(23L, stored.getCreatedByUserId());
        assertEquals("catalog:read,orders:write", stored.getScopes());
        assertEquals(AgentKey.STATUS_ACTIVE, stored.getStatus());
        assertNotEquals(secret, stored.getKeyHash());
        assertTrue(passwordEncoder.matches(secret, stored.getKeyHash()));
        assertFalse(stored.getKeyHash().contains(secret));
    }

    @Test
    void createRejectsUnknownScopeBeforeWriting() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "越权 Agent", List.of("orders:confirm"), 90)));

        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void rotateIssuesReplacementAndDisablesPreviousKey() {
        AgentKey previous = new AgentKey();
        previous.setId(77L);
        previous.setName("Mac 纸单 Agent");
        previous.setScopes("catalog:read,orders:write");
        previous.setStatus(AgentKey.STATUS_ACTIVE);
        when(keyMapper.selectById(77L)).thenReturn(previous);

        AgentKeyManagementDTO.Credential replacement = service.rotate(
                77L, new AgentKeyManagementDTO.RotateRequest(30));

        assertEquals(77L, replacement.rotatedFromKeyId());
        assertEquals(AgentKey.STATUS_DISABLED, previous.getStatus());
        assertTrue(previous.getDisabledTime() != null);
        verify(keyMapper).updateById(previous);
    }
}
