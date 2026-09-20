package com.blade.agent;

import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.agent.service.AgentKeyManagementService;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKeyManagementServiceTest {
    private final AgentKeyMapper keyMapper = mock(AgentKeyMapper.class);
    private final AgentKeyOutletMapper outletMapper = mock(AgentKeyOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AgentKeyManagementService service =
            new AgentKeyManagementService(keyMapper, outletMapper, userMapper, passwordEncoder);

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
        assertEquals(AgentKey.OUTLET_SCOPE_NONE, stored.getOutletScopeType());
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
    void createRejectsCostScopesWithoutTheirBaseWriteScope() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "商品成本越权", List.of("products:cost:write"), 90)));
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "草稿成本越权", List.of("orders:cost:write"), 90)));

        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void customerScopesAreAvailableForExplicitOwnerSelection() {
        assertTrue(service.allowedScopes().contains("customers:read"));
        assertTrue(service.allowedScopes().contains("customers:create"));
        assertTrue(service.allowedScopes().contains("products:cost:write"));
        assertTrue(service.allowedScopes().contains("orders:cost:write"));
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
                77L, new AgentKeyManagementDTO.RotateRequest(
                        List.of("products:read", "orders:read", "products:create"), 30));

        assertEquals(77L, replacement.rotatedFromKeyId());
        assertEquals(List.of("products:read", "orders:read", "products:create"), replacement.scopes());
        assertEquals(AgentKey.STATUS_DISABLED, previous.getStatus());
        assertTrue(previous.getDisabledTime() != null);
        verify(keyMapper).updateById(previous);
    }

    @Test
    void rotateRejectsAnExplicitEmptyScopeSelection() {
        AgentKey previous = new AgentKey();
        previous.setId(78L);
        previous.setName("Mac 纸单 Agent");
        previous.setScopes("catalog:read,orders:write");
        previous.setStatus(AgentKey.STATUS_ACTIVE);
        when(keyMapper.selectById(78L)).thenReturn(previous);

        assertThrows(BusinessException.class, () -> service.rotate(
                78L, new AgentKeyManagementDTO.RotateRequest(List.of(), 30)));

        assertEquals(AgentKey.STATUS_ACTIVE, previous.getStatus());
        verify(keyMapper, never()).updateById(previous);
    }

    @Test
    void rotateCopiesOutletScopeTypeAndActiveBindingsWithDefaultFlag() {
        AgentKey previous = new AgentKey();
        previous.setId(77L);
        previous.setName("Mac 纸单 Agent");
        previous.setScopes("catalog:read,orders:write");
        previous.setOutletScopeType(AgentKey.OUTLET_SCOPE_ASSIGNED);
        previous.setStatus(AgentKey.STATUS_ACTIVE);
        when(keyMapper.selectById(77L)).thenReturn(previous);

        AgentKeyOutlet bindingA = new AgentKeyOutlet();
        bindingA.setOutletId(12L);
        bindingA.setIsDefault(1);
        AgentKeyOutlet bindingB = new AgentKeyOutlet();
        bindingB.setOutletId(15L);
        bindingB.setIsDefault(0);
        when(outletMapper.selectList(any())).thenReturn(List.of(bindingA, bindingB));

        AgentKeyManagementDTO.Credential replacement = service.rotate(
                77L, new AgentKeyManagementDTO.RotateRequest(
                        List.of("products:read", "orders:read", "products:create"), 30));

        // 新 Key 复制旧 Key 的档口范围类型
        ArgumentCaptor<AgentKey> keyCaptor = ArgumentCaptor.forClass(AgentKey.class);
        verify(keyMapper).insert(keyCaptor.capture());
        assertEquals(AgentKey.OUTLET_SCOPE_ASSIGNED, keyCaptor.getValue().getOutletScopeType());

        // 两条有效绑定复制到新 Key，保留租户、档口与默认标记
        ArgumentCaptor<AgentKeyOutlet> outletCaptor = ArgumentCaptor.forClass(AgentKeyOutlet.class);
        verify(outletMapper, times(2)).insert(outletCaptor.capture());
        List<AgentKeyOutlet> copies = outletCaptor.getAllValues();
        assertEquals(replacement.id(), copies.get(0).getAgentKeyId());
        assertEquals(7L, copies.get(0).getTenantId());
        assertEquals(12L, copies.get(0).getOutletId());
        assertEquals(1, copies.get(0).getIsDefault());
        assertEquals(7L, copies.get(1).getTenantId());
        assertEquals(15L, copies.get(1).getOutletId());
        assertEquals(0, copies.get(1).getIsDefault());

        // 旧 Key 仍按原逻辑停用
        assertEquals(AgentKey.STATUS_DISABLED, previous.getStatus());
        verify(keyMapper).updateById(previous);
    }

    @Test
    void rotateCopyFailureDoesNotDisablePreviousKey() {
        AgentKey previous = new AgentKey();
        previous.setId(79L);
        previous.setName("Mac 纸单 Agent");
        previous.setScopes("catalog:read");
        previous.setOutletScopeType(AgentKey.OUTLET_SCOPE_ASSIGNED);
        previous.setStatus(AgentKey.STATUS_ACTIVE);
        when(keyMapper.selectById(79L)).thenReturn(previous);
        when(outletMapper.selectList(any())).thenReturn(List.of(new AgentKeyOutlet()));
        doThrow(new RuntimeException("复制档口绑定失败")).when(outletMapper).insert(any(AgentKeyOutlet.class));

        assertThrows(RuntimeException.class, () -> service.rotate(
                79L, new AgentKeyManagementDTO.RotateRequest(null, 30)));

        // 复制失败时旧 Key 状态与停用时间不得被错误更新
        assertEquals(AgentKey.STATUS_ACTIVE, previous.getStatus());
        assertNull(previous.getDisabledTime());
        verify(keyMapper, never()).updateById(previous);
    }
}
