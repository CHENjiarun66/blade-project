package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.customer.service.impl.CustomerServiceImpl;
import com.blade.customer.dto.CustomerStatsVO;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.dto.DashboardStatsDTO;
import com.blade.dashboard.service.DashboardService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderFactsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一订单事实一致性验证（系列 E，真实隔离库）。
 * 用同一组样本（取消、未收款、部分收款、足额结清、短款结清、RECORD_ONLY、已出库、历史退货）
 * 断言事实服务、仪表盘、客户统计和 WhatsApp 订单事实在同一筛选范围下口径一致。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderFactConsistencyTest {

    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderFinancialRecordMapper recordMapper;
    @Autowired private OrderFactsService orderFactsService;
    @Autowired private DashboardService dashboardService;
    @Autowired private CustomerServiceImpl customerService;
    @Autowired private JdbcTemplate jdbc;

    private Order seed(String suffix, String fulfillment, Integer legacyStatus, String collection,
                       String total, String gross, String writeOff, String mode, Long customerId) {
        Order o = new Order();
        o.setOrderNo("ORDFCT" + suffix + System.nanoTime());
        o.setOrderDate(LocalDate.now());
        o.setOrderType("SPOT");
        o.setCustomerName("一致性测试" + suffix);
        o.setCustomerId(customerId);
        o.setTotalAmount(new BigDecimal(total));
        o.setOriginalAmount(new BigDecimal(total));
        o.setTotalCostAmount(BigDecimal.ZERO);
        o.setGrossProfit(new BigDecimal(total));
        o.setFreightAmount(BigDecimal.ZERO);
        o.setFreightCost(BigDecimal.ZERO);
        o.setPaidAmount(BigDecimal.ZERO);
        o.setPaymentStatus(0);
        o.setDepositAmount(BigDecimal.ZERO);
        o.setWriteOffAmount(new BigDecimal(writeOff));
        o.setRefundAmount(BigDecimal.ZERO);
        o.setSalesReturnAmount(BigDecimal.ZERO);
        o.setGrossReceivedAmount(new BigDecimal(gross));
        o.setCashRefundAmount(BigDecimal.ZERO);
        o.setNetReceivedAmount(new BigDecimal(gross));
        o.setBalanceAmount(new BigDecimal(total).subtract(new BigDecimal(writeOff)).subtract(new BigDecimal(gross)).max(BigDecimal.ZERO));
        o.setNeedDelivery(0);
        o.setIsDelivered(0);
        o.setDeleted(0);
        o.setVersion(0);
        o.setFulfillmentStatus(fulfillment);
        o.setCollectionStatus(collection);
        o.setFulfillmentMode(mode);
        if (legacyStatus != null) {
            // 模拟历史未迁移行
            o.setFulfillmentStatus(null);
            o.setCollectionStatus(null);
            o.setFulfillmentMode(null);
            o.setStatus(legacyStatus);
        }
        orderMapper.insert(o);
        return o;
    }

    @Test
    void consumers_agreeOnSameFacts_withinSameFilterRange() {
        TenantContext.setTenantId(1L);
        try {
            Long customerId = 1L;
            // 相对断言：隔离库可重复执行，以测试前基线为参照
            int businessBefore = orderFactsService.customerBusinessOrders(1L, customerId).size();
            Order cancelled = seed("CXL", FulfillmentStatus.CANCELLED.name(), null,
                    CollectionStatus.SETTLED.name(), "500.00", "500.00", "0.00",
                    FulfillmentMode.RECORD_ONLY.name(), customerId);
            Order unpaid = seed("UNP", FulfillmentStatus.CONFIRMED.name(), null,
                    CollectionStatus.UNPAID.name(), "100.00", "0.00", "0.00",
                    FulfillmentMode.UNDECIDED.name(), customerId);
            Order partial = seed("PRT", FulfillmentStatus.CONFIRMED.name(), null,
                    CollectionStatus.PARTIAL.name(), "100.00", "40.00", "0.00",
                    FulfillmentMode.UNDECIDED.name(), customerId);
            Order settledFull = seed("FUL", FulfillmentStatus.CONFIRMED.name(), null,
                    CollectionStatus.SETTLED.name(), "100.00", "100.00", "0.00",
                    FulfillmentMode.UNDECIDED.name(), customerId);
            Order settledWriteOff = seed("WOF", FulfillmentStatus.CONFIRMED.name(), null,
                    CollectionStatus.SETTLED.name(), "100.00", "90.00", "10.00",
                    FulfillmentMode.UNDECIDED.name(), customerId);
            Order recordOnly = seed("RCD", FulfillmentStatus.COMPLETED.name(), null,
                    CollectionStatus.SETTLED.name(), "100.00", "100.00", "0.00",
                    FulfillmentMode.RECORD_ONLY.name(), customerId);
            Order shipped = seed("SHP", FulfillmentStatus.SHIPPED.name(), null,
                    CollectionStatus.SETTLED.name(), "100.00", "100.00", "0.00",
                    FulfillmentMode.STOCK_LINKED.name(), customerId);
            Order legacyReturned = seed("RTN", null, 8, null, "100.00", "0.00", "0.00", null, customerId);
            legacyReturned.setFulfillmentMode(null);

            // ── 事实服务口径 ──
            List<Order> business = orderFactsService.customerBusinessOrders(1L, customerId);
            // 取消 + 历史已退货(8) 都不属于经营订单
            assertTrue(business.stream().noneMatch(o -> o.getId().equals(cancelled.getId())));
            assertTrue(business.stream().noneMatch(o -> o.getId().equals(legacyReturned.getId())));
            assertEquals(businessBefore + 6, business.size());

            // RECORD_ONLY 计入销售并视为已完成；库存周转由消费者按 fulfillment_mode=RECORD_ONLY 排除
            assertTrue(orderFactsService.isFulfilled(recordOnly));
            assertTrue(orderFactsService.isShippedOrBeyond(shipped));
            assertTrue(orderFactsService.isShippedOrBeyond(recordOnly));
            assertEquals("RECORD_ONLY", recordOnly.getFulfillmentMode());

            // 净销售额公式：adjusted - write_off
            assertEquals(0, orderFactsService.netSalesAmount(settledWriteOff)
                    .compareTo(new BigDecimal("90.00")));

            // ── 仪表盘与事实服务一致（同一日期范围） ──
            DashboardQueryDTO query = new DashboardQueryDTO();
            query.setPeriodType(com.blade.dashboard.enums.PeriodType.CUSTOM);
            query.setStartDate(LocalDate.now());
            query.setEndDate(LocalDate.now());
            DashboardStatsDTO stats = dashboardService.getStats(query);
            List<Order> factsPaid = orderFactsService.paidBusinessOrdersByOrderDate(1L, LocalDate.now(), LocalDate.now());
            assertEquals(factsPaid.size(), stats.getPeriodOrders(),
                    "仪表盘订单数必须与统一事实服务一致");
            assertTrue(stats.getPeriodOrders() >= 6, "本次种子至少贡献 6 笔已收款经营订单");

            // ── 客户统计与事实服务一致（取消订单不进消费额） ──
            CustomerStatsVO customerStats = customerService.getStats(customerId);
            BigDecimal expectedSpending = business.stream()
                    .map(orderFactsService::gross)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, customerStats.getTotalSpending().compareTo(expectedSpending),
                    "客户消费额必须等于经营订单累计实收之和");

            // ── WhatsApp 订单事实 SQL 与事实服务一致（排除取消） ──
            Long waCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sale_order o
                    WHERE o.tenant_id=? AND o.customer_id=? AND o.deleted=0
                      AND (o.fulfillment_status IS NOT NULL AND o.fulfillment_status<>'CANCELLED'
                           OR (o.fulfillment_status IS NULL AND (o.status IS NULL OR o.status NOT IN (6,8))))
                    """, Long.class, 1L, customerId);
            assertEquals(business.size(), waCount.intValue(),
                    "WhatsApp 订单事实必须使用统一经营订单口径");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void cashFlow_usesFinancialRecordTime_notOrderTime() {
        TenantContext.setTenantId(1L);
        try {
            Order order = seed("CASH", FulfillmentStatus.CONFIRMED.name(), null,
                    CollectionStatus.PARTIAL.name(), "100.00", "30.00", "0.00",
                    FulfillmentMode.UNDECIDED.name(), 1L);
            OrderFinancialRecord receipt = new OrderFinancialRecord();
            receipt.setTenantId(1L);
            receipt.setOrderId(order.getId());
            receipt.setRecordType(FinancialRecordType.RECEIPT.name());
            receipt.setAmount(new BigDecimal("30.00"));
            receipt.setOccurredAt(LocalDateTime.now().minusDays(3));
            receipt.setSource("PC");
            receipt.setDeleted(0);
            recordMapper.insert(receipt);

            BigDecimal cashThreeDaysAgo = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount),0) FROM order_financial_record
                    WHERE tenant_id=1 AND record_type IN ('RECEIPT','MIGRATION_OPENING')
                      AND occurred_at >= DATE_SUB(NOW(3), INTERVAL 4 DAY)
                      AND occurred_at <  DATE_SUB(NOW(3), INTERVAL 2 DAY)
                    """, BigDecimal.class);
            assertTrue(cashThreeDaysAgo.compareTo(new BigDecimal("30.00")) >= 0,
                    "现金流必须按流水业务时间统计，而不是订单日期");
        } finally {
            TenantContext.clear();
        }
    }
}
