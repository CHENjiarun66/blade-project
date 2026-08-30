package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.system.user.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 短款核销结清、统一金额快照与冲销守卫——针对统一动作服务与真实快照服务的
 * 金额不变量单测（原 OrderServiceImpl 写回测试迁移至新架构，语义保持）。
 * 流水存储用内存列表模拟，快照公式按真实实现复算。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplWriteOffTest {

    private static final Long TENANT_ID = 1L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderFinancialRecordMapper financialRecordMapper;
    @Mock private OrderStateTransitionLogMapper transitionLogMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private InventoryService inventoryService;
    @Mock private com.blade.order.service.OrderPlaceholderSplitService placeholderSplitService;
    @Mock private com.blade.customer.service.CustomerStatsCacheService customerStatsCacheService;

    private OrderActionService actionService;
    private OrderFinanceSnapshotService snapshotService;

    /** 内存流水存储，真实快照服务从中聚合 */
    private List<OrderFinancialRecord> records;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderFinancialRecord.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        lenient().when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        User principal = new User();
        principal.setId(9L);
        principal.setNickname("测试员");
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        lenient().when(authentication.getAuthorities()).thenReturn((java.util.Collection) java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:viewAll"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:recordPayment"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:writeOff"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:refund"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:reverse"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:chooseFulfillment"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:allocate"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:deliver"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:cancel"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("btn:order:view")));
        SecurityContextHolder.setContext(securityContext);

        records = new ArrayList<>();
        lenient().when(financialRecordMapper.insert(any(OrderFinancialRecord.class)))
                .thenAnswer(inv -> {
                    records.add(inv.getArgument(0));
                    return 1;
                });
        lenient().when(financialRecordMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<>(records));

        snapshotService = new OrderFinanceSnapshotService(orderMapper, financialRecordMapper,
                new OrderCompatAdapter());
        actionService = new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper, snapshotService, new OrderCompatAdapter(),
                inventoryService, placeholderSplitService, customerStatsCacheService,
                new com.blade.order.service.OrderAccessPolicy(mock(com.blade.system.user.mapper.UserMapper.class)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** 已迁移订单 + 一笔期初收款，快照字段与流水一致。 */
    private Order migratedOrder(Long id, BigDecimal total, BigDecimal initialReceipt) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(0);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setFulfillmentMode("UNDECIDED");
        order.setTotalAmount(total);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setVersion(0);
        OrderFinancialRecord opening = new OrderFinancialRecord();
        opening.setId(1000L + id);
        opening.setTenantId(TENANT_ID);
        opening.setOrderId(id);
        opening.setRecordType(FinancialRecordType.RECEIPT.name());
        opening.setAmount(initialReceipt);
        opening.setOccurredAt(java.time.LocalDateTime.now());
        opening.setDeleted(0);
        records.add(opening);
        snapshotService.recalculate(order);
        return order;
    }

    /** 历史未迁移行：只有旧字段。 */
    private Order legacyOrder(Long id, int legacyStatus, BigDecimal paid) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(legacyStatus);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setPaidAmount(paid);
        order.setPaymentStatus(paid.signum() > 0 ? 1 : 0);
        return order;
    }

    private AddPaymentDTO dto(BigDecimal amount, Boolean markAsSettled, String reason) {
        AddPaymentDTO d = new AddPaymentDTO();
        d.setAdditionalAmount(amount);
        d.setMarkAsSettled(markAsSettled);
        d.setWriteOffReason(reason);
        return d;
    }

    // ── 正常收款 ─────────────────────────────────────────────────────

    @Test
    void addPaymentDto_shouldAcceptPositiveAmountAndAccumulate() {
        Order order = migratedOrder(1L, new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(orderMapper.selectByIdForUpdate(1L, TENANT_ID)).thenReturn(order);

        actionService.addPaymentCompat(1L, dto(new BigDecimal("20.00"), false, null));

        assertEquals(0, new BigDecimal("50.00").compareTo(order.getPaidAmount()));
        assertEquals(CollectionStatus.PARTIAL.name(), order.getCollectionStatus());
        assertEquals(1, order.getPaymentStatus());
        verifyNoInventoryTouch();
    }

    @Test
    void addPaymentDto_shouldRejectZeroAmountWithoutMarkSettled() {
        Order order = migratedOrder(2L, new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(orderMapper.selectByIdForUpdate(2L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                actionService.addPaymentCompat(2L, dto(BigDecimal.ZERO, false, null)));
    }

    @Test
    void addPaymentDto_shouldRejectOverBalance() {
        Order order = migratedOrder(3L, new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(orderMapper.selectByIdForUpdate(3L, TENANT_ID)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                actionService.addPaymentCompat(3L, dto(new BigDecimal("80.00"), false, null)));
        assertTrue(ex.getMessage().contains("70"));
    }

    @Test
    void addPayment_fullReceipt_reachesSettled() {
        Order order = migratedOrder(31L, new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(orderMapper.selectByIdForUpdate(31L, TENANT_ID)).thenReturn(order);

        actionService.recordPayment(31L, new BigDecimal("70.00"), null, null, "PC");

        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());
        assertEquals(2, order.getPaymentStatus());
        assertEquals(0, order.getBalanceAmount().compareTo(BigDecimal.ZERO));
        assertEquals("FULL_RECEIPT", order.getSettlementMethod());
        assertNotNull(order.getSettledAt());
    }

    // ── 历史未迁移行（旧公式余额兜底） ──────────────────────────────

    @Test
    void addPayment_legacyRow_rejectedUntilMigrated() {
        // 终审 P0-4：历史未迁移行不得参与任何新财务动作（含期初固化），必须先走迁移工具
        Order order = legacyOrder(33L, 1, new BigDecimal("90.00"));
        when(orderMapper.selectByIdForUpdate(33L, TENANT_ID)).thenReturn(order);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                actionService.addPaymentCompat(33L, dto(new BigDecimal("20.00"), false, null)));
        assertTrue(ex.getMessage().contains("迁移"));
        assertTrue(records.isEmpty(), "历史行不得产生任何流水");
    }

    @Test
    void refundPayment_legacyRow_rejected() {
        Order order = legacyOrder(34L, 1, new BigDecimal("10.00"));
        when(orderMapper.selectByIdForUpdate(34L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                actionService.refundPayment(34L, new BigDecimal("5.00"), "退款", null, "PC"));
        assertTrue(records.isEmpty(), "历史行不得产生任何流水");
    }

    // ── 标记结清（短款核销） ────────────────────────────────────────

    @Test
    void markAsSettled_withZeroPayment_shouldWriteOffRemainingBalance() {
        Order order = migratedOrder(5L, new BigDecimal("100.00"), new BigDecimal("80.00"));
        when(orderMapper.selectByIdForUpdate(5L, TENANT_ID)).thenReturn(order);

        actionService.addPaymentCompat(5L, dto(BigDecimal.ZERO, true, "客户少付20元"));

        assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("80.00"))); // 实收不变
        assertEquals(0, order.getWriteOffAmount().compareTo(new BigDecimal("20.00")));
        assertEquals("客户少付20元", order.getWriteOffReason());
        assertEquals(2, order.getPaymentStatus());
        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());
        assertEquals("WRITE_OFF", order.getSettlementMethod());
        verifyNoInventoryTouch();
    }

    @Test
    void markAsSettled_withPositivePayment_shouldAccumulatePaymentAndWriteOff() {
        Order order = migratedOrder(6L, new BigDecimal("100.00"), new BigDecimal("70.00"));
        when(orderMapper.selectByIdForUpdate(6L, TENANT_ID)).thenReturn(order);

        actionService.addPaymentCompat(6L, dto(new BigDecimal("10.00"), true, "尾款抹零"));

        assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("80.00")));
        assertEquals(0, order.getWriteOffAmount().compareTo(new BigDecimal("20.00")));
        assertEquals("尾款抹零", order.getWriteOffReason());
        assertEquals(2, order.getPaymentStatus());
    }

    @Test
    void markAsSettled_settlesExactlyRemainingAndRejectsRepeat() {
        Order order = migratedOrder(7L, new BigDecimal("100.00"), new BigDecimal("85.00"));
        when(orderMapper.selectByIdForUpdate(7L, TENANT_ID)).thenReturn(order);
        // 尾款 15 → 一次核销全部剩余，结清
        actionService.settleWithWriteOff(7L, BigDecimal.ZERO, "尾款核销", null, "PC");
        assertEquals(0, order.getBalanceAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, order.getWriteOffAmount().compareTo(new BigDecimal("15.00")));
        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());

        // 第二次核销拒绝
        assertThrows(RuntimeException.class, () ->
                actionService.settleWithWriteOff(7L, BigDecimal.ZERO, "重复核销", null, "PC"));
    }

    @Test
    void markAsSettled_requiresReason() {
        Order order = migratedOrder(71L, new BigDecimal("100.00"), new BigDecimal("80.00"));
        when(orderMapper.selectByIdForUpdate(71L, TENANT_ID)).thenReturn(order);

        AddPaymentDTO d = dto(BigDecimal.ZERO, true, " ");
        assertThrows(RuntimeException.class, () -> actionService.addPaymentCompat(71L, d));
    }

    @Test
    void markAsSettled_rejectsZeroReceiptOrder() {
        Order order = migratedOrder(72L, new BigDecimal("100.00"), BigDecimal.ZERO);
        when(orderMapper.selectByIdForUpdate(72L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                actionService.settleWithWriteOff(72L, BigDecimal.ZERO, "整单核销", null, "PC"));
    }

    @Test
    void alreadySettledOrder_rejectsMorePayment() {
        Order order = migratedOrder(8L, new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(orderMapper.selectByIdForUpdate(8L, TENANT_ID)).thenReturn(order);
        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());

        assertThrows(RuntimeException.class, () ->
                actionService.addPaymentCompat(8L, dto(new BigDecimal("10.00"), false, null)));
    }

    // ── 现金退款与冲销 ──────────────────────────────────────────────

    @Test
    void refundPayment_shouldReduceNetReceivedAndReopenBalance() {
        Order order = migratedOrder(9L, new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(orderMapper.selectByIdForUpdate(9L, TENANT_ID)).thenReturn(order);
        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());

        actionService.refundPayment(9L, new BigDecimal("30.00"), "少件退款", null, "PC");

        assertEquals(0, order.getCashRefundAmount().compareTo(new BigDecimal("30.00")));
        assertEquals(0, order.getNetReceivedAmount().compareTo(new BigDecimal("70.00")));
        assertEquals(0, order.getBalanceAmount().compareTo(new BigDecimal("30.00")));
        assertEquals(CollectionStatus.PARTIAL.name(), order.getCollectionStatus());
        assertEquals(1, order.getPaymentStatus());
    }

    @Test
    void refundPayment_rejectsOverGrossReceived() {
        Order order = migratedOrder(91L, new BigDecimal("100.00"), new BigDecimal("50.00"));
        when(orderMapper.selectByIdForUpdate(91L, TENANT_ID)).thenReturn(order);

        assertThrows(RuntimeException.class, () ->
                actionService.refundPayment(91L, new BigDecimal("60.00"), "多退", null, "PC"));
    }

    @Test
    void reverseFinancialRecord_shouldAppendReversalOnlyOnce() {
        Order order = migratedOrder(10L, new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(orderMapper.selectByIdForUpdate(10L, TENANT_ID)).thenReturn(order);
        OrderFinancialRecord target = records.get(0);
        when(financialRecordMapper.selectOne(any())).thenReturn(target);
        // 第一次无已有冲销，第二次已有冲销（数据库唯一键并发兜底由 schema/集成测试验证）
        when(financialRecordMapper.selectCount(any())).thenReturn(0L, 1L);

        actionService.reverseFinancialRecord(10L, target.getId(), "录错金额", null, "PC");

        // 被冲销的收款不再计入快照
        assertEquals(0, order.getGrossReceivedAmount().compareTo(BigDecimal.ZERO));
        assertEquals(CollectionStatus.UNPAID.name(), order.getCollectionStatus());

        // 第二次冲销同一流水：服务层守卫拒绝
        assertThrows(RuntimeException.class, () ->
                actionService.reverseFinancialRecord(10L, target.getId(), "重复冲销", null, "PC"));
    }

    @Test
    void reverseFinancialRecord_rejectsReversingReversal() {
        Order order = migratedOrder(11L, new BigDecimal("100.00"), new BigDecimal("50.00"));
        when(orderMapper.selectByIdForUpdate(11L, TENANT_ID)).thenReturn(order);
        OrderFinancialRecord reversal = new OrderFinancialRecord();
        reversal.setId(2000L);
        reversal.setRecordType(FinancialRecordType.REVERSAL.name());
        reversal.setOrderId(11L);
        reversal.setAmount(new BigDecimal("10.00"));
        when(financialRecordMapper.selectOne(any())).thenReturn(reversal);

        assertThrows(RuntimeException.class, () ->
                actionService.reverseFinancialRecord(11L, 2000L, "冲销冲销", null, "PC"));
    }

    // ── 幂等键 ──────────────────────────────────────────────────────

    @Test
    void recordPayment_withSameIdempotencyKey_replaysSilently() {
        Order order = migratedOrder(12L, new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(orderMapper.selectByIdForUpdate(12L, TENANT_ID)).thenReturn(order);
        when(financialRecordMapper.selectOne(any())).thenAnswer(inv -> records.stream()
                .filter(r -> "REQ-001".equals(r.getIdempotencyKey()))
                .findFirst().orElse(null));

        actionService.recordPayment(12L, new BigDecimal("10.00"), null, "REQ-001", "PC");
        int recordCountAfterFirst = records.size();

        actionService.recordPayment(12L, new BigDecimal("10.00"), null, "REQ-001", "PC");
        assertEquals(recordCountAfterFirst, records.size(), "幂等重放不得新增流水");
        assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("40.00")));
    }

    @Test
    void recordPayment_withForeignIdempotencyKey_rejected() {
        Order order = migratedOrder(13L, new BigDecimal("100.00"), new BigDecimal("30.00"));
        when(orderMapper.selectByIdForUpdate(13L, TENANT_ID)).thenReturn(order);
        OrderFinancialRecord foreign = new OrderFinancialRecord();
        foreign.setId(9999L);
        foreign.setOrderId(8888L);
        foreign.setIdempotencyKey("REQ-OTHER");
        when(financialRecordMapper.selectOne(any())).thenReturn(foreign);

        assertThrows(RuntimeException.class, () ->
                actionService.recordPayment(13L, new BigDecimal("10.00"), null, "REQ-OTHER", "PC"));
    }

    // ── 零金额订单人工结清 ──────────────────────────────────────────

    @Test
    void zeroAmountOrder_requiresManualConfirmationToSettle() {
        Order order = migratedOrder(14L, BigDecimal.ZERO, BigDecimal.ZERO);
        when(orderMapper.selectByIdForUpdate(14L, TENANT_ID)).thenReturn(order);
        assertEquals(CollectionStatus.UNPAID.name(), order.getCollectionStatus());

        // 任何重算都不得自动结清
        snapshotService.recalculateAndApply(order);
        assertEquals(CollectionStatus.UNPAID.name(), order.getCollectionStatus());

        snapshotService.markZeroAmountSettled(order, 9L, "测试员");
        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());
    }

    // ── 事务回滚契约：金额校验失败不得写库 ──────────────────────────

    @Test
    void failedValidation_mustNotPersistAnything() {
        Order order = migratedOrder(15L, new BigDecimal("100.00"), new BigDecimal("90.00"));
        when(orderMapper.selectByIdForUpdate(15L, TENANT_ID)).thenReturn(order);
        int recordsBefore = records.size();

        assertThrows(RuntimeException.class, () ->
                actionService.addPaymentCompat(15L, dto(new BigDecimal("50.00"), false, null)));

        assertEquals(recordsBefore, records.size(), "校验失败不得插入流水");
        assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("90.00")));
    }

    private void verifyNoInventoryTouch() {
        org.mockito.Mockito.verifyNoInteractions(inventoryService);
    }
}
