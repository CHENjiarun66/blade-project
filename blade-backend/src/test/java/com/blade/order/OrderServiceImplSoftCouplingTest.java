package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.customer.mapper.CustomerMapper;
import com.blade.customer.service.CustomerStatsCacheService;
import com.blade.file.service.FileService;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.common.exception.BusinessException;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Soft-coupling order from inventory — focused unit tests.
 * 订单写路径已收敛到统一动作服务：本测试验证旧接口委托与"绝不触碰库存"的软解耦契约。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplSoftCouplingTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderFinancialRecordMapper financialRecordMapper;
    @Mock private ProductSkuMapper productSkuMapper;
    @Mock private ProductColorMapper productColorMapper;
    @Mock private ProductSizeMapper productSizeMapper;
    @Mock private ProductMapper productMapper;
    @Mock private InventoryService inventoryService;
    @Mock private UserMapper userMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private RedissonClient redissonClient;
    @Mock private FileService fileService;
    @Mock private OrderFinanceSnapshotService snapshotService;
    @Mock private OrderActionService actionService;
    @Mock private com.blade.order.service.OrderAccessPolicy accessPolicy;
    @Mock private CustomerStatsCacheService customerStatsCacheService;
    @Mock private CustomerMapper customerMapper;

    @InjectMocks
    private OrderServiceImpl orderService;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        lenient().when(accessPolicy.canAccess(any(Order.class))).thenReturn(true);
        // Stub security context so getCurrentUserId() returns 1L without tripping NPE
        SecurityContext securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(null); // triggers default 1L path
        lenient().when(authentication.getAuthorities()).thenReturn((java.util.Collection) java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:viewAll"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:viewFinance"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:view")));
        SecurityContextHolder.setContext(securityContext);
        lenient().when(snapshotService.records(anyLong(), anyLong())).thenReturn(List.of());
        lenient().when(orderMapper.updateById(any(Order.class))).thenReturn(1);
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

    @Test
    void getById_withoutAmountFieldPermissions_shouldReturnNullInsteadOfZeroOrSensitiveValues() {
        Order order = stubOrder(1L, 0);
        order.setOriginalAmount(new BigDecimal("120.00"));
        order.setDepositAmount(new BigDecimal("20.00"));
        order.setFreightAmount(new BigDecimal("5.00"));
        order.setFreightCost(new BigDecimal("2.00"));
        order.setTotalCostAmount(new BigDecimal("60.00"));
        order.setGrossProfit(new BigDecimal("45.00"));
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        com.blade.order.dto.OrderVO result = orderService.getById(1L);

        assertNull(result.getTotalAmount());
        assertNull(result.getOriginalAmount());
        assertNull(result.getPaidAmount());
        assertNull(result.getDepositAmount());
        assertNull(result.getFreightCost());
        assertNull(result.getGrossProfit());
    }

    // ── confirmPayment 委托统一收款动作 ───────────────────────────────

    @Test
    void confirmPayment_shouldDelegateToActionServiceWithoutInventoryInteraction() {
        orderService.confirmPayment(1L, new BigDecimal("100.00"));

        verify(actionService).recordPayment(1L, new BigDecimal("100.00"), null, null, "PC");
        // Inventory must NOT be touched
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(orderMapper);
    }

    // ── addPayment 委托统一动作服务 ──────────────────────────────────

    @Test
    void addPayment_shouldDelegateCompatWithoutInventoryInteraction() {
        AddPaymentDTO dto = new AddPaymentDTO();
        dto.setAdditionalAmount(new BigDecimal("30.00"));

        orderService.addPayment(5L, dto);

        verify(actionService).addPaymentCompat(5L, dto);
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void addPaymentBigDecimal_shouldDelegateThroughDtoOverload() {
        orderService.addPayment(4L, new BigDecimal("40.00"));

        ArgumentCaptor<AddPaymentDTO> captor = ArgumentCaptor.forClass(AddPaymentDTO.class);
        verify(actionService).addPaymentCompat(eq(4L), captor.capture());
        assertEquals(0, new BigDecimal("40.00").compareTo(captor.getValue().getAdditionalAmount()));
        verifyNoInteractions(inventoryService);
    }

    // ── cancelOrder 委托统一取消动作 ─────────────────────────────────

    @Test
    void cancelOrder_shouldDelegateWithoutInventoryRelease() {
        orderService.cancelOrder(6L, "客户要求取消");

        verify(actionService).cancelOrder(6L, "客户要求取消", "PC");
        // 取消不释放库存（软解耦契约）
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void cancelOrder_createdStatus_shouldSucceedWithoutInventoryRelease() {
        orderService.cancelOrder(7L, "测试取消");

        verify(actionService).cancelOrder(7L, "测试取消", "PC");
        verifyNoInteractions(inventoryService);
    }

    // ── deliverOrder 委托统一发货事务 ────────────────────────────────

    @Test
    void deliverOrder_shouldDelegateToActionService() {
        orderService.deliverOrder(11L);

        verify(actionService).shipOrder(11L, "PC");
    }

    // ── delete 只允许删除无事实订单，软删除 ─────────────────────────

    @Test
    void delete_shouldSoftDeleteFactFreeConfirmedOrder() {
        Order order = stubOrder(20L, 0);
        order.setFulfillmentStatus("CONFIRMED");
        order.setCollectionStatus("UNPAID");
        when(orderMapper.selectById(20L)).thenReturn(order);
        when(financialRecordMapper.selectCount(any())).thenReturn(0L);
        when(deliveryPlanMapper.selectCount(any())).thenReturn(0L);

        orderService.delete(20L);

        // 全局逻辑删除配置：deleteById 即软删除（UPDATE deleted=1）
        verify(orderMapper).deleteById(20L);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void delete_shouldRejectOrderWithFinancialRecords() {
        Order order = stubOrder(21L, 0);
        order.setFulfillmentStatus("CONFIRMED");
        order.setCollectionStatus("PARTIAL");
        when(orderMapper.selectById(21L)).thenReturn(order);
        when(financialRecordMapper.selectCount(any())).thenReturn(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.delete(21L));
        assertTrue(ex.getMessage().contains("财务流水"));
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void delete_shouldRejectShippedOrder() {
        Order order = stubOrder(22L, 4);
        when(orderMapper.selectById(22L)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.delete(22L));
        assertTrue(ex.getMessage().contains("待处理"));
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void updateFinancialFields_shouldAllowPartialReceiptBeforeFulfillment() {
        Order order = stubOrder(30L, 0);
        order.setFulfillmentStatus("CONFIRMED");
        order.setFulfillmentMode("UNDECIDED");
        order.setCollectionStatus("PARTIAL");
        order.setFreightAmount(BigDecimal.ZERO);
        order.setFreightCost(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(new BigDecimal("40.00"));
        when(orderMapper.selectById(30L)).thenReturn(order);
        when(financialRecordMapper.selectCount(any())).thenReturn(1L);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setId(30L);
        dto.setFreightAmount(new BigDecimal("100.00"));

        orderService.update(dto);

        verify(orderMapper).updateById(order);
        verify(snapshotService).recalculateAndApply(order);
    }

    @Test
    void updateOrderContent_shouldRejectAfterSettlement() {
        Order order = stubOrder(31L, 0);
        order.setFulfillmentStatus("CONFIRMED");
        order.setFulfillmentMode("UNDECIDED");
        order.setCollectionStatus("SETTLED");
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        when(orderMapper.selectById(31L)).thenReturn(order);
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setId(31L);
        dto.setCustomerName("不能修改");

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.update(dto));

        assertEquals(400, ex.getCode());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void updateOrderContent_shouldDetachInvalidCustomerButKeepSnapshot() {
        Order order = stubOrder(32L, 0);
        order.setCustomerId(8L);
        order.setFulfillmentStatus("CONFIRMED");
        order.setFulfillmentMode("UNDECIDED");
        order.setCollectionStatus("UNPAID");
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        when(orderMapper.selectById(32L)).thenReturn(order);
        when(customerMapper.selectById(999L)).thenReturn(null);
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setId(32L);
        dto.setCustomerId(999L);
        dto.setCustomerName("未建档客户");

        orderService.update(dto);

        assertNull(order.getCustomerId());
        assertEquals("未建档客户", order.getCustomerName());
        verify(orderMapper).updateById(order);
    }

    @Test
    void updateOrderContent_shouldIgnoreCostFieldsWithoutCostPermission() {
        Order order = stubOrder(33L, 0);
        order.setFulfillmentStatus("CONFIRMED");
        order.setFulfillmentMode("UNDECIDED");
        order.setCollectionStatus("UNPAID");
        order.setFreightCost(new BigDecimal("6.00"));
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        when(orderMapper.selectById(33L)).thenReturn(order);
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setId(33L);
        dto.setFreightCost(new BigDecimal("999.00"));

        orderService.update(dto);

        assertEquals(0, new BigDecimal("6.00").compareTo(order.getFreightCost()));
        verify(snapshotService, never()).recalculateAndApply(order);
        verify(orderMapper).updateById(order);
    }
}
