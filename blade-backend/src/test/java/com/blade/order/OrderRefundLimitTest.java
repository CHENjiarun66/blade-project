package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 终审 P0-3 反例：累计现金退款不得超过"有效累计实收 − 有效累计现金退款"。
 * 行锁串行化 + 额度校验；退款冲销后额度自动恢复。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderRefundLimitTest {

    private static final Long TENANT_ID = 1L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderFinancialRecordMapper financialRecordMapper;
    @Mock private OrderStateTransitionLogMapper transitionLogMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private InventoryService inventoryService;

    private OrderActionService actionService;
    private OrderFinanceSnapshotService snapshotService;
    private List<OrderFinancialRecord> records;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        User principal = new User();
        principal.setId(9L);
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
                .thenAnswer(inv -> { records.add(inv.getArgument(0)); return 1; });
        lenient().when(financialRecordMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<>(records));
        lenient().when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        snapshotService = new OrderFinanceSnapshotService(orderMapper, financialRecordMapper,
                new OrderCompatAdapter());
        actionService = new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper, snapshotService, new OrderCompatAdapter(),
                inventoryService, mock(com.blade.order.service.OrderPlaceholderSplitService.class),
                mock(com.blade.customer.service.CustomerStatsCacheService.class),
                new com.blade.order.service.OrderAccessPolicy());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order settledOrder(Long id, BigDecimal gross) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(0);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());
        order.setCollectionStatus(CollectionStatus.SETTLED.name());
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setGrossReceivedAmount(gross);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(gross);
        order.setBalanceAmount(BigDecimal.ZERO);
        order.setVersion(0);
        when(orderMapper.selectByIdForUpdate(id, TENANT_ID)).thenReturn(order);
        return order;
    }

    @Test
    void cumulativeRefunds_neverExceedGrossReceived() {
        Order order = settledOrder(1L, new BigDecimal("100.00"));
        // 一笔已存在的收款流水支撑快照
        OrderFinancialRecord receipt = new OrderFinancialRecord();
        receipt.setId(100L);
        receipt.setRecordType(FinancialRecordType.RECEIPT.name());
        receipt.setAmount(new BigDecimal("100.00"));
        receipt.setOrderId(1L);
        receipt.setTenantId(TENANT_ID);
        records.add(receipt);

        // 第一笔退款 80：允许
        actionService.refundPayment(1L, new BigDecimal("80.00"), "第一次退款", null, "PC");
        assertEquals(0, order.getCashRefundAmount().compareTo(new BigDecimal("80.00")));

        // 第二笔退款 80：剩余额度只有 20，必须拒绝（终审 P0-3 反例）
        com.blade.common.exception.BusinessException ex = assertThrows(
                com.blade.common.exception.BusinessException.class,
                () -> actionService.refundPayment(1L, new BigDecimal("80.00"), "超额退款", null, "PC"));
        assertTrue(ex.getMessage().contains("20"));

        // 退剩余 20：允许，净实收归 0
        actionService.refundPayment(1L, new BigDecimal("20.00"), "退余款", null, "PC");
        assertEquals(0, order.getNetReceivedAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, records.stream()
                .filter(r -> FinancialRecordType.REFUND.name().equals(r.getRecordType()))
                .map(OrderFinancialRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("100.00")));
    }

    @Test
    void reversingRefund_restoresRefundableQuota() {
        Order order = settledOrder(2L, new BigDecimal("100.00"));
        OrderFinancialRecord receipt = new OrderFinancialRecord();
        receipt.setId(200L);
        receipt.setRecordType(FinancialRecordType.RECEIPT.name());
        receipt.setAmount(new BigDecimal("100.00"));
        receipt.setOrderId(2L);
        receipt.setTenantId(TENANT_ID);
        records.add(receipt);

        actionService.refundPayment(2L, new BigDecimal("100.00"), "全退", null, "PC");
        // 额度耗尽
        assertThrows(com.blade.common.exception.BusinessException.class,
                () -> actionService.refundPayment(2L, new BigDecimal("1.00"), "再退", null, "PC"));

        // 冲销那笔退款 → 额度恢复
        OrderFinancialRecord refundRecord = records.stream()
                .filter(r -> FinancialRecordType.REFUND.name().equals(r.getRecordType()))
                .findFirst().orElseThrow();
        refundRecord.setId(300L);
        when(financialRecordMapper.selectOne(any())).thenReturn(refundRecord);
        when(financialRecordMapper.selectCount(any())).thenReturn(0L, 1L);

        actionService.reverseFinancialRecord(2L, refundRecord.getId(), "退款录错", null, "PC");
        assertEquals(0, order.getCashRefundAmount().compareTo(BigDecimal.ZERO));

        // 恢复后可再退
        actionService.refundPayment(2L, new BigDecimal("50.00"), "重新退款", null, "PC");
        assertEquals(0, order.getCashRefundAmount().compareTo(new BigDecimal("50.00")));
    }
}
