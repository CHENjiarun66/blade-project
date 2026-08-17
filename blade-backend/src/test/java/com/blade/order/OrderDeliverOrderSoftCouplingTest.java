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
import com.blade.order.mapper.OrderDeliveryMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderService;
import com.blade.order.service.impl.OrderDeliveryServiceImpl;
import com.blade.order.service.impl.OrderServiceImpl;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.blade.file.service.FileService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SOW-2 / BE-126/BE-139/BE-142: Shipment path unification — focused unit tests.
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

    // ── OrderServiceImpl mocks ────────────────────────────────────────
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private ProductSkuMapper productSkuMapper;
    @Mock private ProductColorMapper productColorMapper;
    @Mock private ProductSizeMapper productSizeMapper;
    @Mock private ProductMapper productMapper;
    @Mock private InventoryService inventoryService;
    @Mock private UserMapper userMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private RedissonClient redissonClient;
    @Mock private FileService fileService;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ── OrderDeliveryServiceImpl mocks ────────────────────────────────
    @Mock private OrderDeliveryMapper deliveryMapper;
    @Mock private com.blade.order.mapper.OrderDeliveryItemMapper deliveryItemMapper;
    @Mock private OrderService orderServiceMockForDelivery;

    @InjectMocks
    private OrderDeliveryServiceImpl deliveryService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
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
        o.setTenantId(1L);
        o.setWarehouseId(1L);
        return o;
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
    // deliverOrder — whole shipment (happy path)
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_shouldShipWholeOrderAndAdvanceStatusToDelivered() {
        Order order = stubOrder(1L, 3); // READY_TO_SHIP
        when(orderMapper.selectByIdForUpdate(1L, 1L)).thenReturn(order);

        List<OrderDeliveryPlan> plans = Arrays.asList(
                stubPlan(10L, 1L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED),
                stubPlan(11L, 1L, 3, 0, OrderDeliveryPlan.Status.ALLOCATED));
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        orderService.deliverOrder(1L);

        // Each non-OUT plan should call outByPlan
        verify(inventoryService).outByPlan(eq(10L), eq(5), anyLong());
        verify(inventoryService).outByPlan(eq(11L), eq(3), anyLong());

        // Order advanced to DELIVERED
        Order updated = captorUpdatedOrder();
        assertEquals(4, updated.getStatus());
        assertEquals(1, updated.getIsDelivered());
        assertNotNull(updated.getDeliveredAt());
        assertNotNull(updated.getDeliverTime());
    }

    @Test
    void deliverOrder_shouldSkipAlreadyOutPlans() {
        Order order = stubOrder(2L, 3);
        when(orderMapper.selectByIdForUpdate(2L, 1L)).thenReturn(order);

        List<OrderDeliveryPlan> plans = Arrays.asList(
                stubPlan(20L, 2L, 5, 5, OrderDeliveryPlan.Status.OUT),  // already fully out
                stubPlan(21L, 2L, 3, 0, OrderDeliveryPlan.Status.ALLOCATED));
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        orderService.deliverOrder(2L);

        // Only the ALLOCATED plan should trigger outByPlan
        verify(inventoryService, never()).outByPlan(eq(20L), anyInt(), anyLong());
        verify(inventoryService).outByPlan(eq(21L), eq(3), anyLong());
    }

    @Test
    void deliverOrder_shouldUseSelectByIdForUpdateWithTenantFilter() {
        Order order = stubOrder(3L, 3);
        when(orderMapper.selectByIdForUpdate(3L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(
                stubPlan(30L, 3L, 2, 0, OrderDeliveryPlan.Status.ALLOCATED)));

        orderService.deliverOrder(3L);

        // Must use selectByIdForUpdate, never plain selectById
        verify(orderMapper).selectByIdForUpdate(3L, 1L);
        verify(orderMapper, never()).selectById(anyLong());
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — idempotency (no-op)
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_alreadyDelivered_shouldNoOpWithoutInventoryInteraction() {
        Order order = stubOrder(4L, 4); // DELIVERED
        when(orderMapper.selectByIdForUpdate(4L, 1L)).thenReturn(order);

        orderService.deliverOrder(4L);

        // No inventory operations, no order update
        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void deliverOrder_alreadyCompleted_shouldNoOpWithoutInventoryInteraction() {
        Order order = stubOrder(5L, 5); // COMPLETED
        when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);

        orderService.deliverOrder(5L);

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — wrong status rejection
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_wrongStatusCreated_shouldThrowWithoutInventory() {
        Order order = stubOrder(6L, 0); // CREATED
        when(orderMapper.selectByIdForUpdate(6L, 1L)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(6L));
        assertTrue(ex.getMessage().contains("待发货"));
        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void deliverOrder_wrongStatusPaid_shouldThrowWithoutInventory() {
        Order order = stubOrder(7L, 1); // PAID
        when(orderMapper.selectByIdForUpdate(7L, 1L)).thenReturn(order);

        assertThrows(RuntimeException.class, () -> orderService.deliverOrder(7L));
        verifyNoInteractions(inventoryService);
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — no / invalid plans
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_emptyPlans_shouldThrow() {
        Order order = stubOrder(8L, 3);
        when(orderMapper.selectByIdForUpdate(8L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(Collections.emptyList());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(8L));
        assertTrue(ex.getMessage().contains("配货计划"));
        verifyNoInteractions(inventoryService);
    }

    @Test
    void deliverOrder_plansWithPendingStatus_shouldThrow() {
        Order order = stubOrder(9L, 3);
        when(orderMapper.selectByIdForUpdate(9L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(
                stubPlan(90L, 9L, 3, 0, OrderDeliveryPlan.Status.PENDING)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(9L));
        assertTrue(ex.getMessage().contains("状态异常"));
        verifyNoInteractions(inventoryService);
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — non-OUT plan with zero/negative remaining
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_nonOutPlan_zeroRemaining_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(12L, 3); // READY_TO_SHIP
        when(orderMapper.selectByIdForUpdate(12L, 1L)).thenReturn(order);

        // Non-OUT plan with allocatedQty == outQty → toOutQty == 0
        OrderDeliveryPlan zeroRemainPlan = stubPlan(120L, 12L, 5, 5, OrderDeliveryPlan.Status.ALLOCATED);
        zeroRemainPlan.setAllocatedQty(5);
        zeroRemainPlan.setOutQty(5);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(zeroRemainPlan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(12L));
        assertTrue(ex.getMessage().contains("无待出库数量"));
        assertTrue(ex.getMessage().contains("planId=120"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void deliverOrder_nonOutPlan_negativeRemaining_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(13L, 3); // READY_TO_SHIP
        when(orderMapper.selectByIdForUpdate(13L, 1L)).thenReturn(order);

        // Non-OUT plan with allocatedQty < outQty → toOutQty < 0 (data corruption)
        OrderDeliveryPlan negRemainPlan = stubPlan(130L, 13L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED);
        negRemainPlan.setAllocatedQty(3);
        negRemainPlan.setOutQty(5); // out > allocated
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(negRemainPlan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(13L));
        assertTrue(ex.getMessage().contains("无待出库数量"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void deliverOrder_nonOutPlan_nullAllocatedQty_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(14L, 3);
        when(orderMapper.selectByIdForUpdate(14L, 1L)).thenReturn(order);

        OrderDeliveryPlan plan = stubPlan(140L, 14L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED);
        plan.setAllocatedQty(null);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(plan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(14L));
        assertTrue(ex.getMessage().contains("配货数量或已出库数量为空"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void deliverOrder_nonOutPlan_nullOutQty_shouldThrowAndNotUpdateOrder() {
        Order order = stubOrder(15L, 3);
        when(orderMapper.selectByIdForUpdate(15L, 1L)).thenReturn(order);

        OrderDeliveryPlan plan = stubPlan(150L, 15L, 5, 0, OrderDeliveryPlan.Status.ALLOCATED);
        plan.setOutQty(null);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(plan));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(15L));
        assertTrue(ex.getMessage().contains("配货数量或已出库数量为空"));

        verifyNoInteractions(inventoryService);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — insufficient stock / rollback contract
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_inventoryFailure_shouldPropagateExceptionWithoutUpdatingOrder() {
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
                () -> orderService.deliverOrder(10L));
        assertTrue(ex.getMessage().contains("库存不足"));

        // Order must NOT be updated (rollback)
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — tenant-scoped plan query
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_shouldFilterPlansByTenant() {
        Order order = stubOrder(11L, 3);
        when(orderMapper.selectByIdForUpdate(11L, 1L)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(List.of(
                stubPlan(110L, 11L, 2, 0, OrderDeliveryPlan.Status.ALLOCATED)));

        orderService.deliverOrder(11L);

        // Capture the LambdaQueryWrapper to assert it was called with tenant filter
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
    // confirmDelivery — delegation to canonical deliverOrder
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

        // Verify selectOne was called (with tenant-filtered wrapper)
        verify(deliveryMapper).selectOne(any());
        verify(orderServiceMockForDelivery).deliverOrder(500L);
    }

    // ══════════════════════════════════════════════════════════════════
    // deliverOrder — cross-tenant isolation
    // ══════════════════════════════════════════════════════════════════

    @Test
    void deliverOrder_crossTenant_shouldNotFindOrder() {
        when(orderMapper.selectByIdForUpdate(12L, 1L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.deliverOrder(12L));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(orderMapper, never()).updateById(any(Order.class));
        verifyNoInteractions(inventoryService);
    }
}
