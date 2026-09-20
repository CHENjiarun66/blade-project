package com.blade.outlet;

import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.service.OrderService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-1 真实一致性：ASSIGNED + data:outlet:unassigned 时列表与详情都必须能看到
 * source_outlet_id=NULL；无 unassigned 时两者都不可见。分页前过滤。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutletNullScopeIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderService orderService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void auth(Long userId, String... authorities) {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(userId);
        principal.setUsername("u" + userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private long seedOutlet(String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) VALUES(1,?,?,'STORE',1,0,0,0)",
                code, "矩阵档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE outlet_code=?", Long.class, code);
    }

    private long seedUser(String name) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                name, "x", name);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private long seedOrder(String no, Long salesmanId, Long outletId) {
        jdbc.update("""
                INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,
                  payment_status,status,fulfillment_status,fulfillment_mode,collection_status,
                  gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,
                  salesman_id,source_outlet_id,tenant_id,deleted,version)
                VALUES(?,CURDATE(),'SPOT','矩阵客户',100,0,0,0,'CONFIRMED','UNDECIDED','UNPAID',0,0,0,0,100,?,?,1,0,0)
                """, no, salesmanId, outletId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, no);
    }

    @Test
    void assignedWithUnassignedSeesNullInListAndDetail() {
        long outletId = seedOutlet("SCN" + System.nanoTime() % 100000);
        long userId = seedUser("scnu" + System.nanoTime() % 100000);
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,0,1,0)",
                userId, outletId);
        long outletOrder = seedOrder("SCN-A-" + System.nanoTime(), userId, outletId);
        long nullOrder = seedOrder("SCN-N-" + System.nanoTime(), userId, null);

        auth(userId, "menu:order", "data:outlet:unassigned");
        PageResult<OrderVO> page = orderService.pageList(pageDto());
        List<Long> ids = page.getRecords().stream().map(OrderVO::getId).toList();
        assertTrue(ids.contains(outletOrder), "列表应含绑定档口订单");
        assertTrue(ids.contains(nullOrder), "列表应含未归档订单（unassigned）");
        // 详情一致
        orderService.getById(nullOrder);
        orderService.getById(outletOrder);
    }

    @Test
    void assignedWithoutUnassignedCannotSeeNullInListOrDetail() {
        long outletId = seedOutlet("SCX" + System.nanoTime() % 100000);
        long userId = seedUser("scxu" + System.nanoTime() % 100000);
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,0,1,0)",
                userId, outletId);
        long outletOrder = seedOrder("SCX-A-" + System.nanoTime(), userId, outletId);
        long nullOrder = seedOrder("SCX-N-" + System.nanoTime(), userId, null);

        auth(userId, "menu:order");
        PageResult<OrderVO> page = orderService.pageList(pageDto());
        List<Long> ids = page.getRecords().stream().map(OrderVO::getId).toList();
        assertTrue(ids.contains(outletOrder), "列表应含绑定档口订单");
        assertTrue(!ids.contains(nullOrder), "列表不应含未归档订单");
        assertThrows(BusinessException.class, () -> orderService.getById(nullOrder),
                "详情不应可访问未归档订单");
    }

    private OrderPageDTO pageDto() {
        OrderPageDTO dto = new OrderPageDTO();
        dto.setCurrent(1L);
        dto.setSize(100L);
        return dto;
    }
}
