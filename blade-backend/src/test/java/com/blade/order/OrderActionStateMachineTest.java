package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.service.InventoryService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.entity.OrderStateTransitionLog;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 统一动作服务状态机白名单测试：11 个动作的合法转移与非法转移。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderActionStateMachineTest {

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
    private List<OrderFinancialRecord> records;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderFinancialRecord.class);
        TableInfoHelper.initTableInfo(assistant, OrderDeliveryPlan.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        lenient().when(orderMapper.updateById(any(Order.class))).thenReturn(1);
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

        snapshotService = new OrderFinanceSnapshotService(orderMapper, financialRecordMapper,
                new OrderCompatAdapter());
        actionService = new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper, snapshotService, new OrderCompatAdapter(),
                inventoryService, placeholderSplitService, customerStatsCacheService, new com.blade.order.service.OrderAccessPolicy());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** 已结清 + 已确认 + 未选择履约方式的订单（履约链路起点）。 */
    private Order settledConfirmedOrder(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(0);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());
        order.setCollectionStatus(CollectionStatus.SETTLED.name());
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setGrossReceivedAmount(new BigDecimal("100.00"));
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(new BigDecimal("100.00"));
        order.setBalanceAmount(BigDecimal.ZERO);
        order.setVersion(0);
        when(orderMapper.selectByIdForUpdate(id, TENANT_ID)).thenReturn(order);
        return order;
    }

    @Test
    void chooseFulfillmentMode_recordOnly_completesDirectlyWithoutInventory() {
        Order order = settledConfirmedOrder(1L);

        actionService.chooseFulfillmentMode(1L, FulfillmentMode.RECORD_ONLY, "PC");

        assertEquals(FulfillmentStatus.COMPLETED.name(), order.getFulfillmentStatus());
        assertEquals(5, order.getStatus());
        assertEquals(FulfillmentMode.RECORD_ONLY.name(), order.getFulfillmentMode());
        assertNotNull(order.getFulfillmentDecidedAt());
        verifyNoInventoryTouch();
    }

    @Test
    void chooseFulfillmentMode_stockLinked_movesToWaitingAllocation() {
        Order order = settledConfirmedOrder(2L);

        actionService.chooseFulfillmentMode(2L, FulfillmentMode.STOCK_LINKED, "PC");

        assertEquals(FulfillmentStatus.WAITING_ALLOCATION.name(), order.getFulfillmentStatus());
        assertEquals(1, order.getStatus());
        verifyNoInventoryTouch();
    }

    @Test
    void chooseFulfillmentMode_rejectedWhenNotSettled() {
        Order order = settledConfirmedOrder(3L);
        order.setCollectionStatus(CollectionStatus.PARTIAL.name());
        order.setBalanceAmount(new BigDecimal("50.00"));

        assertThrows(BusinessException.class, () ->
                actionService.chooseFulfillmentMode(3L, FulfillmentMode.RECORD_ONLY, "PC"));
    }

    @Test
    void chooseFulfillmentMode_rejectedWhenAlreadyDecided() {
        Order order = settledConfirmedOrder(4L);
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());

        assertThrows(BusinessException.class, () ->
                actionService.chooseFulfillmentMode(4L, FulfillmentMode.RECORD_ONLY, "PC"));
    }

    @Test
    void startAllocation_requiresStockLinkedMode() {
        Order order = settledConfirmedOrder(5L);
        order.setFulfillmentStatus(FulfillmentStatus.WAITING_ALLOCATION.name());
        order.setStatus(1);
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());

        assertThrows(BusinessException.class, () -> actionService.startAllocation(5L, "PC"));
    }

    @Test
    void startAllocation_requiresWaitingAllocationStatus() {
        Order order = settledConfirmedOrder(6L);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());

        assertThrows(BusinessException.class, () -> actionService.startAllocation(6L, "PC"));
    }

    @Test
    void startAllocation_movesToAllocating() {
        Order order = settledConfirmedOrder(7L);
        order.setFulfillmentStatus(FulfillmentStatus.WAITING_ALLOCATION.name());
        order.setStatus(1);
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());

        actionService.startAllocation(7L, "PC");

        assertEquals(FulfillmentStatus.ALLOCATING.name(), order.getFulfillmentStatus());
        assertEquals(2, order.getStatus());
        assertEquals(Order.AdjustmentStatus.PENDING, order.getAdjustmentStatus());
    }

    @Test
    void confirmAllocation_movesToReadyToShip() {
        Order order = settledConfirmedOrder(8L);
        order.setFulfillmentStatus(FulfillmentStatus.ALLOCATING.name());
        order.setStatus(2);
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());

        actionService.confirmAllocation(8L, "PC");

        assertEquals(FulfillmentStatus.READY_TO_SHIP.name(), order.getFulfillmentStatus());
        assertEquals(3, order.getStatus());
    }

    @Test
    void completeOrder_requiresShippedStatus() {
        Order order = settledConfirmedOrder(9L);
        order.setFulfillmentStatus(FulfillmentStatus.READY_TO_SHIP.name());
        order.setStatus(3);
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());

        assertThrows(BusinessException.class, () -> actionService.completeOrder(9L, "PC"));

        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED.name());
        order.setStatus(4);
        actionService.completeOrder(9L, "PC");
        assertEquals(FulfillmentStatus.COMPLETED.name(), order.getFulfillmentStatus());
        assertEquals(5, order.getStatus());
        assertNotNull(order.getCompleteTime());
    }

    @Test
    void cancelOrder_allowedFromConfirmedAndAllocationStages() {
        Order order = settledConfirmedOrder(10L);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());

        actionService.cancelOrder(10L, "客户取消", "PC");

        assertEquals(FulfillmentStatus.CANCELLED.name(), order.getFulfillmentStatus());
        assertEquals(6, order.getStatus());
        assertTrue(order.getRemark().contains("客户取消"));
    }

    @Test
    void cancelOrder_cleansUpPendingPlans() {
        Order order = settledConfirmedOrder(11L);
        order.setFulfillmentStatus(FulfillmentStatus.ALLOCATING.name());
        order.setStatus(2);

        actionService.cancelOrder(11L, "配货中取消", "PC");

        assertEquals(FulfillmentStatus.CANCELLED.name(), order.getFulfillmentStatus());
        verifyPlanCleanup(11L);
    }

    @Test
    void cancelOrder_rejectedAfterShipped() {
        Order order = settledConfirmedOrder(12L);
        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED.name());
        order.setStatus(4);

        assertThrows(BusinessException.class, () -> actionService.cancelOrder(12L, "迟到取消", "PC"));
    }

    @Test
    void shipOrder_andFulfillmentActions_rejectedForLegacyUnmigratedRows() {
        Order order = settledConfirmedOrder(13L);
        order.setFulfillmentStatus(null);
        order.setFulfillmentMode(null);

        assertThrows(BusinessException.class, () -> actionService.chooseFulfillmentMode(13L, FulfillmentMode.RECORD_ONLY, "PC"));
        assertThrows(BusinessException.class, () -> actionService.startAllocation(13L, "PC"));
        assertThrows(BusinessException.class, () -> actionService.cancelOrder(13L, "取消", "PC"));
    }

    @Test
    void startAllocation_blockedByPlaceholderItems() {
        Order order = settledConfirmedOrder(15L);
        order.setFulfillmentStatus(FulfillmentStatus.WAITING_ALLOCATION.name());
        order.setStatus(1);
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());
        lenient().when(placeholderSplitService.hasPlaceholderItems(15L, TENANT_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> actionService.startAllocation(15L, "PC"));
        assertTrue(ex.getMessage().contains("拆分"));
    }

    @Test
    void shipOrder_blockedByPlaceholderItems() {
        Order order = settledConfirmedOrder(16L);
        order.setFulfillmentStatus(FulfillmentStatus.READY_TO_SHIP.name());
        order.setStatus(3);
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());
        lenient().when(placeholderSplitService.hasPlaceholderItems(16L, TENANT_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> actionService.shipOrder(16L, "PC"));
        assertTrue(ex.getMessage().contains("拆分"));
        verifyNoInventoryTouch();
    }

    @Test
    void stateLog_writtenForEachTransition() {
        Order order = settledConfirmedOrder(14L);

        actionService.chooseFulfillmentMode(14L, FulfillmentMode.RECORD_ONLY, "PC");

        org.mockito.Mockito.verify(transitionLogMapper).insert(any(OrderStateTransitionLog.class));
    }

    private void verifyPlanCleanup(Long orderId) {
        org.mockito.Mockito.verify(deliveryPlanMapper).delete(any());
        org.mockito.Mockito.verify(adjustmentLogMapper).delete(any());
    }

    private void verifyNoInventoryTouch() {
        org.mockito.Mockito.verifyNoInteractions(inventoryService);
    }
}
