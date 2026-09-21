package com.blade.order;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.service.OrderService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第二批B：改档口审计必须有可靠操作人（order_outlet_change_log.operator_id NOT NULL）。
 *
 * <p>真实 DB：无可靠 User principal 时 {@code applyOutletChange} 在任何写入前 fail closed（401），
 * 订单 source_outlet_id/source_shop 不变、审计行 0；可靠用户正常改档口并写正确 operator_id。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderOutletChangeOperatorIntegrationTest {

    private static final long TENANT = 1L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderService orderService;

    private Long outletA;
    private Long outletB;
    private String outletAName;
    private String outletBName;
    private Long orderId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        String suffix = Long.toString(System.nanoTime()).substring(8);
        outletAName = "B2B档口A" + suffix;
        outletBName = "B2B档口B" + suffix;
        outletA = outlet("B2B-OA-" + suffix, outletAName);
        outletB = outlet("B2B-OB-" + suffix, outletBName);
        orderId = order("B2B-ORD-" + suffix, outletA, outletAName);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void operatorIdColumnIsNotNull() {
        String nullable = jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_outlet_change_log' "
                + "AND COLUMN_NAME = 'operator_id'", String.class);
        assertEquals("NO", nullable, "order_outlet_change_log.operator_id 必须 NOT NULL");
    }

    @Test
    void noReliableUserFailsClosedWithoutAnyWrite() {
        authenticateAsGhost();

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.update(changeDto()));

        assertEquals(401, ex.getCode(), "无可靠用户必须以业务 401 fail closed");
        assertEquals(outletA, jdbc.queryForObject(
                "SELECT source_outlet_id FROM sale_order WHERE id=?", Long.class, orderId));
        assertEquals(outletAName, jdbc.queryForObject(
                "SELECT source_shop FROM sale_order WHERE id=?", String.class, orderId));
        assertEquals(0, count("SELECT COUNT(*) FROM order_outlet_change_log WHERE order_id=?", orderId),
                "失败不得写审计");
    }

    @Test
    void authorizedUserWritesCorrectOperatorId() {
        long operatorId = 9001L;
        User operator = new User();
        operator.setId(operatorId);
        operator.setUsername("b2b_operator");
        authenticate(operator);

        orderService.update(changeDto());

        assertEquals(outletB, jdbc.queryForObject(
                "SELECT source_outlet_id FROM sale_order WHERE id=?", Long.class, orderId));
        assertEquals(outletBName, jdbc.queryForObject(
                "SELECT source_shop FROM sale_order WHERE id=?", String.class, orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM order_outlet_change_log WHERE order_id=?", orderId));
        assertEquals(operatorId, jdbc.queryForObject(
                "SELECT operator_id FROM order_outlet_change_log WHERE order_id=?", Long.class, orderId));
    }

    // ==================== helpers ====================

    private OrderUpdateDTO changeDto() {
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setId(orderId);
        dto.setSourceOutletId(outletB);
        dto.setOutletChangeReason("第二批B审计测试");
        return dto;
    }

    private void authenticateAsGhost() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ghost_b2b_user_not_in_db", null, authorities()));
    }

    private void authenticate(User principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, authorities()));
    }

    private List<SimpleGrantedAuthority> authorities() {
        return List.of(
                new SimpleGrantedAuthority("data:outlet:all"),
                new SimpleGrantedAuthority("data:order:peopleAll"),
                new SimpleGrantedAuthority("btn:order:changeOutlet"));
    }

    private Long outlet(String code, String name) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                + "VALUES(?,?,?,'STORE',1,0,0,0)", TENANT, code, name);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?",
                Long.class, TENANT, code);
    }

    private Long order(String orderNo, Long sourceOutletId, String sourceShop) {
        jdbc.update("INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,"
                        + "payment_status,status,fulfillment_status,fulfillment_mode,collection_status,"
                        + "gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,"
                        + "balance_amount,source_shop,source_outlet_id,tenant_id,deleted,version) "
                        + "VALUES(?,CURDATE(),'SPOT','B2B客户',100,0,0,0,'CONFIRMED','UNDECIDED','UNPAID',"
                        + "0,0,0,0,100,?,?,?,0,0)",
                orderNo, sourceShop, sourceOutletId, TENANT);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, orderNo);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
