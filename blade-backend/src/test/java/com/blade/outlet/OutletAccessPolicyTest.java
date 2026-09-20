package com.blade.outlet;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutletAccessPolicyTest {

    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final AgentKeyMapper agentKeyMapper = mock(AgentKeyMapper.class);
    private final AgentKeyOutletMapper agentKeyOutletMapper = mock(AgentKeyOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OutletAccessPolicy policy = new OutletAccessPolicy(
            salesOutletMapper, sysUserOutletMapper, agentKeyMapper, agentKeyOutletMapper, userMapper);

    private SalesOutlet outlet(long id, int status) {
        SalesOutlet o = new SalesOutlet();
        o.setId(id);
        o.setOutletCode("O" + id);
        o.setOutletName("档口" + id);
        o.setStatus(status);
        o.setSort(0);
        return o;
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        User user = new User();
        user.setId(23L);
        user.setUsername("u");
        user.setTenantId(7L);
        when(userMapper.selectByUsername("u")).thenReturn(user);
        when(salesOutletMapper.selectList(any())).thenReturn(List.of(outlet(1L, 1), outlet(2L, 1), outlet(3L, 0)));
        when(salesOutletMapper.selectOne(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void userAuth(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private void agentAuth(long keyId) {
        AgentKey key = new AgentKey();
        key.setId(keyId);
        key.setTenantId(7L);
        key.setName("agent");
        key.setScopes("orders:read");
        AgentPrincipal principal = AgentPrincipal.from(key);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
    }

    @Test
    void userWithAllAuthorityGetsAllOutletsAndAllPeopleIndependent() {
        userAuth("data:outlet:all", "data:order:peopleAll");
        OutletAccessScope scope = policy.resolveCurrentScope();

        assertTrue(scope.isAll());
        assertEquals(List.of(1L, 2L, 3L), scope.readableOutletIds());
        assertEquals(List.of(1L, 2L), scope.usableOutletIds());
        assertTrue(scope.isPeopleAll());
        assertEquals("ALL_USERS", scope.peopleScopeType());
    }

    @Test
    void userWithBindingsGetsAssignedAndSelfByDefault() {
        userAuth("menu:outlet");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L, 3L));
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(1L);

        OutletAccessScope scope = policy.resolveCurrentScope();

        assertTrue(scope.isAssigned());
        assertEquals(List.of(1L, 3L), scope.readableOutletIds());
        // 禁用档口 3 可读历史但不可用
        assertTrue(scope.canReadOutlet(3L));
        assertFalse(scope.canUseOutlet(3L));
        assertTrue(scope.canUseOutlet(1L));
        assertFalse(scope.isPeopleAll());
        assertEquals("SELF", scope.peopleScopeType());
        assertEquals(1L, scope.defaultOutletId());
    }

    @Test
    void userWithoutBindingsGetsNoneFailClosed() {
        userAuth("menu:outlet");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());

        OutletAccessScope scope = policy.resolveCurrentScope();

        assertTrue(scope.isNone());
        assertTrue(scope.readableOutletIds().isEmpty());
        assertTrue(scope.usableOutletIds().isEmpty());
        assertNull(scope.defaultOutletId());
        assertFalse(scope.canReadOutlet(1L));
        assertThrows(RuntimeException.class, () -> policy.requireUseOutlet(1L));
    }

    @Test
    void peopleScopeIndependentOfOutletScope() {
        userAuth("menu:outlet", "data:order:peopleAll");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L));

        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.isAssigned());
        assertTrue(scope.isPeopleAll());
    }

    @Test
    void defaultPriorityPersonalThenTenantThenSingle() {
        userAuth("menu:outlet");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L, 2L));
        // personal default 2 wins
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(2L);
        SalesOutlet tenantDefault = outlet(1L, 1);
        tenantDefault.setIsTenantDefault(1);
        when(salesOutletMapper.selectOne(any())).thenReturn(tenantDefault);
        assertEquals(2L, policy.resolveCurrentScope().defaultOutletId());

        // no personal -> tenant default 1
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(null);
        assertEquals(1L, policy.resolveCurrentScope().defaultOutletId());

        // no personal/tenant -> single usable
        when(salesOutletMapper.selectOne(any())).thenReturn(null);
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L));
        assertEquals(1L, policy.resolveCurrentScope().defaultOutletId());
    }

    @Test
    void agentAllScopeIsAllOutletsAllUsers() {
        agentAuth(5L);
        AgentKey key = new AgentKey();
        key.setId(5L);
        key.setTenantId(7L);
        key.setOutletScopeType(OutletAccessScope.ALL);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        when(agentKeyMapper.selectById(5L)).thenReturn(key);

        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.isAll());
        assertEquals(2, scope.usableOutletIds().size());
        assertTrue(scope.isPeopleAll());
    }

    @Test
    void agentAssignedScopeUsesKeyBindings() {
        agentAuth(5L);
        AgentKey key = new AgentKey();
        key.setId(5L);
        key.setTenantId(7L);
        key.setOutletScopeType(OutletAccessScope.ASSIGNED);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        when(agentKeyMapper.selectById(5L)).thenReturn(key);
        when(agentKeyOutletMapper.selectOutletIdsByKeyId(5L)).thenReturn(List.of(1L, 3L));
        when(agentKeyOutletMapper.selectDefaultOutletIdByKeyId(5L)).thenReturn(1L);

        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.isAssigned());
        assertTrue(scope.readableOutletIds().containsAll(List.of(1L, 3L)));
        assertTrue(scope.canUseOutlet(1L));
        assertFalse(scope.canUseOutlet(3L));
        assertEquals(1L, scope.defaultOutletId());
        assertTrue(scope.isPeopleAll());
    }

    @Test
    void agentNoneScopeNeverFallsBackToAll() {
        agentAuth(5L);
        AgentKey key = new AgentKey();
        key.setId(5L);
        key.setTenantId(7L);
        key.setOutletScopeType(OutletAccessScope.NONE);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        when(agentKeyMapper.selectById(5L)).thenReturn(key);

        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.isNone());
        assertTrue(scope.readableOutletIds().isEmpty());
        assertFalse(scope.canReadOutlet(1L));
    }

    @Test
    void optionsContractExposesMetadataAndOnlyUsableItems() {
        userAuth("menu:outlet");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L, 3L));
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(1L);
        when(salesOutletMapper.selectList(any())).thenReturn(List.of(outlet(1L, 1)));

        OutletOptionsVO options = policy.listAvailableOptions();
        assertEquals("ASSIGNED", options.getScopeType());
        assertEquals("SELF", options.getPeopleScope());
        assertEquals(1, options.getItems().size());
        assertEquals(1L, options.getItems().get(0).getId());
    }
}
