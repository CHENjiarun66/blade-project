package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 终审三轮 P0-4：零金额人工结清的真实数据库乐观锁行为。
 * 使用 @Transactional + 真隔离库，@Version 由 MyBatis-Plus 拦截器自动处理，
 * 无 mock（终审批评 mock updateById=1 掩盖了真实行为）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderZeroAmountLockTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderFinanceSnapshotService snapshotService;

    private void bindAdmin() {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(1L);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("btn:order:viewAll")))));
    }

    private Long seedZeroAmountOrder() {
        String orderNo = "ORDZ0" + System.nanoTime();
        jdbc.update("""
                INSERT INTO sale_order (order_no, order_date, order_type, customer_name, total_amount, original_amount,
                  total_cost_amount, gross_profit, freight_amount, freight_cost, paid_amount, payment_status,
                  deposit_amount, write_off_amount, refund_amount, sales_return_amount, gross_received_amount,
                  cash_refund_amount, net_received_amount, balance_amount, need_delivery, is_delivered,
                  fulfillment_status, fulfillment_mode, collection_status, version, deleted)
                VALUES (?, CURDATE(), 'SPOT', '零金额测试', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        'CONFIRMED', 'UNDECIDED', 'UNPAID', 0, 0)
                """, orderNo);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no = ?", Long.class, orderNo);
    }

    @Test
    void markZeroAmountSettled_succeedsWithRealOptimisticLock() {
        bindAdmin();
        Long orderId = seedZeroAmountOrder();
        Order order = new Order();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setCollectionStatus(CollectionStatus.UNPAID.name());
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());
        order.setVersion(0);

        // 真实 @Version 拦截器：手工递增版本会导致 409 恒定冲突（终审三轮 P0-4 反例）
        assertDoesNotThrow(() -> snapshotService.markZeroAmountSettled(order, 1L, "测试"));

        String collection = jdbc.queryForObject(
                "SELECT collection_status FROM sale_order WHERE id = ?", String.class, orderId);
        assertEquals("SETTLED", collection);
        Integer version = jdbc.queryForObject("SELECT version FROM sale_order WHERE id = ?", Integer.class, orderId);
        assertEquals(1, version, "@Version 应自增一次");
    }
}
