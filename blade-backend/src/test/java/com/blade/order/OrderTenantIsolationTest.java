package com.blade.order;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.service.InventoryService;
import com.blade.order.entity.Order;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 租户与空租户隔离测试：新订单动作服务遇空租户显式拒绝（18.2-8），
 * 跨租户读写由租户过滤的行锁查询承接。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderTenantIsolationTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderFinancialRecordMapper financialRecordMapper;
    @Mock private OrderStateTransitionLogMapper transitionLogMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private InventoryService inventoryService;
    @Mock private com.blade.order.service.OrderPlaceholderSplitService placeholderSplitService;
    @Mock private com.blade.customer.service.CustomerStatsCacheService customerStatsCacheService;

    private OrderActionService actionService;

    @BeforeEach
    void setUp() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        actionService = new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper,
                new OrderFinanceSnapshotService(orderMapper, financialRecordMapper, new OrderCompatAdapter()),
                new OrderCompatAdapter(), inventoryService, placeholderSplitService, customerStatsCacheService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void actions_rejectWhenTenantContextMissing() {
        TenantContext.clear();

        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> actionService.recordPayment(1L, BigDecimal.ONE, null, null, "PC"));
        assertEquals(403, ex1.getCode());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> actionService.cancelOrder(1L, "取消", "PC"));
        assertEquals(403, ex2.getCode());

        BusinessException ex3 = assertThrows(BusinessException.class,
                () -> actionService.chooseFulfillmentMode(1L, FulfillmentMode.RECORD_ONLY, "PC"));
        assertEquals(403, ex3.getCode());

        BusinessException ex4 = assertThrows(BusinessException.class, () -> {
            com.blade.order.dto.AddPaymentDTO d = new com.blade.order.dto.AddPaymentDTO();
            d.setAdditionalAmount(BigDecimal.ONE);
            actionService.addPaymentCompat(1L, d);
        });
        assertEquals(403, ex4.getCode());
    }

    @Test
    void crossTenantLookup_returnsNullAndRejectsAsNotFound() {
        TenantContext.setTenantId(2L);
        // 行锁查询带租户过滤：租户 2 查不到租户 1 的订单
        when(orderMapper.selectByIdForUpdate(1L, 2L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> actionService.recordPayment(1L, BigDecimal.ONE, null, null, "PC"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void sameTenantLookup_resolvesOrderAndAppliesActions() {
        TenantContext.setTenantId(1L);
        Order order = new Order();
        order.setId(1L);
        order.setTenantId(1L);
        order.setStatus(0);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());
        order.setCollectionStatus(CollectionStatus.UNPAID.name());
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setGrossReceivedAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(BigDecimal.ZERO);
        order.setBalanceAmount(new BigDecimal("100.00"));
        order.setVersion(0);
        when(orderMapper.selectByIdForUpdate(1L, 1L)).thenReturn(order);
        java.util.List<com.blade.order.entity.OrderFinancialRecord> records = new ArrayList<>();
        when(financialRecordMapper.insert(any(com.blade.order.entity.OrderFinancialRecord.class)))
                .thenAnswer(inv -> {
                    records.add(inv.getArgument(0));
                    return 1;
                });
        when(financialRecordMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<>(records));
        lenient().when(financialRecordMapper.selectOne(any())).thenReturn(null);

        actionService.recordPayment(1L, new BigDecimal("100.00"), null, null, "PC");

        assertEquals(CollectionStatus.SETTLED.name(), order.getCollectionStatus());
    }
}
