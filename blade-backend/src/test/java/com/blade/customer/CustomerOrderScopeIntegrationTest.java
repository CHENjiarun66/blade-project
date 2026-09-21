package com.blade.customer;

import com.blade.auth.dto.LoginRequest;
import com.blade.auth.dto.LoginResponse;
import com.blade.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-3 审计整改：客户订单/统计/偏好必须应用档口 × 人员范围，
 * 缓存键包含范围指纹，三个出口要求 {@code btn:customer:viewOrders}。
 *
 * <p>真实登录/JWT filter + 真实 DB + 真实 Redis 缓存。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerOrderScopeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "cust-scope-123";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void salesSelfSeesOnlyOwnOutletAOrder() throws Exception {
        Fixture f = seedFixture();
        String token = login(f.userSelfName);
        Long c = f.customerId;

        mockMvc.perform(get("/api/customers/{id}/stats", c).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalOrders").value(1))
                .andExpect(jsonPath("$.data.completedOrders").value(1))
                .andExpect(jsonPath("$.data.totalSpending").value(100.00));

        mockMvc.perform(get("/api/customers/{id}/orders", c).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(f.oA1));

        mockMvc.perform(get("/api/customers/{id}/preference", c).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productTypeCount").value(1))
                .andExpect(jsonPath("$.data.colors.length()").value(1));
    }

    @Test
    void leadSeesOutletAAllPeopleButNotOutletB() throws Exception {
        Fixture f = seedFixture();
        String token = login(f.userLeadName);

        mockMvc.perform(get("/api/customers/{id}/stats", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.totalSpending").value(150.00));

        mockMvc.perform(get("/api/customers/{id}/orders", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.productTypeCount").value(2));
    }

    @Test
    void ownerSeesAllArchivedOrdersButExcludesNullOutlet() throws Exception {
        Fixture f = seedFixture();
        String token = login(f.userOwnerName);

        mockMvc.perform(get("/api/customers/{id}/stats", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(3))
                .andExpect(jsonPath("$.data.totalSpending").value(220.00));

        mockMvc.perform(get("/api/customers/{id}/orders", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.productTypeCount").value(3));
    }

    @Test
    void noneScopeSeesEmpty() throws Exception {
        Fixture f = seedFixture();
        String token = login(f.userNoneName);

        mockMvc.perform(get("/api/customers/{id}/stats", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.totalSpending").value(0));

        mockMvc.perform(get("/api/customers/{id}/orders", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.productTypeCount").value(0));
    }

    @Test
    void missingViewOrdersAuthorityReturns403OnAllThreeExits() throws Exception {
        Fixture f = seedFixture();
        String token = login(f.userNoPermName);

        mockMvc.perform(get("/api/customers/{id}/stats", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/customers/{id}/orders", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void preferenceCacheIsIsolatedByScopeFingerprint() throws Exception {
        Fixture f = seedFixture();
        String selfToken = login(f.userSelfName);
        String leadToken = login(f.userLeadName);

        // 同一客户、不同范围：先 self 后 lead，结果不得复用
        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + selfToken))
                .andExpect(jsonPath("$.data.productTypeCount").value(1));
        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.data.productTypeCount").value(2));
        // 再次 self 命中自身缓存，仍为 1
        mockMvc.perform(get("/api/customers/{id}/preference", f.customerId).header("Authorization", "Bearer " + selfToken))
                .andExpect(jsonPath("$.data.productTypeCount").value(1));
    }

    // ==================== 客户 orderCount 范围一致性 ====================

    @Test
    void orderCountIsScopedOnListDetailAndSearch() throws Exception {
        Fixture f = seedFixture();
        Long c = f.customerId;
        String phone = f.customerPhone;

        // SELF + ASSIGNED A：仅 oA1 -> 1
        String selfToken = login(f.userSelfName);
        mockMvc.perform(get("/api/customers").param("keyword", f.customerName)
                        .header("Authorization", "Bearer " + selfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(1));
        mockMvc.perform(get("/api/customers/{id}", c).header("Authorization", "Bearer " + selfToken))
                .andExpect(jsonPath("$.data.orderCount").value(1));
        mockMvc.perform(get("/api/customers/search").param("phone", phone)
                        .header("Authorization", "Bearer " + selfToken))
                .andExpect(jsonPath("$.data.orderCount").value(1));

        // ALL_USERS + ASSIGNED A：oA1+oA2 -> 2
        String leadToken = login(f.userLeadName);
        mockMvc.perform(get("/api/customers").param("keyword", f.customerName)
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(2));
        mockMvc.perform(get("/api/customers/{id}", c).header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.data.orderCount").value(2));
        mockMvc.perform(get("/api/customers/search").param("phone", phone)
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.data.orderCount").value(2));

        // ALL + ALL_USERS：oA1+oA2+oB1 -> 3，排除 source_outlet_id NULL
        String ownerToken = login(f.userOwnerName);
        mockMvc.perform(get("/api/customers").param("keyword", f.customerName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(3));
        mockMvc.perform(get("/api/customers/{id}", c).header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data.orderCount").value(3));

        // NONE：0
        String noneToken = login(f.userNoneName);
        mockMvc.perform(get("/api/customers").param("keyword", f.customerName)
                        .header("Authorization", "Bearer " + noneToken))
                .andExpect(jsonPath("$.data.records[0].orderCount").value(0));
        mockMvc.perform(get("/api/customers/{id}", c).header("Authorization", "Bearer " + noneToken))
                .andExpect(jsonPath("$.data.orderCount").value(0));
        mockMvc.perform(get("/api/customers/search").param("phone", phone)
                        .header("Authorization", "Bearer " + noneToken))
                .andExpect(jsonPath("$.data.orderCount").value(0));
    }

    // ==================== fixtures ====================

    private record Fixture(Long customerId, String customerName, String customerPhone, Long oA1,
                           String userSelfName, String userLeadName,
                           String userOwnerName, String userNoneName, String userNoPermName) {
    }

    private Fixture seedFixture() {
        String suffix = Long.toString(System.nanoTime()).substring(8);
        Long outletA = outlet("CS-A-" + suffix);
        Long outletB = outlet("CS-B-" + suffix);
        String customerName = "范围客户" + suffix;
        Long customerId = customer(customerName);
        String customerPhone = "138" + suffix;
        jdbc.update("INSERT INTO crm_customer_phone(customer_id,phone,is_primary,tenant_id,deleted) "
                + "VALUES(?,?,1,1,0)", customerId, customerPhone);

        Long roleSelf = role("E2E_CS_SELF", "客户范围销售");
        grant(roleSelf, "btn:customer:viewOrders");
        Long roleLead = role("E2E_CS_LEAD", "客户范围负责人");
        grant(roleLead, "btn:customer:viewOrders");
        grant(roleLead, "data:order:peopleAll");
        Long roleNoPerm = role("E2E_CS_NOPERM", "客户范围无权限");
        Long ownerRoleId = jdbc.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='ROLE_OWNER' AND tenant_id=1 AND deleted=0", Long.class);

        String selfName = "cs_self_" + suffix;
        String leadName = "cs_lead_" + suffix;
        String ownerName = "cs_owner_" + suffix;
        String noneName = "cs_none_" + suffix;
        String noPermName = "cs_noperm_" + suffix;
        Long userSelf = user(selfName, roleSelf);
        Long userLead = user(leadName, roleLead);
        Long userOwner = user(ownerName, ownerRoleId);
        Long userNone = user(noneName, roleSelf);
        Long userNoPerm = user(noPermName, roleNoPerm);

        Long oA1 = order("CS-A1-" + suffix, customerId, outletA, userSelf, "100.00");
        Long oA2 = order("CS-A2-" + suffix, customerId, outletA, userLead, "50.00");
        Long oB1 = order("CS-B1-" + suffix, customerId, outletB, userSelf, "70.00");
        order("CS-NULL-" + suffix, customerId, null, userSelf, "9.00");
        item(oA1, "款A", "红色", "M");
        item(oA2, "款B", "蓝色", "L");
        item(oB1, "款C", "绿色", "S");

        bind(userSelf, outletA);
        bind(userLead, outletA);

        return new Fixture(customerId, customerName, customerPhone, oA1, selfName, leadName,
                ownerName, noneName, noPermName);
    }

    private Long outlet(String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default)"
                + " VALUES(1,?,?,'STORE',1,0,0,0)", code, "档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=1 AND outlet_code=?", Long.class, code);
    }

    private Long customer(String name) {
        jdbc.update("INSERT INTO crm_customer(name,address,remark,tenant_id,deleted) VALUES(?, '地址','备注',1,0)", name);
        return jdbc.queryForObject("SELECT id FROM crm_customer WHERE name=? ORDER BY id DESC LIMIT 1", Long.class, name);
    }

    private Long role(String code, String name) {
        jdbc.update("INSERT INTO sys_role(role_name,role_code,status,tenant_id,deleted) VALUES(?,?,1,1,0)", name, code);
        return jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=? AND tenant_id=1", Long.class, code);
    }

    private void grant(Long roleId, String permissionCode) {
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id,tenant_id,deleted) "
                + "SELECT ?, p.id, 1, 0 FROM sys_permission p WHERE p.code=?", roleId, permissionCode);
    }

    private Long user(String username, Long roleId) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                username, passwordEncoder.encode(PASSWORD), username);
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id,deleted) VALUES(?,?,1,0)", userId, roleId);
        return userId;
    }

    private void bind(Long userId, Long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) "
                + "VALUES(1,?,?,1,1,0)", userId, outletId);
    }

    private Long order(String orderNo, Long customerId, Long outletId, Long salesmanId, String gross) {
        jdbc.update("INSERT INTO sale_order(order_no,order_date,order_type,customer_id,customer_name,total_amount,paid_amount,"
                        + "payment_status,status,fulfillment_status,fulfillment_mode,collection_status,gross_received_amount,"
                        + "cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,salesman_id,source_outlet_id,"
                        + "tenant_id,deleted,version) "
                        + "VALUES(?,CURDATE(),'SPOT',?,?,?,0,0,4,'COMPLETED','UNDECIDED','SETTLED',?,0,0,?,0,?,?,1,0,0)",
                orderNo, customerId, "范围客户", new BigDecimal(gross), new BigDecimal(gross),
                new BigDecimal(gross), salesmanId, outletId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, orderNo);
    }

    private void item(Long orderId, String productName, String color, String size) {
        jdbc.update("INSERT INTO sale_order_item(tenant_id,order_id,product_id,product_name,color_name,size_name,price,quantity,subtotal) "
                + "VALUES(1,?,1,?,?,?,10,1,10)", orderId, productName, color, size);
    }

    private String login(String username) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setTenantCode("test_tenant");
        request.setUsername(username);
        request.setPassword(PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class).getToken();
    }
}
