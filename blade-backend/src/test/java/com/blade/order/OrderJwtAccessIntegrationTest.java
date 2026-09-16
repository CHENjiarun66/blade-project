package com.blade.order;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实登录/JWT filter/UserDetails principal 下的销售订单数据范围。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderJwtAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void salesJwt_canReadOwnOrderButNotAnotherSalesOrder() throws Exception {
        String suffix = Long.toString(System.nanoTime()).substring(8);
        String userA = "sa" + suffix;
        String userB = "sb" + suffix;
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='ROLE_SALES' AND tenant_id=1 AND deleted=0", Long.class);
        String encoded = passwordEncoder.encode("jwt-test-123");
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                userA, encoded, "销售A");
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                userB, encoded, "销售B");
        Long userAId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, userA);
        Long userBId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, userB);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id,deleted) VALUES(?,?,1,0)", userAId, roleId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id,deleted) VALUES(?,?,1,0)", userBId, roleId);
        Long ownOrder = seedOrder("JWT-A-" + suffix, userAId);
        Long otherOrder = seedOrder("JWT-B-" + suffix, userBId);

        String token = login(userA);

        mockMvc.perform(get("/api/orders/{id}", ownOrder)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(ownOrder));
        mockMvc.perform(get("/api/orders/{id}", otherOrder)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .param("customerName", "JWT范围测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(ownOrder));
    }

    private Long seedOrder(String orderNo, Long salesmanId) {
        jdbc.update("""
                INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,
                  payment_status,status,fulfillment_status,fulfillment_mode,collection_status,
                  gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,
                  salesman_id,tenant_id,deleted,version)
                VALUES(?,CURDATE(),'SPOT','JWT范围测试',100,0,0,0,'CONFIRMED','UNDECIDED','UNPAID',0,0,0,0,100,?,1,0,0)
                """, orderNo, salesmanId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, orderNo);
    }

    private String login(String username) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setTenantCode("test_tenant");
        request.setUsername(username);
        request.setPassword("jwt-test-123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class).getToken();
    }
}
