package com.blade.outlet;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.entity.Order;
import com.blade.order.service.OrderAccessPolicy;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Series C 范围矩阵：档口维度 × 人员维度 × 待归档，订单与草稿语义一致；
 * 含严格租户隔离与 Agent 校验。使用真实 OutletAccessPolicy（mock 数据层）。
 */
class OutletScopeMatrixTest {

    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final AgentKeyMapper agentKeyMapper = mock(AgentKeyMapper.class);
    private final AgentKeyOutletMapper agentKeyOutletMapper = mock(AgentKeyOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OutletAccessPolicy policy = new OutletAccessPolicy(
            salesOutletMapper, sysUserOutletMapper, agentKeyMapper, agentKeyOutletMapper, userMapper);
    private final OrderAccessPolicy orderPolicy = new OrderAccessPolicy(userMapper, policy);

    private SalesOutlet outlet(long id, int status) {
        SalesOutlet o = new SalesOutlet();
        o.setId(id);
        o.setTenantId(7L);
        o.setOutletCode("O" + id);
        o.setOutletName("档口" + id);
        o.setStatus(status);
        o.setSort(0);
        o.setDeleted(0);
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
        when(agentKeyOutletMapper.selectDefaultOutletIdByKeyId(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void userAuth(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "n/a", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private void agentAuth(long keyId) {
        AgentKey principalKey = new AgentKey();
        principalKey.setId(keyId);
        principalKey.setTenantId(7L);
        principalKey.setName("agent");
        principalKey.setScopes("orders:read");
        AgentPrincipal principal = AgentPrincipal.from(principalKey);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
    }

    private AgentKey activeKey(long id, String outletScope) {
        AgentKey key = new AgentKey();
        key.setId(id);
        key.setTenantId(7L);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setOutletScopeType(outletScope);
        key.setExpiresTime(LocalDateTime.now().plusDays(1));
        return key;
    }

    // ---------- 1) filter × 6 ----------

    @Test
    void outletFilterCoversAllSixCombinations() {
        assertEquals(OutletAccessScope.OutletFilter.NO_FILTER, scope("ALL", true, List.of(1L)).outletFilter());
        assertEquals(OutletAccessScope.OutletFilter.NOT_NULL, scope("ALL", false, List.of(1L)).outletFilter());
        assertEquals(OutletAccessScope.OutletFilter.IN_OR_NULL, scope("ASSIGNED", true, List.of(1L)).outletFilter());
        assertEquals(OutletAccessScope.OutletFilter.IN, scope("ASSIGNED", false, List.of(1L)).outletFilter());
        assertEquals(OutletAccessScope.OutletFilter.ONLY_NULL, scope("NONE", true, List.of()).outletFilter());
        assertEquals(OutletAccessScope.OutletFilter.DENY, scope("NONE", false, List.of()).outletFilter());
    }

    // ---------- 2) 订单/草稿列表与详情一致 ----------

    @Test
    void orderAndDraftDetailsMatchFilterSemanticsForAssignedUnassigned() {
        userAuth("menu:order");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L));
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(null);
        // ASSIGNED + unassigned=true (由 data:outlet:unassigned 授权)
        userAuth("menu:order", "data:outlet:unassigned");

        OutletAccessScope scope = policy.resolveCurrentScope();
        assertEquals(OutletAccessScope.OutletFilter.IN_OR_NULL, scope.outletFilter());
        // 详情：绑定档口可读
        assertTrue(scope.canReadOutlet(1L));
        // 详情：未归档可读
        assertTrue(scope.canReadOutlet(null));
        // 详情：未绑定档口不可读
        assertFalse(scope.canReadOutlet(2L));
    }

    @Test
    void noneUnassignedShowsOnlyNullRowsAndDetailAllowsNull() {
        userAuth("menu:order", "data:outlet:unassigned");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());
        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.isNone());
        assertEquals(OutletAccessScope.OutletFilter.ONLY_NULL, scope.outletFilter());
        assertTrue(scope.canReadOutlet(null));
        assertFalse(scope.canReadOutlet(1L));
    }

    @Test
    void noneWithoutUnassignedDeniesAll() {
        userAuth("menu:order");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());
        OutletAccessScope scope = policy.resolveCurrentScope();
        assertEquals(OutletAccessScope.OutletFilter.DENY, scope.outletFilter());
        assertFalse(scope.canReadOutlet(null));
        assertFalse(scope.canReadOutlet(1L));
    }

    // ---------- 3) people × outlet 正交；viewAll 不绕过 ----------

