package com.blade.agent;

import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.agent.service.AgentKeyManagementService;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
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
    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AgentKeyManagementService service =
            new AgentKeyManagementService(keyMapper, outletMapper, salesOutletMapper, userMapper, passwordEncoder);

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

    // ==================== create：基础 ====================

    @Test
    void createReturnsSecretOnceAndPersistsOnlyHashForCurrentTenant() {
        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "Mac 纸单 Agent", List.of("catalog:read", "orders:write"), 90, null, null, null));

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
        assertEquals(AgentKey.OUTLET_SCOPE_NONE, credential.outletScopeType());
        assertNull(credential.defaultOutletId());
        assertTrue(credential.outlets().isEmpty());
        verify(outletMapper, never()).insert(any(AgentKeyOutlet.class));
    }

    @Test
    void createRejectsUnknownScopeBeforeWriting() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "越权 Agent", List.of("orders:confirm"), 90, null, null, null)));

        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void createRejectsCostScopesWithoutTheirBaseWriteScope() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "商品成本越权", List.of("products:cost:write"), 90, null, null, null)));
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "草稿成本越权", List.of("orders:cost:write"), 90, null, null, null)));

        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void outletsReadScopeIsAvailableForSelection() {
        assertTrue(service.allowedScopes().contains("outlets:read"));
        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "档口 Agent", List.of("orders:read", "outlets:read"), 90, null, null, null));
        assertEquals(List.of("orders:read", "outlets:read"), credential.scopes());
    }

    // ==================== create：档口范围 ====================

    @Test
    void createAllScopeDoesNotPersistRedundantBindings() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, true), outlet(15L, "B", "档口B", 1, false));

        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "全部档口", List.of("orders:read"), 90, "ALL", null, null));

        ArgumentCaptor<AgentKey> keyCaptor = ArgumentCaptor.forClass(AgentKey.class);
        verify(keyMapper).insert(keyCaptor.capture());
        assertEquals(AgentKey.OUTLET_SCOPE_ALL, keyCaptor.getValue().getOutletScopeType());
        assertEquals(AgentKey.OUTLET_SCOPE_ALL, credential.outletScopeType());
        assertNull(credential.defaultOutletId());
        verify(outletMapper, never()).insert(any(AgentKeyOutlet.class));
    }

    @Test
    void createAllScopeStoresOnlyExplicitDefaultMarker() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false), outlet(15L, "B", "档口B", 1, false));

        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "全部档口", List.of("orders:read"), 90, "ALL", null, 15L));

        assertEquals(AgentKey.OUTLET_SCOPE_ALL, credential.outletScopeType());
        assertEquals(15L, credential.defaultOutletId());
        assertEquals(List.of("B"), credential.outlets().stream()
                .map(AgentKeyManagementDTO.OutletSummary::outletCode).toList());
        ArgumentCaptor<AgentKeyOutlet> captor = ArgumentCaptor.forClass(AgentKeyOutlet.class);
        verify(outletMapper).insert(captor.capture());
        assertEquals(15L, captor.getValue().getOutletId());
        assertEquals(1, captor.getValue().getIsDefault());
    }

    @Test
    void createAllScopeRejectsRedundantOutletIds() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "全部档口", List.of("orders:read"), 90, "ALL", List.of(12L), null)));
        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void createAssignedWritesSortedDedupedBindings() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false), outlet(15L, "B", "档口B", 1, false));

        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "指定档口", List.of("orders:read"), 90, "ASSIGNED", List.of(15L, 12L, 15L), null));

        assertEquals(AgentKey.OUTLET_SCOPE_ASSIGNED, credential.outletScopeType());
        assertNull(credential.defaultOutletId());
        ArgumentCaptor<AgentKeyOutlet> captor = ArgumentCaptor.forClass(AgentKeyOutlet.class);
        verify(outletMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(12L, 15L), captor.getAllValues().stream().map(AgentKeyOutlet::getOutletId).toList());
        assertTrue(captor.getAllValues().stream().allMatch(binding -> binding.getIsDefault() == 0));
    }

    @Test
    void createAssignedSingleEnabledOutletAutoDefaults() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false));

        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "单档口", List.of("orders:read"), 90, "ASSIGNED", List.of(12L), null));

        assertEquals(12L, credential.defaultOutletId());
        ArgumentCaptor<AgentKeyOutlet> captor = ArgumentCaptor.forClass(AgentKeyOutlet.class);
        verify(outletMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getIsDefault());
    }

    @Test
    void createAssignedDefaultMustBeInsideSet() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false), outlet(15L, "B", "档口B", 1, false));

        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "指定档口", List.of("orders:read"), 90, "ASSIGNED", List.of(12L), 15L)));
        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void createAssignedDefaultMustBeEnabled() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 0, false));

        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "指定档口", List.of("orders:read"), 90, "ASSIGNED", List.of(12L), 12L)));
        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void createAssignedAllowsDisabledHistoricalBindingButNoAutoDefault() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 0, false), outlet(15L, "B", "档口B", 1, false));

        AgentKeyManagementDTO.Credential credential = service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "指定档口", List.of("orders:read"), 90, "ASSIGNED", List.of(12L, 15L), null));

        assertNull(credential.defaultOutletId());
        verify(outletMapper, times(2)).insert(any(AgentKeyOutlet.class));
    }

    @Test
    void createRejectsCrossTenantOrDeletedOutlet() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false));

        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "越权档口", List.of("orders:read"), 90, "ASSIGNED", List.of(99L), null)));
        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void createNoneRejectsOutletIdsAndDefault() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "不开放", List.of("orders:read"), 90, "NONE", List.of(12L), null)));
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "不开放", List.of("orders:read"), 90, "NONE", null, 12L)));
        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void createRejectsUnknownOutletScopeType() {
        assertThrows(BusinessException.class, () -> service.create(
                new AgentKeyManagementDTO.CreateRequest(
                        "非法范围", List.of("orders:read"), 90, "EVERYTHING", null, null)));
        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void listConfigurableOutletsReturnsAllTenantOutletsIncludingDisabled() {
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, true), outlet(15L, "B", "档口B", 0, false));

        List<AgentKeyManagementDTO.AdminOutletOption> options = service.listConfigurableOutlets();

        assertEquals(List.of("A", "B"), options.stream()
                .map(AgentKeyManagementDTO.AdminOutletOption::outletCode).toList());
        assertEquals(List.of(1, 0), options.stream()
                .map(AgentKeyManagementDTO.AdminOutletOption::status).toList());
        assertTrue(options.get(0).tenantDefault());
    }

    // ==================== rotate ====================

    @Test
    void rotateIssuesReplacementAndDisablesPreviousKey() {
        AgentKey previous = activeKey(77L, AgentKey.OUTLET_SCOPE_NONE);
        when(keyMapper.selectById(77L)).thenReturn(previous);

        AgentKeyManagementDTO.Credential replacement = service.rotate(
                77L, new AgentKeyManagementDTO.RotateRequest(
                        List.of("products:read", "orders:read", "products:create"), 30, null, null, null));

        assertEquals(77L, replacement.rotatedFromKeyId());
        assertEquals(List.of("products:read", "orders:read", "products:create"), replacement.scopes());
        assertEquals(AgentKey.STATUS_DISABLED, previous.getStatus());
        assertTrue(previous.getDisabledTime() != null);
        verify(keyMapper).updateById(previous);
    }

    @Test
    void rotateRejectsAnExplicitEmptyScopeSelection() {
        AgentKey previous = activeKey(78L, AgentKey.OUTLET_SCOPE_NONE);
        when(keyMapper.selectById(78L)).thenReturn(previous);

        assertThrows(BusinessException.class, () -> service.rotate(
                78L, new AgentKeyManagementDTO.RotateRequest(List.of(), 30, null, null, null)));

        assertEquals(AgentKey.STATUS_ACTIVE, previous.getStatus());
        verify(keyMapper, never()).updateById(previous);
    }

    @Test
    void rotateInheritsScopesAndOutletConfigWhenFieldsNull() {
        AgentKey previous = activeKey(80L, AgentKey.OUTLET_SCOPE_ASSIGNED);
        when(keyMapper.selectById(80L)).thenReturn(previous);
        AgentKeyOutlet bindingA = binding(12L, 1);
        AgentKeyOutlet bindingB = binding(15L, 0);
        when(outletMapper.selectList(any())).thenReturn(List.of(bindingA, bindingB));
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false), outlet(15L, "B", "档口B", 1, false));

        AgentKeyManagementDTO.Credential replacement = service.rotate(
                80L, new AgentKeyManagementDTO.RotateRequest(null, 30, null, null, null));

        ArgumentCaptor<AgentKey> keyCaptor = ArgumentCaptor.forClass(AgentKey.class);
        verify(keyMapper).insert(keyCaptor.capture());
        assertEquals(AgentKey.OUTLET_SCOPE_ASSIGNED, keyCaptor.getValue().getOutletScopeType());
        assertEquals(12L, replacement.defaultOutletId());
        ArgumentCaptor<AgentKeyOutlet> captor = ArgumentCaptor.forClass(AgentKeyOutlet.class);
        verify(outletMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(12L, 15L), captor.getAllValues().stream().map(AgentKeyOutlet::getOutletId).toList());
        assertEquals(1, captor.getAllValues().get(0).getIsDefault());
    }

    @Test
    void rotateExplicitConfigReplacesBindingsWithoutCopyingOld() {
        AgentKey previous = activeKey(81L, AgentKey.OUTLET_SCOPE_ASSIGNED);
        previous.setScopes("catalog:read");
        when(keyMapper.selectById(81L)).thenReturn(previous);
        when(outletMapper.selectList(any())).thenReturn(List.of(binding(12L, 1), binding(15L, 0)));
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false), outlet(15L, "B", "档口B", 1, false),
                outlet(20L, "C", "档口C", 1, false));

        AgentKeyManagementDTO.Credential replacement = service.rotate(
                81L, new AgentKeyManagementDTO.RotateRequest(
                        List.of("catalog:read"), 30, "ASSIGNED", List.of(20L), null));

        assertEquals(20L, replacement.defaultOutletId());
        ArgumentCaptor<AgentKeyOutlet> captor = ArgumentCaptor.forClass(AgentKeyOutlet.class);
        verify(outletMapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().getOutletId());
        assertEquals(1, captor.getValue().getIsDefault());
    }

    @Test
    void rotateChangesScopeTypeWithoutInheritingStaleBindings() {
        AgentKey previous = activeKey(82L, AgentKey.OUTLET_SCOPE_ASSIGNED);
        previous.setScopes("catalog:read");
        when(keyMapper.selectById(82L)).thenReturn(previous);
        when(outletMapper.selectList(any())).thenReturn(List.of(binding(12L, 1), binding(15L, 0)));
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false), outlet(15L, "B", "档口B", 1, false));

        AgentKeyManagementDTO.Credential replacement = service.rotate(
                82L, new AgentKeyManagementDTO.RotateRequest(
                        List.of("catalog:read"), 30, "ALL", null, null));

        assertEquals(AgentKey.OUTLET_SCOPE_ALL, replacement.outletScopeType());
        assertNull(replacement.defaultOutletId());
        verify(outletMapper, never()).insert(any(AgentKeyOutlet.class));
    }

    @Test
    void rotateRejectsDisabledPreviousKey() {
        AgentKey previous = new AgentKey();
        previous.setId(83L);
        previous.setStatus(AgentKey.STATUS_DISABLED);
        when(keyMapper.selectById(83L)).thenReturn(previous);

        assertThrows(BusinessException.class, () -> service.rotate(
                83L, new AgentKeyManagementDTO.RotateRequest(null, 30, null, null, null)));

        verify(keyMapper, never()).insert(any(AgentKey.class));
    }

    @Test
    void rotateFailureKeepsPreviousKeyActiveAndDoesNotDisable() {
        AgentKey previous = activeKey(79L, AgentKey.OUTLET_SCOPE_ASSIGNED);
        when(keyMapper.selectById(79L)).thenReturn(previous);
        when(outletMapper.selectList(any())).thenReturn(List.of(binding(12L, 0)));
        stubTenantOutlets(outlet(12L, "A", "档口A", 1, false));
        doThrow(new RuntimeException("写入绑定失败")).when(outletMapper).insert(any(AgentKeyOutlet.class));

        assertThrows(RuntimeException.class, () -> service.rotate(
                79L, new AgentKeyManagementDTO.RotateRequest(null, 30, null, null, null)));

        assertEquals(AgentKey.STATUS_ACTIVE, previous.getStatus());
        assertNull(previous.getDisabledTime());
        verify(keyMapper, never()).updateById(previous);
    }

    // ==================== helpers ====================

    private void stubTenantOutlets(SalesOutlet... outlets) {
        when(salesOutletMapper.selectList(any())).thenReturn(List.of(outlets));
    }

    private AgentKey activeKey(Long id, String outletScopeType) {
        AgentKey key = new AgentKey();
        key.setId(id);
        key.setName("Mac 纸单 Agent");
        key.setScopes("catalog:read,orders:write");
        key.setOutletScopeType(outletScopeType);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setTenantId(7L);
        return key;
    }

    private AgentKeyOutlet binding(Long outletId, int isDefault) {
        AgentKeyOutlet binding = new AgentKeyOutlet();
        binding.setOutletId(outletId);
        binding.setIsDefault(isDefault);
        binding.setStatus(1);
        return binding;
    }

    private SalesOutlet outlet(Long id, String code, String name, int status, boolean tenantDefault) {
        SalesOutlet outlet = new SalesOutlet();
        outlet.setId(id);
        outlet.setTenantId(7L);
        outlet.setOutletCode(code);
        outlet.setOutletName(name);
        outlet.setStatus(status);
        outlet.setIsTenantDefault(tenantDefault ? 1 : 0);
        outlet.setDeleted(0);
        outlet.setSort(id.intValue());
        return outlet;
    }
}
