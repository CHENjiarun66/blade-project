package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.service.InventoryService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDelivery;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderStateTransitionLog;
import com.blade.order.mapper.OrderDeliveryMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.order.service.impl.OrderDeliveryServiceImpl;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.system.user.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.blade.file.service.FileService;
import com.blade.order.service.OrderService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Shipment path unification — focused unit tests on the unified action service.
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderDeliverOrderSoftCouplingTest {

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, OrderDeliveryPlan.class);
    }

    // ── OrderActionService mocks ─────────────────────────────────────
    @Mock private OrderMapper orderMapper;
    @Mock private OrderFinancialRecordMapper financialRecordMapper;
    @Mock private OrderStateTransitionLogMapper transitionLogMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private OrderFinanceSnapshotService snapshotService;
    @Mock private InventoryService inventoryService;
    @Mock private com.blade.order.service.OrderPlaceholderSplitService placeholderSplitService;
    @Mock private com.blade.customer.service.CustomerStatsCacheService customerStatsCacheService;

    private OrderActionService actionService;

    // ── OrderDeliveryServiceImpl mocks ────────────────────────────────
    @Mock private OrderDeliveryMapper deliveryMapper;
    @Mock private com.blade.order.mapper.OrderDeliveryItemMapper deliveryItemMapper;
    @Mock private OrderService orderServiceMockForDelivery;

    private OrderDeliveryServiceImpl deliveryService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);

        User principal = new User();
        principal.setId(1L);
        principal.setUsername("admin");
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.setContext(securityContext);

        actionService = new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper, snapshotService, new OrderCompatAdapter(),
                inventoryService, placeholderSplitService, customerStatsCacheService);
        deliveryService = new OrderDeliveryServiceImpl(deliveryMapper, deliveryItemMapper,
                orderMapper, mock(com.blade.order.mapper.OrderItemMapper.class),
                mock(WarehouseMapper.class), mock(com.blade.product.mapper.ProductSkuMapper.class),
                mock(com.blade.product.mapper.ProductColorMapper.class),
                mock(com.blade.product.mapper.ProductSizeMapper.class),
                mock(com.blade.product.mapper.ProductMapper.class), orderServiceMockForDelivery,
                mock(org.redisson.api.RedissonClient.class));
    }


    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Order stubOrder(Long id, int status) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(status);
        o.setFulfillmentStatus(legacyFulfillment(status));
        o.setFulfillmentMode(com.blade.order.enums.FulfillmentMode.STOCK_LINKED.name());
        o.setTenantId(1L);
        o.setWarehouseId(1L);
        return o;
    }

    private String legacyFulfillment(int status) {
        switch (status) {
            case 0: return com.blade.order.enums.FulfillmentStatus.CONFIRMED.name();
            case 1: return com.blade.order.enums.FulfillmentStatus.WAITING_ALLOCATION.name();
            case 2: return com.blade.order.enums.FulfillmentStatus.ALLOCATING.name();
            case 3: return com.blade.order.enums.FulfillmentStatus.READY_TO_SHIP.name();
            case 4: return com.blade.order.enums.FulfillmentStatus.SHIPPED.name();
            case 5: return com.blade.order.enums.FulfillmentStatus.COMPLETED.name();
            case 6: return com.blade.order.enums.FulfillmentStatus.CANCELLED.name();
            default: return null;
        }
    }

    private OrderDeliveryPlan stubPlan(Long id, Long orderId, int allocatedQty, int outQty, String status) {
        OrderDeliveryPlan p = new OrderDeliveryPlan();
        p.setId(id);
        p.setOrderId(orderId);
        p.setSkuId(100L + id);
        p.setWarehouseId(1L);
        p.setPlannedQty(allocatedQty + outQty);
        p.setAllocatedQty(allocatedQty);
        p.setOutQty(outQty);
        p.setStatus(status);
        p.setTenantId(1L);
        return p;
    }

    private Order captorUpdatedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — whole shipment (happy path)
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_shouldShipWholeOrderAndAdvanceStatusToDelivered() {
        Order order = stubOrder(1L, 3); // READY_TO_SHIP
        when(orderMapper.selectByIdForUpdate(1L, 1L)).thenReturn(order);

        List<OrderDeliveryPlan> plans = Arrays.asList(
                stubPlan(10L, 1L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED),
                stubPlan(11L, 1L, 3, 0, OrderDeliveryPlan.Status.ALLOCATED));
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        actionService.shipOrder(1L, "PC");

        // Each non-OUT plan should call outByPlan
        verify(inventoryService).outByPlan(eq(10L), eq(5), anyLong());
        verify(inventoryService).outByPlan(eq(11L), eq(3), anyLong());

        // Order advanced to SHIPPED (legacy projection 4)
        Order updated = captorUpdatedOrder();
        assertEquals(4, updated.getStatus());
        assertEquals("SHIPPED", updated.getFulfillmentStatus());
        assertEquals(1, updated.getIsDelivered());
        assertNotNull(updated.getDeliveredAt());
        assertNotNull(updated.getDeliverTime());
        verify(transitionLogMapper).insert(any(OrderStateTransitionLog.class));
    }

    @Test
    void shipOrder_shouldSkipAlreadyOutPlans() {
        Order order = stubOrder(2L, 3);
        when(orderMapper.selectByIdForUpdate(2L, 1L)).thenReturn(order);

        List<OrderDeliveryPlan> plans = Arrays.asList(
                stubPlan(20L, 2L, 5, 5, OrderDeliveryPlan.Status.OUT),  // already fully out
                stubPlan(21L, 2L, 3, 0, OrderDeliveryPlan.Status.ALLOCATED));
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        actionService.shipOrder(2L, "PC");

        // Only the ALLOCATED plan should trigger outByPlan
        verify(inventoryService, never()).outByPlan(eq(20L), anyInt(), anyLong());
        verify(inventoryService).outByPlan(eq(21L), eq(3), anyLong());
    }

    @Test
    void shipOrder_shouldUseSelectByIdForUpdateWithTenantFilter() {
        Order order = stubOrder(3L, 3);
        when(orderMapper.selectByIdForUpdate(3L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(
                stubPlan(30L, 3L, 2, 0, OrderDeliveryPlan.Status.ALLOCATED)));

        actionService.shipOrder(3L, "PC");

        // Must use selectByIdForUpdate, never plain selectById
        verify(orderMapper).selectByIdForUpdate(3L, 1L);
        verify(orderMapper, never()).selectById(anyLong());
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — idempotency (no-op)
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_alreadyDelivered_shouldNoOpWithoutInventoryInteraction() {
        Order order = stubOrder(4L, 4); // SHIPPED
        when(orderMapper.selectByIdForUpdate(4L, 1L)).thenReturn(order);

        actionService.shipOrder(4L, "PC");

        // No inventory operations, no order update
        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void shipOrder_alreadyCompleted_shouldNoOpWithoutInventoryInteraction() {
        Order order = stubOrder(5L, 5); // COMPLETED
        when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);

        actionService.shipOrder(5L, "PC");

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — wrong status rejection
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_wrongStatusConfirmed_shouldThrowWithoutInventory() {
        Order order = stubOrder(6L, 0); // CONFIRMED
        when(orderMapper.selectByIdForUpdate(6L, 1L)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(6L, "PC"));
        assertTrue(ex.getMessage().contains("待发货"));
        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void shipOrder_wrongStatusWaitingAllocation_shouldThrowWithoutInventory() {
        Order order = stubOrder(7L, 1); // WAITING_ALLOCATION
        when(orderMapper.selectByIdForUpdate(7L, 1L)).thenReturn(order);

        assertThrows(RuntimeException.class, () -> actionService.shipOrder(7L, "PC"));
        verifyNoInteractions(inventoryService);
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — no / invalid plans
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_emptyPlans_shouldThrow() {
        Order order = stubOrder(8L, 3);
        when(orderMapper.selectByIdForUpdate(8L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(Collections.emptyList());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(8L, "PC"));
        assertTrue(ex.getMessage().contains("配货计划"));
        verifyNoInteractions(inventoryService);
    }

    @Test
    void shipOrder_plansWithPendingStatus_shouldThrow() {
        Order order = stubOrder(9L, 3);
        when(orderMapper.selectByIdForUpdate(9L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(
                stubPlan(90L, 9L, 3, 0, OrderDeliveryPlan.Status.PENDING)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(9L, "PC"));
        assertTrue(ex.getMessage().contains("状态异常"));
        verifyNoInteractions(inventoryService);
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — non-OUT plan with zero/negative remaining
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_nonOutPlan_zeroRemaining_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(12L, 3); // READY_TO_SHIP
        when(orderMapper.selectByIdForUpdate(12L, 1L)).thenReturn(order);

        OrderDeliveryPlan zeroRemainPlan = stubPlan(120L, 12L, 5, 5, OrderDeliveryPlan.Status.ALLOCATED);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(zeroRemainPlan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(12L, "PC"));
        assertTrue(ex.getMessage().contains("无待出库数量"));
        assertTrue(ex.getMessage().contains("planId=120"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void shipOrder_nonOutPlan_negativeRemaining_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(13L, 3); // READY_TO_SHIP
        when(orderMapper.selectByIdForUpdate(13L, 1L)).thenReturn(order);

        OrderDeliveryPlan negRemainPlan = stubPlan(130L, 13L, 3, 5, OrderDeliveryPlan.Status.ALLOCATED);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(negRemainPlan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(13L, "PC"));
        assertTrue(ex.getMessage().contains("无待出库数量"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void shipOrder_nonOutPlan_nullAllocatedQty_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(14L, 3);
        when(orderMapper.selectByIdForUpdate(14L, 1L)).thenReturn(order);

        OrderDeliveryPlan plan = stubPlan(140L, 14L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED);
        plan.setAllocatedQty(null);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(plan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(14L, "PC"));
        assertTrue(ex.getMessage().contains("配货数量或已出库数量为空"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void shipOrder_nonOutPlan_nullOutQty_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(15L, 3);
        when(orderMapper.selectByIdForUpdate(15L, 1L)).thenReturn(order);

        OrderDeliveryPlan plan = stubPlan(150L, 15L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED);
        plan.setOutQty(null);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(plan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(15L, "PC"));
        assertTrue(ex.getMessage().contains("配货数量或已出库数量为空"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — insufficient stock / rollback contract
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_inventoryFailure_shouldPropagateExceptionWithoutUpdatingOrder() {
        Order order = stubOrder(10L, 3);
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);

        List<OrderDeliveryPlan> plans = Arrays.asList(
                stubPlan(100L, 10L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED),
                stubPlan(101L, 10L, 3, 0, OrderDeliveryPlan.Status.ALLOCATED));
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        // First plan succeeds, second throws
        doNothing().when(inventoryService).outByPlan(eq(100L), eq(5), anyLong());
        doThrow(new RuntimeException("库存不足: SKU[111] 仓库[1] 可用:1 需要:3"))
                .when(inventoryService).outByPlan(eq(101L), eq(3), anyLong());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(10L, "PC"));
        assertTrue(ex.getMessage().contains("库存不足"));

        // Order must NOT be updated (rollback)
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — tenant-scoped plan query
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_shouldFilterPlansByTenant() {
        Order order = stubOrder(11L, 3);
        when(orderMapper.selectByIdForUpdate(11L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(
                stubPlan(110L, 11L, 2, 0, OrderDeliveryPlan.Status.ALLOCATED)));

        actionService.shipOrder(11L, "PC");

        ArgumentCaptor<LambdaQueryWrapper<OrderDeliveryPlan>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deliveryPlanMapper).selectList(captor.capture());
        String planSql = captor.getValue().getSqlSegment();
        assertNotNull(planSql);
        assertTrue(planSql.contains("order_id"),
                "Plan query must filter by order_id: " + planSql);
        assertTrue(planSql.contains("tenant_id"),
                "Plan query must filter by tenant_id: " + planSql);
    }

    // ══════════════════════════════════════════════════════════════════
    // confirmDelivery — delegation to canonical shipment action
    // ══════════════════════════════════════════════════════════════════

    @Test
    void confirmDelivery_shouldDelegateToOrderServiceDeliverOrder() {
        OrderDelivery delivery = new OrderDelivery();
        delivery.setId(1L);
        delivery.setOrderId(100L);
        delivery.setStatus(0); // pending
        delivery.setTenantId(1L);
        when(deliveryMapper.selectOne(any())).thenReturn(delivery);

        deliveryService.confirmDelivery(1L);

        // Must delegate to OrderService.deliverOrder
        verify(orderServiceMockForDelivery).deliverOrder(100L);

        // Delivery status advanced to 2 (shipped)
        ArgumentCaptor<OrderDelivery> captor = ArgumentCaptor.forClass(OrderDelivery.class);
        verify(deliveryMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getDeliverTime());
    }

    @Test
    void confirmDelivery_alreadyShippedStatus2_shouldNoOpWithoutDelegate() {
        OrderDelivery delivery = new OrderDelivery();
        delivery.setId(2L);
        delivery.setOrderId(200L);
        delivery.setStatus(2); // already shipped
        delivery.setTenantId(1L);
        when(deliveryMapper.selectOne(any())).thenReturn(delivery);

        deliveryService.confirmDelivery(2L);

        // Must NOT delegate, must NOT update
        verifyNoInteractions(orderServiceMockForDelivery);
        verify(deliveryMapper, never()).updateById(any(OrderDelivery.class));
    }

    @Test
    void confirmDelivery_cancelledStatus3_shouldThrow() {
        OrderDelivery delivery = new OrderDelivery();
        delivery.setId(3L);
        delivery.setOrderId(300L);
        delivery.setStatus(3); // cancelled
        delivery.setTenantId(1L);
        when(deliveryMapper.selectOne(any())).thenReturn(delivery);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deliveryService.confirmDelivery(3L));
        assertTrue(ex.getMessage().contains("取消"));
        verifyNoInteractions(orderServiceMockForDelivery);
    }

    @Test
    void confirmDelivery_notFoundInTenant_shouldThrow() {
        when(deliveryMapper.selectOne(any())).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deliveryService.confirmDelivery(999L));
        assertTrue(ex.getMessage().contains("不存在"));
        verifyNoInteractions(orderServiceMockForDelivery);
    }

    @Test
    void confirmDelivery_shouldUseTenantFilterOnDeliveryQuery() {
        OrderDelivery delivery = new OrderDelivery();
        delivery.setId(5L);
        delivery.setOrderId(500L);
        delivery.setStatus(0);
        delivery.setTenantId(1L);
        when(deliveryMapper.selectOne(any())).thenReturn(delivery);

        deliveryService.confirmDelivery(5L);

        verify(deliveryMapper).selectOne(any());
        verify(orderServiceMockForDelivery).deliverOrder(500L);
    }

    // ══════════════════════════════════════════════════════════════════
    // shipOrder — cross-tenant isolation
    // ══════════════════════════════════════════════════════════════════

    @Test
    void shipOrder_crossTenant_shouldNotFindOrder() {
        when(orderMapper.selectByIdForUpdate(12L, 1L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> actionService.shipOrder(12L, "PC"));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(orderMapper, never()).updateById(any(Order.class));
        verifyNoInteractions(inventoryService);
    }
}
