package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.service.InventoryService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderStateTransitionLog;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.order.service.OrderPlaceholderSplitService;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1 审计整改：{@code shipOrder} 必须在幂等返回/库存出库/状态变更之前先锁单并 requireAccess。
 * 使用真实 {@link OrderAccessPolicy} + {@link OutletAccessPolicy}（mock 数据层），
 * 不使用 allScope 替身，直接验证越权/幂等拒绝与库存/状态无副作用。
 */
class OrderShipScopeGuardTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderFinancialRecordMapper financialRecordMapper = mock(OrderFinancialRecordMapper.class);
    private final OrderStateTransitionLogMapper transitionLogMapper = mock(OrderStateTransitionLogMapper.class);
    private final OrderDeliveryPlanMapper deliveryPlanMapper = mock(OrderDeliveryPlanMapper.class);
    private final OrderAdjustmentLogMapper adjustmentLogMapper = mock(OrderAdjustmentLogMapper.class);
    private final OrderFinanceSnapshotService snapshotService = mock(OrderFinanceSnapshotService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final OrderPlaceholderSplitService placeholderSplitService = mock(OrderPlaceholderSplitService.class);
    private final com.blade.customer.service.CustomerStatsCacheService customerStatsCacheService =
            mock(com.blade.customer.service.CustomerStatsCacheService.class);

    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final AgentKeyOutletMapper agentKeyOutletMapper = mock(AgentKeyOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    private OrderActionService noneScopedService;
    private OutletAccessPolicy realPolicy;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, OrderDeliveryPlan.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        authenticate("btn:order:deliver", "btn:order:view");

        realPolicy = new OutletAccessPolicy(salesOutletMapper, sysUserOutletMapper,
                mock(com.blade.agent.mapper.AgentKeyMapper.class), agentKeyOutletMapper, userMapper);
        when(salesOutletMapper.selectList(any())).thenReturn(List.of());
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());
        noneScopedService = buildService(new OrderAccessPolicy(userMapper, realPolicy));
    }

    private void authenticate(String... authorities) {
        User principal = new User();
        principal.setId(23L);
        principal.setUsername("warehouse");
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, java.util.Arrays.stream(authorities)
                                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                .toList()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private OrderActionService buildService(OrderAccessPolicy accessPolicy) {
        return new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper, snapshotService, new OrderCompatAdapter(),
                inventoryService, placeholderSplitService, customerStatsCacheService, accessPolicy);
    }

    private Order order(String fulfillmentStatus) {
        Order order = new Order();
        order.setId(5L);
        order.setTenantId(1L);
        order.setSourceOutletId(99L); // B 档口
        order.setFulfillmentStatus(fulfillmentStatus);
        order.setFulfillmentMode("STOCK_LINKED");
        order.setCollectionStatus(CollectionStatus.SETTLED.name());
        order.setStatus(3);
        order.setIsDelivered(0);
        return order;
    }

    @Test
    void noneScopeCannotShipOtherOutletOrderAndNothingChanges() {
        Order order = order(FulfillmentStatus.READY_TO_SHIP.name());
        when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);
        when(placeholderSplitService.hasPlaceholderItems(anyLong(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> noneScopedService.shipOrder(5L, "PC"));

        assertEquals(403, ex.getCode());
        assertEquals(FulfillmentStatus.READY_TO_SHIP.name(), order.getFulfillmentStatus());
        verify(inventoryService, never()).outByPlan(anyLong(), anyInt(), anyLong());
        verify(orderMapper, never()).updateById(any(Order.class));
        verify(transitionLogMapper, never()).insert(any(OrderStateTransitionLog.class));
    }

    @Test
    void idempotentAlreadyShippedStillRequiresAccessFirst() {
        Order order = order(FulfillmentStatus.SHIPPED.name());
        when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () -> noneScopedService.shipOrder(5L, "PC"));

        assertEquals(403, ex.getCode());
        verify(inventoryService, never()).outByPlan(anyLong(), anyInt(), anyLong());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void authorizedScopeStillShipsAndCallsInventory() {
        authenticate("btn:order:deliver", "btn:order:view", "data:outlet:all", "data:order:peopleAll");
        com.blade.outlet.entity.SalesOutlet outlet = new com.blade.outlet.entity.SalesOutlet();
        outlet.setId(99L);
        outlet.setTenantId(1L);
        outlet.setStatus(1);
        outlet.setDeleted(0);
        when(salesOutletMapper.selectList(any())).thenReturn(List.of(outlet));

        Order order = order(FulfillmentStatus.READY_TO_SHIP.name());
        when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);
        when(placeholderSplitService.hasPlaceholderItems(anyLong(), anyLong())).thenReturn(false);
        OrderDeliveryPlan plan = new OrderDeliveryPlan();
        plan.setId(77L);
        plan.setOrderId(5L);
        plan.setTenantId(1L);
        plan.setStatus(OrderDeliveryPlan.Status.ALLOCATED);
        plan.setAllocatedQty(2);
        plan.setOutQty(0);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderAccessPolicy allPolicy = new OrderAccessPolicy(userMapper, realPolicy);
        buildService(allPolicy).shipOrder(5L, "PC");

        assertEquals(FulfillmentStatus.SHIPPED.name(), order.getFulfillmentStatus());
        verify(inventoryService).outByPlan(77L, 2, 23L);
        verify(orderMapper).updateById(any(Order.class));
    }
}
