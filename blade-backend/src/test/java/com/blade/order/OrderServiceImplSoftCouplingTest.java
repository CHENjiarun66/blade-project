package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.inventory.service.InventoryService;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.impl.OrderServiceImpl;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SOW-1 / BE-138: Soft-coupling order from inventory — focused unit tests.
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplSoftCouplingTest {

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

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        // Stub security context so getCurrentUserId() returns 1L without tripping NPE
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(null); // triggers default 1L path
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order stubOrder(Long id, int status) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setPaymentStatus(0);
        order.setWarehouseId(1L);
        order.setTenantId(1L);
        return order;
    }

    /** Captures the Order passed to orderMapper.updateById for assertion. */
    private Order captureUpdatedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ── confirmPayment ───────────────────────────────────────────────

    @Test
    void confirmPayment_shouldUpdateOrderStatusWithoutInventoryInteraction() {
        Order order = stubOrder(1L, 0); // STATUS_CREATED
        when(orderMapper.selectByIdForUpdate(1L, 1L)).thenReturn(order);

        orderService.confirmPayment(1L, new BigDecimal("100.00"));

        Order updated = captureUpdatedOrder();
        assertEquals(1, updated.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(updated.getPaidAmount()));

        // Inventory must NOT be touched
        verifyNoInteractions(inventoryService);
        verify(orderMapper).selectByIdForUpdate(1L, 1L);
        verify(orderMapper, never()).selectById(1L);
    }

    @Test
    void confirmPayment_shouldSyncPaymentStatusToFull() {
        Order order = stubOrder(2L, 0);
        when(orderMapper.selectByIdForUpdate(2L, 1L)).thenReturn(order);

        orderService.confirmPayment(2L, new BigDecimal("100.00"));

        Order updated = captureUpdatedOrder();
        assertEquals(2, updated.getPaymentStatus()); // PAYMENT_FULL
        verifyNoInteractions(inventoryService);
    }

    @Test
    void confirmPayment_shouldSyncPaymentStatusToDeposit() {
        Order order = stubOrder(3L, 0);
        when(orderMapper.selectByIdForUpdate(3L, 1L)).thenReturn(order);

        orderService.confirmPayment(3L, new BigDecimal("50.00"));

        Order updated = captureUpdatedOrder();
        assertEquals(1, updated.getPaymentStatus()); // PAYMENT_DEPOSIT
        verifyNoInteractions(inventoryService);
    }

    @Test
    void confirmPayment_shouldSetPayTime() {
        Order order = stubOrder(4L, 0);
        when(orderMapper.selectByIdForUpdate(4L, 1L)).thenReturn(order);

        orderService.confirmPayment(4L, new BigDecimal("100.00"));

        Order updated = captureUpdatedOrder();
        assertNotNull(updated.getPayTime());
        verifyNoInteractions(inventoryService);
    }

    // ── addPayment ───────────────────────────────────────────────────

    @Test
    void addPayment_shouldNotTouchInventory() {
        // After SOW-3: addPayment uses FOR UPDATE row-lock with tenant
        com.blade.common.tenant.TenantContext.setTenantId(1L);
        try {
            Order order = stubOrder(5L, 1); // STATUS_PAID
            order.setPaidAmount(new BigDecimal("50.00"));
            order.setPaymentStatus(1);
            when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);

            orderService.addPayment(5L, new BigDecimal("30.00"));

            Order updated = captureUpdatedOrder();
            assertEquals(0, new BigDecimal("80.00").compareTo(updated.getPaidAmount()));
            verifyNoInteractions(inventoryService);
        } finally {
            com.blade.common.tenant.TenantContext.clear();
        }
    }

    // ── cancelOrder ──────────────────────────────────────────────────

    @Test
    void cancelOrder_shouldCancelWithoutInventoryRelease() {
        Order order = stubOrder(6L, 1); // STATUS_PAID
        order.setPaymentStatus(1);
        when(orderMapper.selectById(6L)).thenReturn(order);

        orderService.cancelOrder(6L, "客户要求取消");

        Order updated = captureUpdatedOrder();
        assertEquals(6, updated.getStatus()); // STATUS_CANCELLED
        // After SOW-1: cancelOrder must NOT release inventory
        verifyNoInteractions(inventoryService);
    }

    @Test
    void cancelOrder_createdStatus_shouldSucceedWithoutInventoryRelease() {
        Order order = stubOrder(7L, 0); // STATUS_CREATED
        when(orderMapper.selectById(7L)).thenReturn(order);

        orderService.cancelOrder(7L, "测试取消");

        Order updated = captureUpdatedOrder();
        assertEquals(6, updated.getStatus());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void cancelOrder_shouldAppendReasonToRemark() {
        Order order = stubOrder(8L, 0);
        order.setRemark("旧备注");
        when(orderMapper.selectById(8L)).thenReturn(order);

        orderService.cancelOrder(8L, "取消原因测试");

        Order updated = captureUpdatedOrder();
        assertNotNull(updated.getRemark());
        assertTrue(updated.getRemark().contains("取消原因测试"));
    }

    // ── updateStatus → STATUS_CANCELLED ──────────────────────────────

    @Test
    void updateStatus_toCancelled_shouldNotReleaseInventory() {
        Order order = stubOrder(9L, 1); // STATUS_PAID
        when(orderMapper.selectById(9L)).thenReturn(order);

        orderService.updateStatus(9L, 6); // STATUS_CANCELLED

        Order updated = captureUpdatedOrder();
        assertEquals(6, updated.getStatus());
        // After SOW-1: updateStatus(…, CANCELLED) must NOT call releaseInventory
        verifyNoInteractions(inventoryService);
    }

    // ── Guard: other status transitions still work ───────────────────

    @Test
    void updateStatus_toPaid_shouldSetConfirmTime() {
        Order order = stubOrder(10L, 0);
        when(orderMapper.selectById(10L)).thenReturn(order);

        orderService.updateStatus(10L, 1); // STATUS_PAID

        Order updated = captureUpdatedOrder();
        assertEquals(1, updated.getStatus());
        assertNotNull(updated.getConfirmTime());
    }

    @Test
    void updateStatus_toDelivered_shouldSetDeliverTime() {
        Order order = stubOrder(11L, 3); // READY_TO_SHIP
        when(orderMapper.selectById(11L)).thenReturn(order);

        orderService.updateStatus(11L, 4); // STATUS_DELIVERED

        Order updated = captureUpdatedOrder();
        assertEquals(4, updated.getStatus());
        assertNotNull(updated.getDeliverTime());
    }
}
