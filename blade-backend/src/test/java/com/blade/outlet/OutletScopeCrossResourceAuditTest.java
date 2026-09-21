package com.blade.outlet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.entity.Order;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderReadScope;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TEST-OUTLET-004（本地矩阵审计）：档口 × 人员 × user/agent × 订单/草稿/统计读范围。
 *
 * <p>使用真实策略 + mock 数据层，覆盖 single/multi/all/none/unassigned 与 Agent
 * ASSIGNED/ALL/NONE；订单谓词、草稿谓词、统计读范围、可用档口选项四类资源一致。
 * 生产规模性能（EXPLAIN 计划）保持 pending，见 scripts/outlet-scope-explain.sql。</p>
 */
class OutletScopeCrossResourceAuditTest {

    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final AgentKeyMapper agentKeyMapper = mock(AgentKeyMapper.class);
    private final AgentKeyOutletMapper agentKeyOutletMapper = mock(AgentKeyOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OutletAccessPolicy policy = new OutletAccessPolicy(
            salesOutletMapper, sysUserOutletMapper, agentKeyMapper, agentKeyOutletMapper, userMapper);
    private final OrderAccessPolicy orderPolicy = new OrderAccessPolicy(userMapper, policy);

    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration config =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, SalesOutlet.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Order.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, OrderDraft.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        // loadTenantOutlets 无 status 条件（含禁用历史）；listAvailableOptions 有 status=1（仅启用）。
        when(salesOutletMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<?> wrapper = invocation.getArgument(0);
            return wrapper.getSqlSegment().contains("status")
                    ? List.of(outlet(1L, 1))
                    : List.of(outlet(1L, 1), outlet(3L, 0));
        });
        when(agentKeyOutletMapper.selectDefaultOutletIdByKeyId(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void userActorsProduceConsistentOrderDraftAnalyticsAndOptionsScope() {
        // single：绑定 [1]（启用），人员 SELF
        userAuth(23L);
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L));
        assertUserScope(OutletAccessScope.ASSIGNED, List.of(1L), "IN", 1);

        // multi：绑定 [1,3]，3 禁用 => usable 仅 [1]
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L, 3L));
        assertUserScope(OutletAccessScope.ASSIGNED, List.of(1L, 3L), "IN", 1);

        // all：data:outlet:all
        userAuth(23L, "data:outlet:all");
        assertUserScope(OutletAccessScope.ALL, List.of(1L, 3L), "IS NOT NULL", 1);

        // none：无绑定，无 all
        userAuth(23L);
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());
        assertUserScope(OutletAccessScope.NONE, List.of(), "1 = 0", 0);

        // unassigned：仅有 data:outlet:unassigned
        userAuth(23L, "data:outlet:unassigned");
        OutletAccessScope unassigned = policy.resolveCurrentScope();
        assertTrue(unassigned.unassignedAllowed());
        assertEquals(OutletAccessScope.OutletFilter.ONLY_NULL, unassigned.outletFilter());
        assertEquals(0, orderPolicy.resolveReadScope(null, null).readableOutletIds().size());
        assertTrue(draftSql().contains("IS NULL"));
    }

    @Test
    void agentActorsProduceConsistentOrderDraftAnalyticsAndOptionsScope() {
        // Agent ASSIGNED [1]（3 禁用，不可用）
        agentAuth(5L);
        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "ASSIGNED"));
        when(agentKeyOutletMapper.selectOutletIdsByKeyId(5L)).thenReturn(List.of(1L, 3L));
        OrderReadScope assigned = orderPolicy.resolveReadScope(null, null);
        assertEquals(OutletAccessScope.ASSIGNED, assigned.outletScopeType());
        assertEquals(List.of(1L, 3L), assigned.readableOutletIds());
        assertTrue(orderSql().contains("IN"));
        assertTrue(draftSql().contains("IN"));
        assertEquals(1, policy.listAvailableOptions().getItems().size());

        // Agent ALL
        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "ALL"));
        OrderReadScope all = orderPolicy.resolveReadScope(null, null);
        assertEquals(OutletAccessScope.ALL, all.outletScopeType());
        assertTrue(orderSql().contains("IS NOT NULL"));

        // Agent NONE
        when(agentKeyMapper.selectById(5L)).thenReturn(activeKey(5L, "NONE"));
        OrderReadScope none = orderPolicy.resolveReadScope(null, null);
        assertTrue(none.isNone());
        assertFalse(none.unassignedAllowed());
        assertTrue(orderSql().contains("1 = 0"));
        assertEquals(0, policy.listAvailableOptions().getItems().size());
    }

    private void assertUserScope(String expectedType, List<Long> expectedReadable, String orderMarker, int usableCount) {
        OutletAccessScope scope = policy.resolveCurrentScope();
        assertEquals(expectedType, scope.outletScopeType());
        assertEquals(expectedReadable, scope.readableOutletIds());
        OrderReadScope readScope = orderPolicy.resolveReadScope(null, null);
        assertEquals(expectedReadable, readScope.readableOutletIds());
        assertTrue(orderSql().contains(orderMarker), "order sql=" + orderSql());
        OutletOptionsVO options = policy.listAvailableOptions();
        assertEquals(usableCount, options.getItems().size());
    }

    private String orderSql() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        orderPolicy.applyReadPredicate(wrapper);
        return wrapper.getSqlSegment();
    }

    private String draftSql() {
        LambdaQueryWrapper<OrderDraft> wrapper = new LambdaQueryWrapper<>();
        policy.applyDraftReadScope(wrapper);
        return wrapper.getSqlSegment();
    }

    private void userAuth(long userId, String... authorities) {
        User user = new User();
        user.setId(userId);
        user.setUsername("u" + userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private void agentAuth(long keyId) {
        AgentKey key = new AgentKey();
        key.setId(keyId);
        key.setTenantId(7L);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setScopes("orders:read");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AgentPrincipal.from(key), "n/a", AgentPrincipal.from(key).getAuthorities()));
    }

    private AgentKey activeKey(long id, String scopeType) {
        AgentKey key = new AgentKey();
        key.setId(id);
        key.setTenantId(7L);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setOutletScopeType(scopeType);
        return key;
    }

    private SalesOutlet outlet(long id, int status) {
        SalesOutlet outlet = new SalesOutlet();
        outlet.setId(id);
        outlet.setTenantId(7L);
        outlet.setOutletCode("O" + id);
        outlet.setOutletName("档口" + id);
        outlet.setStatus(status);
        outlet.setSort((int) id);
        outlet.setDeleted(0);
        return outlet;
    }
}
