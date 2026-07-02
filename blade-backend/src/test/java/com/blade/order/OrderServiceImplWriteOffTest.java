package com.blade.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.dto.OrderExportDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.impl.OrderServiceImpl;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.mapper.UserMapper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SOW-3 / BE-124 &amp; BE-140: Write-off settlement and unified financial formulas.
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplWriteOffTest {

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

    private static final Long TENANT_ID = 1L;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
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

    /** Creates a basic order with default financial fields. */
    private Order stubOrder(Long id, int status) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setPaymentStatus(0);
        order.setTenantId(TENANT_ID);
        order.setWarehouseId(1L);
        return order;
    }

    /** Captures the Order passed to orderMapper.updateById for assertion. */
    private Order captureUpdatedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        return captor.getValue();
    }

    /** Builds a DTO for the new addPayment(Long, AddPaymentDTO) path. */
    private AddPaymentDTO dto(BigDecimal amount, Boolean markAsSettled, String reason) {
        AddPaymentDTO dto = new AddPaymentDTO();
        dto.setAdditionalAmount(amount);
        dto.setMarkAsSettled(markAsSettled);
        dto.setWriteOffReason(reason);
        return dto;
    }

    // ── Normal addPayment via DTO ────────────────────────────────────────

    @Test
    void addPaymentDto_shouldAcceptPositiveAmount() {
        Order order = stubOrder(1L, 1); // STATUS_PAID
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(1L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(1L, dto(new BigDecimal("20.00"), false, null));

        Order updated = captureUpdatedOrder();
        assertEquals(0, new BigDecimal("50.00").compareTo(updated.getPaidAmount()));
        verifyNoInteractions(inventoryService);
    }

    @Test
    void addPaymentDto_shouldRejectZeroAmountWithoutMarkSettled() {
        Order order = stubOrder(2L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(2L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(2L, dto(BigDecimal.ZERO, false, null)));
    }

    @Test
    void addPaymentDto_shouldRejectOverNetReceivable() {
        Order order = stubOrder(3L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        // netReceivable = 100 - 0 - 0 = 100, balance = 70
        when(orderMapper.selectByIdForUpdate(3L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(3L, dto(new BigDecimal("80.00"), false, null)));
    }

    @Test
    void addPaymentDto_overNetShouldReportCurrentBalance() {
        Order order = stubOrder(33L, 1);
        order.setPaidAmount(new BigDecimal("90.00"));
        order.setPaymentStatus(1);
        // balance = 10
        when(orderMapper.selectByIdForUpdate(33L, TENANT_ID)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.addPayment(33L, dto(new BigDecimal("20.00"), false, null)));
        assertTrue(ex.getMessage().contains("10"));
    }

    // ── BigDecimal overload delegation ───────────────────────────────────

    @Test
    void addPaymentBigDecimal_shouldDelegateToDtoOverload() {
        Order order = stubOrder(4L, 1);
        order.setPaidAmount(new BigDecimal("10.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(4L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(4L, new BigDecimal("40.00"));

        Order updated = captureUpdatedOrder();
        assertEquals(0, new BigDecimal("50.00").compareTo(updated.getPaidAmount()));
        // Must use FOR UPDATE
        verify(orderMapper).selectByIdForUpdate(eq(4L), eq(TENANT_ID));
    }

    // ── Mark-as-settled ──────────────────────────────────────────────────

    @Test
    void markAsSettled_withZeroPayment_shouldWriteOffRemainingBalance() {
        Order order = stubOrder(5L, 1);
        order.setPaidAmount(new BigDecimal("80.00"));
        order.setPaymentStatus(1);
        // netReceivable = 100, balance = 20
        when(orderMapper.selectByIdForUpdate(5L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(5L, dto(BigDecimal.ZERO, true, "客户少付20元"));

        Order updated = captureUpdatedOrder();
        assertEquals(0, updated.getPaidAmount().compareTo(new BigDecimal("80.00"))); // unchanged
        assertEquals(0, updated.getWriteOffAmount().compareTo(new BigDecimal("20.00")));
        assertEquals("客户少付20元", updated.getWriteOffReason());
        assertEquals(2, updated.getPaymentStatus()); // PAYMENT_FULL
        verifyNoInteractions(inventoryService);
    }

    @Test
    void markAsSettled_withPositivePayment_shouldAccumulatePaymentAndWriteOff() {
        Order order = stubOrder(6L, 1);
        order.setPaidAmount(new BigDecimal("70.00"));
        order.setPaymentStatus(1);
        // netReceivable = 100, pay 10 more, remaining = 20 → write_off
        when(orderMapper.selectByIdForUpdate(6L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(6L, dto(new BigDecimal("10.00"), true, "尾款抹零"));

        Order updated = captureUpdatedOrder();
        assertEquals(0, updated.getPaidAmount().compareTo(new BigDecimal("80.00")));
        assertEquals(0, updated.getWriteOffAmount().compareTo(new BigDecimal("20.00")));
        assertEquals("尾款抹零", updated.getWriteOffReason());
        assertEquals(2, updated.getPaymentStatus());
    }

    @Test
    void markAsSettled_shouldPreserveExistingWriteOffAndAddNewRemainder() {
        Order order = stubOrder(7L, 1);
        order.setPaidAmount(new BigDecimal("80.00"));
        order.setWriteOffAmount(new BigDecimal("5.00"));
        order.setWriteOffReason("之前抹零5元");
        order.setPaymentStatus(1);
        // netReceivable = 100-0-5 = 95, paid=80, balance=15
        when(orderMapper.selectByIdForUpdate(7L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(7L, dto(BigDecimal.ZERO, true, "再抹15元"));

        Order updated = captureUpdatedOrder();
        // Existing writeOff=5 + newRemainder=15 = 20
        assertEquals(0, updated.getWriteOffAmount().compareTo(new BigDecimal("20.00")));
        assertEquals("再抹15元", updated.getWriteOffReason());
        assertEquals(2, updated.getPaymentStatus());
    }

    @Test
    void markAsSettled_shouldRequireNonblankReason() {
        Order order = stubOrder(8L, 1);
        order.setPaidAmount(new BigDecimal("80.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(8L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(8L, dto(BigDecimal.ZERO, true, "")));
        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(8L, dto(BigDecimal.ZERO, true, null)));
        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(8L, dto(BigDecimal.ZERO, true, "   ")));
    }

    @Test
    void markAsSettled_shouldRejectWhenBalanceAlreadyZero() {
        Order order = stubOrder(9L, 1);
        order.setPaidAmount(new BigDecimal("100.00"));
        order.setPaymentStatus(2); // already full
        when(orderMapper.selectByIdForUpdate(9L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(9L, dto(BigDecimal.ZERO, true, "不需要")));
    }

    @Test
    void markAsSettled_shouldRejectOnAlreadySettledOrder() {
        Order order = stubOrder(99L, 1);
        order.setPaidAmount(new BigDecimal("100.00"));
        order.setPaymentStatus(2); // PAYMENT_FULL
        when(orderMapper.selectByIdForUpdate(99L, TENANT_ID)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.addPayment(99L, dto(BigDecimal.ZERO, true, "冗余结清")));
        assertTrue(ex.getMessage().contains("已结清"));
    }

    @Test
    void markAsSettled_shouldRejectWhenFullPaymentLeavesNoBalance() {
        // If additional payment alone brings paid to netReceivable, balance=0 → reject settlement
        Order order = stubOrder(10L, 1);
        order.setPaidAmount(new BigDecimal("50.00"));
        order.setPaymentStatus(1);
        // netReceivable = 100, pay 50 more → balance = 0
        when(orderMapper.selectByIdForUpdate(10L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(10L, dto(new BigDecimal("50.00"), true, "no need")));
    }

    // ── State and inventory isolation ─────────────────────────────────────

    @Test
    void addPayment_shouldNotChangeOrderStatus() {
        Order order = stubOrder(11L, 1); // STATUS_PAID
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(11L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(11L, dto(new BigDecimal("20.00"), false, null));

        Order updated = captureUpdatedOrder();
        assertEquals(1, updated.getStatus()); // unchanged
        verifyNoInteractions(inventoryService);
    }

    @Test
    void addPayment_shouldNotTouchInventory() {
        Order order = stubOrder(12L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(12L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(12L, dto(new BigDecimal("20.00"), false, null));

        // No inventory interaction
        verifyNoInteractions(inventoryService);
    }

    // ── Tenant-scoped FOR UPDATE ─────────────────────────────────────────

    @Test
    void addPaymentDto_shouldUseSelectByIdForUpdateWithTenant() {
        Order order = stubOrder(13L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(13L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(13L, dto(new BigDecimal("10.00"), false, null));

        verify(orderMapper).selectByIdForUpdate(eq(13L), eq(TENANT_ID));
        verify(orderMapper, never()).selectById(any());
    }

    @Test
    void addPaymentDto_shouldRejectWhenNoTenant() {
        TenantContext.clear();
        Order order = stubOrder(14L, 1);
        order.setTenantId(null);
        when(orderMapper.selectByIdForUpdate(14L, null)).thenReturn(order);

        // TenantContext.getTenantId() returns null → exception before mapper call
        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(14L, dto(new BigDecimal("10.00"), false, null)));
    }

    // ── Payment status transitions ───────────────────────────────────────

    @Test
    void addPayment_shouldSetPaymentStatusToFullWhenPaidReachesNetReceivable() {
        Order order = stubOrder(15L, 1);
        order.setPaidAmount(new BigDecimal("50.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(15L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(15L, dto(new BigDecimal("50.00"), false, null));

        Order updated = captureUpdatedOrder();
        assertEquals(2, updated.getPaymentStatus()); // PAYMENT_FULL
    }

    @Test
    void addPayment_shouldKeepPaymentStatusDepositWhenPartial() {
        Order order = stubOrder(16L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(16L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(16L, dto(new BigDecimal("20.00"), false, null));

        Order updated = captureUpdatedOrder();
        assertEquals(1, updated.getPaymentStatus()); // PAYMENT_DEPOSIT
    }

    // ── NetReceivable formula with refund and writeOff ────────────────────

    @Test
    void addPayment_shouldRespectNetReceivableWithRefund() {
        Order order = stubOrder(17L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setRefundAmount(new BigDecimal("10.00"));
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setPaymentStatus(1);
        // netReceivable = 100 - 10 - 0 = 90, balance = 60
        when(orderMapper.selectByIdForUpdate(17L, TENANT_ID)).thenReturn(order);

        // 70 would exceed netReceivable (30 + 70 = 100 > 90)
        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(17L, dto(new BigDecimal("70.00"), false, null)));

        // 60 should be OK (30 + 60 = 90 ≤ 90)
    }

    @Test
    void addPayment_shouldAllowFullPaymentEqualToNetReceivable() {
        Order order = stubOrder(171L, 1);
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setRefundAmount(new BigDecimal("10.00"));
        order.setPaymentStatus(1);
        // netReceivable = 90, balance = 60
        when(orderMapper.selectByIdForUpdate(171L, TENANT_ID)).thenReturn(order);

        orderService.addPayment(171L, dto(new BigDecimal("60.00"), false, null));

        Order updated = captureUpdatedOrder();
        assertEquals(0, updated.getPaidAmount().compareTo(new BigDecimal("90.00")));
        assertEquals(2, updated.getPaymentStatus());
    }

    @Test
    void addPayment_shouldRespectNetReceivableWithWriteOff() {
        Order order = stubOrder(18L, 1);
        order.setPaidAmount(new BigDecimal("50.00"));
        order.setWriteOffAmount(new BigDecimal("20.00"));
        order.setPaymentStatus(1);
        // netReceivable = 100 - 0 - 20 = 80, balance = 30
        when(orderMapper.selectByIdForUpdate(18L, TENANT_ID)).thenReturn(order);

        // 40 would exceed netReceivable (50 + 40 = 90 > 80)
        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(18L, dto(new BigDecimal("40.00"), false, null)));
    }

    @Test
    void addPayment_netReceivableFloorAtZero() {
        Order order = stubOrder(19L, 1);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setRefundAmount(new BigDecimal("60.00")); // refund > total → netReceivable = 0
        order.setPaymentStatus(1);
        when(orderMapper.selectByIdForUpdate(19L, TENANT_ID)).thenReturn(order);

        // netReceivable = max(50-60-0, 0) = 0, so even 0.01 should be rejected
        assertThrows(RuntimeException.class, () ->
                orderService.addPayment(19L, dto(new BigDecimal("0.01"), false, null)));
    }

    // ── VO balance formula ───────────────────────────────────────────────

    @Test
    void convertToVO_balanceShouldUseUnifiedFormula() {
        Order order = stubOrder(20L, 1);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setRefundAmount(new BigDecimal("10.00"));
        order.setWriteOffAmount(new BigDecimal("5.00"));
        // balance = max(100-10-5-30, 0) = 55
        order.setOrderNo("ORD-TEST");
        order.setOrderType("SPOT");
        order.setPaymentStatus(1);

        // For convertToVO we need orderItemMapper to return empty
        when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of());

        OrderVO vo = callConvertToVO(order);

        assertEquals(0, vo.getBalanceAmount().compareTo(new BigDecimal("55.00")));
        assertEquals(0, vo.getWriteOffAmount().compareTo(new BigDecimal("5.00")));
    }

    @Test
    void convertToVO_balanceShouldFloorAtZero() {
        Order order = stubOrder(21L, 1);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaidAmount(new BigDecimal("100.00"));
        order.setRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setOrderNo("ORD-TEST");
        order.setOrderType("SPOT");
        order.setPaymentStatus(2);

        when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of());

        OrderVO vo = callConvertToVO(order);

        assertEquals(0, vo.getBalanceAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, vo.getWriteOffAmount().compareTo(BigDecimal.ZERO));
    }

    // ── Export formula ───────────────────────────────────────────────────

    @Test
    void export_balanceShouldUseUnifiedFormula() {
        Order order = stubOrder(22L, 1);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPaidAmount(new BigDecimal("30.00"));
        order.setRefundAmount(new BigDecimal("10.00"));
        order.setWriteOffAmount(new BigDecimal("5.00"));
        // balance = max(100-10-5-30, 0) = 55
        order.setOrderNo("ORD-E1");
        order.setOrderType("SPOT");
        order.setPaymentStatus(1);
        order.setOrderDate(java.time.LocalDate.now());
        order.setCreateTime(java.time.LocalDateTime.now());
        order.setSalesmanName("tester");

        when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of());

        // Trigger export
        com.blade.order.dto.OrderPageDTO pageDto = new com.blade.order.dto.OrderPageDTO();
        pageDto.setCurrent(1L);
        pageDto.setSize(1L);
        // Mock the export query
        com.baomidou.mybatisplus.core.metadata.IPage<Order> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1);
        mockPage.setRecords(java.util.List.of(order));
        mockPage.setTotal(1);
        when(orderMapper.selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any())).thenReturn(mockPage);

        java.util.List<OrderExportDTO> exports = orderService.exportOrders(pageDto);

        assertEquals(1, exports.size());
        OrderExportDTO export = exports.get(0);
        assertEquals(0, export.getBalanceAmount().compareTo(new BigDecimal("55.00")));
        assertEquals(0, export.getWriteOffAmount().compareTo(new BigDecimal("5.00")));
    }

    // ── Payment status name labels ───────────────────────────────────────

    @Test
    void getById_paymentStatusNameLabelsShouldBeUpdated() {
        // Test via convertToVO which calls getPaymentStatusName
        Order order = stubOrder(23L, 1);
        order.setOrderNo("ORD-LBL");
        order.setOrderType("SPOT");
        order.setPaymentStatus(1); // 部分收款
        when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of());

        OrderVO vo = callConvertToVO(order);
        assertEquals("部分收款", vo.getPaymentStatusName());
    }

    @Test
    void getById_settledLabelShouldBeUsed() {
        Order order = stubOrder(24L, 1);
        order.setOrderNo("ORD-LBL2");
        order.setOrderType("SPOT");
        order.setPaymentStatus(2); // 已结清
        when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of());

        OrderVO vo = callConvertToVO(order);
        assertEquals("已结清", vo.getPaymentStatusName());
    }

    @Test
    void getById_unpaidLabelShouldRemain() {
        Order order = stubOrder(25L, 1);
        order.setOrderNo("ORD-LBL3");
        order.setOrderType("SPOT");
        order.setPaymentStatus(0); // 未付款
        when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of());

        OrderVO vo = callConvertToVO(order);
        assertEquals("未付款", vo.getPaymentStatusName());
    }

    // ── hasBalance SQL ───────────────────────────────────────────────────

    @Test
    void pageList_hasBalanceTrue_shouldUseUnifiedFormula() {
        com.blade.order.dto.OrderPageDTO dto = new com.blade.order.dto.OrderPageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setHasBalance(true);

        com.baomidou.mybatisplus.core.metadata.IPage<Order> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        mockPage.setRecords(java.util.List.of());
        mockPage.setTotal(0);

        when(orderMapper.selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any()))
                .thenReturn(mockPage);

        orderService.pageList(dto);

        ArgumentCaptor<Wrapper<Order>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("COALESCE(paid_amount, 0) < GREATEST"));
        assertTrue(sql.contains("COALESCE(refund_amount, 0)"));
        assertTrue(sql.contains("COALESCE(write_off_amount, 0)"));
    }

    @Test
    void pageList_hasBalanceFalse_shouldUseUnifiedFormula() {
        com.blade.order.dto.OrderPageDTO dto = new com.blade.order.dto.OrderPageDTO();
        dto.setCurrent(1L);
        dto.setSize(20L);
        dto.setHasBalance(false);

        com.baomidou.mybatisplus.core.metadata.IPage<Order> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        mockPage.setRecords(java.util.List.of());
        mockPage.setTotal(0);

        when(orderMapper.selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any()))
                .thenReturn(mockPage);

        orderService.pageList(dto);

        ArgumentCaptor<Wrapper<Order>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("COALESCE(paid_amount, 0) >= GREATEST"));
        assertTrue(sql.contains("COALESCE(refund_amount, 0)"));
        assertTrue(sql.contains("COALESCE(write_off_amount, 0)"));
    }

    // ── Helper: invoke private convertToVO via getById ───────────────────

    private OrderVO callConvertToVO(Order order) {
        when(orderMapper.selectById(order.getId())).thenReturn(order);
        return orderService.getById(order.getId());
    }
}
