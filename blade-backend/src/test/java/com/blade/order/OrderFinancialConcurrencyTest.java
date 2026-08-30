package com.blade.order;

import com.blade.common.exception.BusinessException;
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
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发财务事实测试（真实隔离库）：
 * - 同一流水并发双冲销只能成功一次（uk_ofr_reversal 数据库唯一键兜底）；
 * - 并发收款总额不超过尾款。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderFinancialConcurrencyTest {

    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderFinancialRecordMapper financialRecordMapper;
    @Autowired private OrderActionService actionService;

    private Order seedOrder(String suffix, BigDecimal total, BigDecimal receipt) {
        Order order = new Order();
        order.setOrderNo("ORDCNC" + suffix + System.currentTimeMillis());
        order.setOrderDate(java.time.LocalDate.now());
        order.setOrderType("SPOT");
        order.setCustomerName("并发测试客户" + suffix);
        order.setTotalAmount(total);
        order.setOriginalAmount(total);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setPaymentStatus(0);
        order.setDepositAmount(BigDecimal.ZERO);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setFreightCost(BigDecimal.ZERO);
        order.setTotalCostAmount(BigDecimal.ZERO);
        order.setGrossProfit(total);
        order.setNeedDelivery(0);
        order.setIsDelivered(0);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setCollectionStatus(CollectionStatus.UNPAID.name());
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setGrossReceivedAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setBalanceAmount(total);
        order.setVersion(0);
        order.setDeleted(0);
        orderMapper.insert(order);

        if (receipt.compareTo(BigDecimal.ZERO) > 0) {
            OrderFinancialRecord r = new OrderFinancialRecord();
            r.setTenantId(order.getTenantId());
            r.setOrderId(order.getId());
            r.setRecordType(FinancialRecordType.RECEIPT.name());
            r.setAmount(receipt);
            r.setOccurredAt(LocalDateTime.now());
            r.setSource("PC");
            r.setDeleted(0);
            financialRecordMapper.insert(r);
        }
        return order;
    }

    private void bindContext() {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(1L);
        principal.setUsername("admin");
        Authentication authentication = new org.springframework.security.authentication.TestingAuthenticationToken(
                principal, null, java.util.List.of());
        SecurityContextHolder.setContext(
                new org.springframework.security.core.context.SecurityContextImpl(authentication));
    }

    @Test
    void concurrentDoubleReversal_onlyOneSucceeds() throws Exception {
        bindContext();
        Order order = seedOrder("REV", new BigDecimal("100.00"), BigDecimal.ZERO);
        OrderFinancialRecord target = new OrderFinancialRecord();
        target.setTenantId(order.getTenantId());
        target.setOrderId(order.getId());
        target.setRecordType(FinancialRecordType.RECEIPT.name());
        target.setAmount(new BigDecimal("100.00"));
        target.setOccurredAt(LocalDateTime.now());
        target.setSource("PC");
        target.setDeleted(0);
        financialRecordMapper.insert(target);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable task = () -> {
            try {
                bindContext();
                start.await();
                actionService.reverseFinancialRecord(order.getId(), target.getId(), "并发冲销测试", null, "TEST");
                success.incrementAndGet();
            } catch (BusinessException | IllegalStateException e) {
                rejected.incrementAndGet();
            } catch (Exception e) {
                // 唯一键冲突包装异常也算拒绝
                rejected.incrementAndGet();
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        };
        pool.submit(task);
        pool.submit(task);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发冲销未在时限内完成");

        assertEquals(1, success.get(), "同一流水并发双冲销必须只能成功一次");
        assertEquals(1, rejected.get());
        List<OrderFinancialRecord> reversals = financialRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderFinancialRecord>()
                        .eq(OrderFinancialRecord::getOrderId, order.getId())
                        .eq(OrderFinancialRecord::getRecordType, FinancialRecordType.REVERSAL.name()));
        assertEquals(1, reversals.size(), "冲销记录必须恰好一条");
    }

    @Test
    void concurrentPayments_neverExceedBalance() throws Exception {
        bindContext();
        // 尾款 100：两笔 80 并发，最多一笔成功
        Order order = seedOrder("PAY", new BigDecimal("100.00"), BigDecimal.ZERO);
        order.setBalanceAmount(new BigDecimal("100.00"));
        orderMapper.updateById(order);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable task = () -> {
            try {
                bindContext();
                start.await();
                actionService.recordPayment(order.getId(), new BigDecimal("80.00"), null, null, "TEST");
                success.incrementAndGet();
            } catch (BusinessException | IllegalStateException e) {
                rejected.incrementAndGet();
            } catch (Exception e) {
                rejected.incrementAndGet();
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        };
        pool.submit(task);
        pool.submit(task);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发收款未在时限内完成");

        Order after = orderMapper.selectById(order.getId());
        assertTrue(after.getGrossReceivedAmount().compareTo(new BigDecimal("100.00")) <= 0,
                "累计实收不得超过应收净额");
        assertTrue(success.get() >= 1, "至少一笔收款成功");
        assertEquals(2, success.get() + rejected.get());
    }
}
