package com.blade.dashboard;

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

/**
 * 第二批A：看板/分析入口必须有服务端菜单权限校验，不能仅靠前端菜单隐藏。
 * 真实登录/JWT + 真实 DB 权限（sys_role_permission）验证无权限 403、有权限可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardAnalyticsPermissionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "dash-perm-123";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void dashboardEntryRequiresMenuDashboard() throws Exception {
        String noPerm = userWith();
        String withDash = userWith("menu:dashboard");
        String withAnalyticsOnly = userWith("menu:analytics");

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + login(noPerm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/dashboard/trend").header("Authorization", "Bearer " + login(noPerm)))
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + login(withDash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + login(withAnalyticsOnly)))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void analyticsEntryRequiresMenuAnalytics() throws Exception {
        String noPerm = userWith();
        String withAnalytics = userWith("menu:analytics");
        String withDashboardOnly = userWith("menu:dashboard");

        mockMvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + login(noPerm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/analytics/trend").header("Authorization", "Bearer " + login(noPerm)))
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + login(withAnalytics)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + login(withDashboardOnly)))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== fixtures ====================

    private String userWith(String... permissionCodes) {
        String suffix = Long.toString(System.nanoTime()).substring(8);
        String roleCode = "E2B_MENU_" + suffix;
        jdbc.update("INSERT INTO sys_role(role_name,role_code,status,tenant_id,deleted) VALUES(?,?,1,1,0)",
                "第二批A菜单角色" + suffix, roleCode);
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, roleCode);
        for (String code : permissionCodes) {
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id,tenant_id,deleted) "
                    + "SELECT ?, p.id, 1, 0 FROM sys_permission p WHERE p.code=?", roleId, code);
        }
        String username = "e2b_menu_" + suffix;
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                username, passwordEncoder.encode(PASSWORD), username);
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id,deleted) VALUES(?,?,1,0)", userId, roleId);
        return username;
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
