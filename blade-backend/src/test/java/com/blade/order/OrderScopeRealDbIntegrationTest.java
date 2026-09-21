package com.blade.order;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderPlaceholderSplitService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1 证据补强：真实 MySQL 下的档口范围反例（非 mock）。
 *
 * <p>用真实 Spring 服务 + 真实 DB 验证：仅绑定 A 档口的用户对 B 档口订单
 * 发货/占位拆分必须在鉴权最先发生时 403，且订单状态、plan/inventory/state-transition/
 * adjustment log、明细行数均不变；已 SHIPPED 的幂等路径也必须先鉴权。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderScopeRealDbIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderActionService orderActionService;
    @Autowired private OrderPlaceholderSplitService placeholderSplitService;

    private Long outletA;
    private Long outletB;
    private Long userId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        String suffix = Long.toString(System.nanoTime()).substring(8);
        outletA = outlet("RDB-A-" + suffix, 1);
        outletB = outlet("RDB-B-" + suffix, 1);
        userId = user("rdb_u_" + suffix);
        bind(userId, outletA);
        authenticate(userId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void realDb_assignedAUserCannotShipOutletBOrderAndNothingChanges() {
        Long orderId = order("RDB-SHIP-" + System.nanoTime(), outletB, userId,
                "READY_TO_SHIP", 3, 0);
        int plansBefore = count("SELECT COUNT(*) FROM order_delivery_plan WHERE order_id=?", orderId);
        int transitionsBefore = count("SELECT COUNT(*) FROM order_state_transition_log WHERE order_id=?", orderId);
        int inventoryLogsBefore = count("SELECT COUNT(*) FROM inventory_log");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderActionService.shipOrder(orderId, "TEST"));

        assertEquals(403, ex.getCode());
        assertEquals("READY_TO_SHIP", jdbc.queryForObject(
                "SELECT fulfillment_status FROM sale_order WHERE id=?", String.class, orderId));
        assertEquals(3, jdbc.queryForObject("SELECT status FROM sale_order WHERE id=?", Integer.class, orderId));
        assertEquals(0, jdbc.queryForObject("SELECT is_delivered FROM sale_order WHERE id=?", Integer.class, orderId));
        assertEquals(plansBefore, count("SELECT COUNT(*) FROM order_delivery_plan WHERE order_id=?", orderId));
        assertEquals(transitionsBefore, count("SELECT COUNT(*) FROM order_state_transition_log WHERE order_id=?", orderId));
        assertEquals(inventoryLogsBefore, count("SELECT COUNT(*) FROM inventory_log"));
    }

    @Test
    void realDb_idempotentAlreadyShippedStillRequiresAccessFirst() {
        Long orderId = order("RDB-SHIP2-" + System.nanoTime(), outletB, userId,
                "SHIPPED", 4, 1);
        int transitionsBefore = count("SELECT COUNT(*) FROM order_state_transition_log WHERE order_id=?", orderId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderActionService.shipOrder(orderId, "TEST"));

        assertEquals(403, ex.getCode());
        assertEquals("SHIPPED", jdbc.queryForObject(
                "SELECT fulfillment_status FROM sale_order WHERE id=?", String.class, orderId));
        assertEquals(transitionsBefore, count("SELECT COUNT(*) FROM order_state_transition_log WHERE order_id=?", orderId));
    }

    @Test
    void realDb_assignedAUserCannotSplitOutletBPlaceholderOrderAndNothingChanges() {
        Long orderId = order("RDB-SPLIT-" + System.nanoTime(), outletB, userId,
                "WAITING_ALLOCATION", 3, 0);
        Long placeholderItemId = placeholderItem(orderId);
        int itemsBefore = count("SELECT COUNT(*) FROM sale_order_item WHERE order_id=?", orderId);
        int adjustmentsBefore = count("SELECT COUNT(*) FROM order_adjustment_log WHERE order_id=?", orderId);

        OrderPlaceholderSplitService.SplitTarget target = new OrderPlaceholderSplitService.SplitTarget();
        target.setSkuId(1L);
        target.setQuantity(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> placeholderSplitService.splitPlaceholderItem(orderId, placeholderItemId, List.of(target), "TEST"));

        assertEquals(403, ex.getCode());
        assertEquals(itemsBefore, count("SELECT COUNT(*) FROM sale_order_item WHERE order_id=?", orderId));
        assertEquals(adjustmentsBefore, count("SELECT COUNT(*) FROM order_adjustment_log WHERE order_id=?", orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM sale_order_item WHERE id=?", placeholderItemId));
    }

    // ==================== fixtures ====================

    private void authenticate(Long actorId) {
        User principal = new User();
        principal.setId(actorId);
        principal.setUsername("rdb_user_" + actorId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(
                        new SimpleGrantedAuthority("btn:order:deliver"),
                        new SimpleGrantedAuthority("btn:order:allocate"),
                        new SimpleGrantedAuthority("btn:order:view"))));
    }

    private Long outlet(String code, int status) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default)"
                + " VALUES(1,?,?,'STORE',?,0,0,0)", code, "档口" + code, status);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=1 AND outlet_code=?", Long.class, code);
    }

    private Long user(String username) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,'真实DB',1,1,0)",
                username, "x");
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
    }

    private void bind(Long actorId, Long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) "
                + "VALUES(1,?,?,1,1,0)", actorId, outletId);
    }

    private Long order(String orderNo, Long outletId, Long salesmanId, String fulfillmentStatus,
                       int status, int isDelivered) {
        jdbc.update("INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,"
                        + "payment_status,status,fulfillment_status,fulfillment_mode,collection_status,"
                        + "gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,"
                        + "is_delivered,salesman_id,source_outlet_id,tenant_id,deleted,version) "
                        + "VALUES(?,CURDATE(),'SPOT','真实DB',100,0,0,?,?,'UNDECIDED','UNPAID',0,0,0,0,100,?,?,?,1,0,0)",
                orderNo, status, fulfillmentStatus, isDelivered, salesmanId, outletId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, orderNo);
    }

    private Long placeholderItem(Long orderId) {
        jdbc.update("INSERT INTO sale_order_item(tenant_id,order_id,product_id,product_name,color_name,size_name,"
                + "price,quantity,subtotal) VALUES(1,?,1,'占位商品','未指定颜色','未指定尺码',10,2,20)", orderId);
        return jdbc.queryForObject("SELECT id FROM sale_order_item WHERE order_id=? ORDER BY id DESC LIMIT 1",
                Long.class, orderId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
