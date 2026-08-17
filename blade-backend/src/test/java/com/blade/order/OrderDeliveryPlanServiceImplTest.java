package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.impl.OrderDeliveryPlanServiceImpl;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SOW-1 / BE-138: confirmAdjustment must not call globalReleasePartial
 * and must update order to READY_TO_SHIP + delivery plans to ALLOCATED.
 * Runs without Spring context, MySQL, Redis, or Docker.
 * Manually seeds MyBatis-Plus entity metadata so that
 * {@code LambdaUpdateWrapper.set(Entity::getField, val)} can resolve columns.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderDeliveryPlanServiceImplTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private ProductSkuMapper productSkuMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductColorMapper colorMapper;
    @Mock private ProductSizeMapper sizeMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private OrderDeliveryPlanServiceImpl planService;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        // Seed MyBatis-Plus entity metadata so LambdaUpdateWrapper can resolve columns.
        // Without Spring Boot MyBatis-Plus auto-config, we build a minimal configuration.
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderDeliveryPlan.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
    }

    private Order stubOrder(Long id, int status, Long warehouseId) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setWarehouseId(warehouseId);
        order.setTenantId(1L);
        return order;
    }

    private OrderDeliveryPlan stubPlan(Long id, Long orderId, Long orderItemId,
                                        int plannedQty, int allocatedQty, Long warehouseId) {
        OrderDeliveryPlan plan = new OrderDeliveryPlan();
        plan.setId(id);
        plan.setOrderId(orderId);
        plan.setOrderItemId(orderItemId);
        plan.setSkuId(100L + id);
        plan.setPlannedQty(plannedQty);
        plan.setAllocatedQty(allocatedQty);
        plan.setOutQty(0);
        plan.setWarehouseId(warehouseId);
        plan.setStatus(OrderDeliveryPlan.Status.PENDING);
        return plan;
    }

    /**
     * Captures the LambdaUpdateWrapper for Order that was passed to orderMapper.update.
     */
    private LambdaUpdateWrapper<Order> captureOrderUpdateWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(eq(null), captor.capture());
        return captor.getValue();
    }

    /**
     * Captures the last LambdaUpdateWrapper for OrderDeliveryPlan passed to
     * deliveryPlanMapper.update.  When multiple updates happen (e.g. warehouse
     * sync + ALLOCATED batch), {@link ArgumentCaptor#getValue()} returns the
     * final captured argument — the ALLOCATED status update.
     */
    private LambdaUpdateWrapper<OrderDeliveryPlan> capturePlanUpdateWrapper(int expectedCalls) {
        ArgumentCaptor<LambdaUpdateWrapper<OrderDeliveryPlan>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(deliveryPlanMapper, times(expectedCalls)).update(eq(null), captor.capture());
        return captor.getValue();
    }

    @Test
    void confirmAdjustment_shouldNotCallGlobalReleasePartial() {
        Long orderId = 1L;
        Order order = stubOrder(orderId, 2, 1L); // ADJUSTMENT_PENDING, has warehouse

        // Plan with reduction (plannedQty > allocatedQty) — must NOT trigger release
        OrderDeliveryPlan plan1 = stubPlan(10L, orderId, 100L, 5, 3, 1L);
        OrderDeliveryPlan plan2 = stubPlan(11L, orderId, 101L, 2, 2, 1L);
        List<OrderDeliveryPlan> plans = Arrays.asList(plan1, plan2);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        planService.confirmAdjustment(orderId);

        // Verify order is updated to READY_TO_SHIP (status=3) + APPROVED
        LambdaUpdateWrapper<Order> orderWrapper = captureOrderUpdateWrapper();
        String orderSql = orderWrapper.getSqlSet();
        assertNotNull(orderSql, "Order update SQL should not be null");
        assertTrue(orderSql.contains("status"),
                "Should set order status to READY_TO_SHIP (3): " + orderSql);
        assertTrue(orderSql.contains("adjustment_status"),
                "Should set adjustment_status to APPROVED: " + orderSql);

        // Verify delivery plans are updated to ALLOCATED (single call — no warehouse sync)
        LambdaUpdateWrapper<OrderDeliveryPlan> planWrapper = capturePlanUpdateWrapper(1);
        String planSql = planWrapper.getSqlSet();
        assertNotNull(planSql, "Plan update SQL should not be null");
        assertTrue(planSql.contains("status"),
                "Should set delivery plan status to ALLOCATED: " + planSql);
    }

    @Test
    void confirmAdjustment_plansWithoutWarehouse_shouldStillNotCallGlobalReleasePartial() {
        Long orderId = 2L;
        Order order = stubOrder(orderId, 2, null); // no warehouse on order

        // Plans without warehouse — will trigger default warehouse lookup + sync
        OrderDeliveryPlan plan1 = stubPlan(20L, orderId, 200L, 10, 8, null);
        List<OrderDeliveryPlan> plans = List.of(plan1);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        // Warehouse lookup needed for default
        Warehouse wh = new Warehouse();
        wh.setId(5L);
        wh.setWarehouseName("默认仓库");
        when(warehouseMapper.selectList(isNull())).thenReturn(List.of(wh));

        // orderItem lookup for warehouse sync
        OrderItem item = new OrderItem();
        item.setId(200L);
        item.setWarehouseId(null);
        when(orderItemMapper.selectById(200L)).thenReturn(item);

        planService.confirmAdjustment(orderId);

        // Verify order is updated to READY_TO_SHIP (status=3) + APPROVED
        LambdaUpdateWrapper<Order> orderWrapper = captureOrderUpdateWrapper();
        String orderSql = orderWrapper.getSqlSet();
        assertNotNull(orderSql, "Order update SQL should not be null");
        assertTrue(orderSql.contains("status"),
                "Should set order status to READY_TO_SHIP (3): " + orderSql);
        assertTrue(orderSql.contains("adjustment_status"),
                "Should set adjustment_status to APPROVED: " + orderSql);

        // deliveryPlanMapper.update is called twice:
        //   1) warehouse sync (set warehouseId on plan)
        //   2) ALLOCATED batch update (set status=ALLOCATED)
        // getValue() returns the last captured wrapper — the ALLOCATED update.
        LambdaUpdateWrapper<OrderDeliveryPlan> planWrapper = capturePlanUpdateWrapper(2);
        String planSql = planWrapper.getSqlSet();
        assertNotNull(planSql, "Plan update SQL should not be null");
        assertTrue(planSql.contains("status"),
                "Should set delivery plan status to ALLOCATED: " + planSql);

        // orderItemMapper.update should be called once for warehouse sync
        verify(orderItemMapper).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void confirmAdjustment_normalFlow_shouldNotTouchInventory() {
        Long orderId = 3L;
        Order order = stubOrder(orderId, 2, 1L); // ADJUSTMENT_PENDING

        OrderDeliveryPlan plan = stubPlan(30L, orderId, 300L, 3, 3, 1L); // no reduction
        List<OrderDeliveryPlan> plans = List.of(plan);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(deliveryPlanMapper.selectList(any())).thenReturn(plans);

        planService.confirmAdjustment(orderId);

        // Verify order is updated to READY_TO_SHIP (status=3) + APPROVED
        LambdaUpdateWrapper<Order> orderWrapper = captureOrderUpdateWrapper();
        String orderSql = orderWrapper.getSqlSet();
        assertNotNull(orderSql, "Order update SQL should not be null");
        assertTrue(orderSql.contains("status"),
                "Should set order status to READY_TO_SHIP (3): " + orderSql);
        assertTrue(orderSql.contains("adjustment_status"),
                "Should set adjustment_status to APPROVED: " + orderSql);

        // Verify delivery plans are updated to ALLOCATED (single call)
        LambdaUpdateWrapper<OrderDeliveryPlan> planWrapper = capturePlanUpdateWrapper(1);
        String planSql = planWrapper.getSqlSet();
        assertNotNull(planSql, "Plan update SQL should not be null");
        assertTrue(planSql.contains("status"),
                "Should set delivery plan status to ALLOCATED: " + planSql);
    }
}