    @Test
    void peopleScopeIndependentAndViewAllCompatDoesNotBypass() {
        userAuth("menu:order", "btn:order:viewAll", "data:order:peopleAll");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L));
        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.isAssigned());
        assertTrue(scope.isPeopleAll());
        // viewAll 兼容权限单独存在不改变档口维度
        userAuth("menu:order", "btn:order:viewAll");
        assertEquals(OutletAccessScope.OutletFilter.IN, policy.resolveCurrentScope().outletFilter());
    }

    // ---------- 4) 严格租户隔离 ----------

    @Test
    void orderRequireAccessFailsClosedOnTenantMismatchOrZeroScope() {
        userAuth("menu:order", "data:outlet:all");
        when(salesOutletMapper.selectList(any())).thenReturn(List.of(outlet(1L, 1)));
        Order otherTenant = new Order();
        otherTenant.setTenantId(9L);
        otherTenant.setSourceOutletId(1L);
        assertThrows(BusinessException.class, () -> orderPolicy.requireAccess(otherTenant));

        Order nullTenant = new Order();
        nullTenant.setTenantId(null);
        nullTenant.setSourceOutletId(1L);
        assertThrows(BusinessException.class, () -> orderPolicy.requireAccess(nullTenant));
    }

    @Test
    void draftRequireAccessFailsClosedOnTenantMismatch() {
        userAuth("menu:order", "data:outlet:all");
        OrderDraft draft = new OrderDraft();
        draft.setTenantId(9L);
        draft.setSourceOutletId(1L);
        assertThrows(BusinessException.class, () -> policy.requireDraftAccess(draft));
    }

    // ---------- 5) Agent 校验 ----------

    @Test
    void agentScopeFailsClosedWhenKeyMissing() {
        agentAuth(5L);
        when(agentKeyMapper.selectById(5L)).thenReturn(null);
        assertTrue(policy.resolveCurrentScope().isNone());
    }

    @Test
    void agentScopeFailsClosedWhenKeyDisabledOrExpiredOrTenantMismatch() {
        agentAuth(5L);
        AgentKey disabled = activeKey(5L, "ALL");
        disabled.setStatus(AgentKey.STATUS_DISABLED);
        when(agentKeyMapper.selectById(5L)).thenReturn(disabled);
        assertTrue(policy.resolveCurrentScope().isNone());

        AgentKey expired = activeKey(5L, "ALL");
        expired.setExpiresTime(LocalDateTime.now().minusDays(1));
        when(agentKeyMapper.selectById(5L)).thenReturn(expired);
        assertTrue(policy.resolveCurrentScope().isNone());

        AgentKey tenantMismatch = activeKey(5L, "ALL");
        tenantMismatch.setTenantId(99L);
        when(agentKeyMapper.selectById(5L)).thenReturn(tenantMismatch);
        assertTrue(policy.resolveCurrentScope().isNone());
    }

    @Test
    void agentAllUsesKeyDefaultAndAssignedIntersectsTenantOutlets() {
        agentAuth(5L);
        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "ALL"));
        when(agentKeyOutletMapper.selectDefaultOutletIdByKeyId(5L)).thenReturn(2L);
        OutletAccessScope all = policy.resolveCurrentScope();
        assertTrue(all.isAll());
        assertEquals(2L, all.defaultOutletId());
        assertTrue(all.canReadOutlet(3L)); // 禁用但本租户可读历史
        assertFalse(all.canUseOutlet(3L));
        assertFalse(all.canReadOutlet(999L)); // 伪造影口不可读

        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "ASSIGNED"));
        when(agentKeyOutletMapper.selectOutletIdsByKeyId(5L)).thenReturn(List.of(1L, 999L, 3L));
        OutletAccessScope assigned = policy.resolveCurrentScope();
        assertEquals(List.of(1L, 3L), assigned.readableOutletIds()); // 999 跨租户/伪造被剔除
        assertEquals(List.of(1L), assigned.usableOutletIds());
    }

    @Test
    void agentNeverGetsUnassignedNullAccess() {
        agentAuth(5L);

        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "ALL"));
        OutletAccessScope all = policy.resolveCurrentScope();
        assertFalse(all.unassignedAllowed());
        assertEquals(OutletAccessScope.OutletFilter.NOT_NULL, all.outletFilter());

        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "ASSIGNED"));
        when(agentKeyOutletMapper.selectOutletIdsByKeyId(5L)).thenReturn(List.of(1L));
        OutletAccessScope assigned = policy.resolveCurrentScope();
        assertFalse(assigned.unassignedAllowed());
        assertEquals(OutletAccessScope.OutletFilter.IN, assigned.outletFilter());

        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "NONE"));
        OutletAccessScope none = policy.resolveCurrentScope();
        assertTrue(none.isNone());
        assertFalse(none.unassignedAllowed());
        assertEquals(OutletAccessScope.OutletFilter.DENY, none.outletFilter());
    }

    // ---------- 6) 默认优先级 ----------

    @Test
    void userAllUsesPersonalDefault() {
        userAuth("menu:order", "data:outlet:all");
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(2L);
        assertEquals(2L, policy.resolveCurrentScope().defaultOutletId());
    }

    // ---------- 7) 禁用档口历史可读但不可用 ----------

    @Test
    void disabledOutletReadableButNotUsable() {
        userAuth("menu:order");
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L, 3L));
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(23L)).thenReturn(null);
        OutletAccessScope scope = policy.resolveCurrentScope();
        assertTrue(scope.canReadOutlet(3L));
        assertFalse(scope.canUseOutlet(3L));
        // 唯一可用档口成为默认；禁用档口不参与默认
        assertEquals(1L, scope.defaultOutletId());
    }

    private OutletAccessScope scope(String type, boolean unassigned, List<Long> readable) {
        return new OutletAccessScope(7L, OutletAccessScope.ActorType.USER, 23L, type, false, unassigned,
                readable, readable, null);
    }
}
