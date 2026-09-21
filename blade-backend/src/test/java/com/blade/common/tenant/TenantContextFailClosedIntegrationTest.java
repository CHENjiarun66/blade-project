package com.blade.common.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.auth.dto.LoginRequest;
import com.blade.auth.dto.LoginResponse;
import com.blade.common.exception.BusinessException;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.service.CustomerService;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.service.OutletService;
import com.blade.product.dto.ProductCreateDTO;
import com.blade.product.entity.Product;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.service.ProductService;
import com.blade.system.permission.dto.PermissionCreateDTO;
import com.blade.system.permission.dto.RolePermissionDTO;
import com.blade.system.permission.service.PermissionService;
import com.blade.system.user.dto.RoleCreateDTO;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.service.RoleService;
import com.blade.system.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第四批：租户上下文 fail-closed（真实 DB + 真实登录）。
 *
 * <p>覆盖：无上下文时 user/role/permission/product/customer/outlet 写路径稳定 403 且零写入；
 * 真实 MyBatis 查询无上下文 fail-closed；tenant1/tenant2 真实登录仍正常（认证链显式设置上下文）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantContextFailClosedIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserService userService;
    @Autowired private RoleService roleService;
    @Autowired private PermissionService permissionService;
    @Autowired private ProductService productService;
    @Autowired private CustomerService customerService;
    @Autowired private OutletService outletService;
    @Autowired private ProductMapper productMapper;

    private static final String PASSWORD = "tenant-ctx-123";

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void writePathsWithoutTenantFailClosedAndWriteNothing() {
        int users = count("sys_user");
        int roles = count("sys_role");
        int permissions = count("sys_permission");
        int rolePermissions = count("sys_role_permission");
        int products = count("product");
        int customers = count("crm_customer");
        int outlets = count("sales_outlet");

        assertForbidden(() -> userService.create(new UserCreateDTO()));
        assertForbidden(() -> roleService.create(new RoleCreateDTO()));

        PermissionCreateDTO permission = new PermissionCreateDTO();
        permission.setCode("ctx:test");
        permission.setName("ctx");
        assertForbidden(() -> permissionService.create(permission));

        RolePermissionDTO assign = new RolePermissionDTO();
        assign.setRoleId(1L);
        assertForbidden(() -> permissionService.assignPermissions(assign));

        assertForbidden(() -> productService.create(new ProductCreateDTO()));
        assertForbidden(() -> customerService.createCustomer(new CustomerCreateDTO()));
        assertForbidden(() -> outletService.create(new OutletCreateDTO()));

        assertEquals(users, count("sys_user"), "user 写路径缺上下文不得写入");
        assertEquals(roles, count("sys_role"), "role 写路径缺上下文不得写入");
        assertEquals(permissions, count("sys_permission"), "permission 写路径缺上下文不得写入");
        assertEquals(rolePermissions, count("sys_role_permission"), "assignPermissions 缺上下文不得删除/写入");
        assertEquals(products, count("product"), "product 写路径缺上下文不得写入");
        assertEquals(customers, count("crm_customer"), "customer 写路径缺上下文不得写入");
        assertEquals(outlets, count("sales_outlet"), "outlet 写路径缺上下文不得写入");
    }

    @Test
    void realMyBatisQueryWithoutTenantFailsClosed() {
        TenantContext.clear();

        // 真实 MyBatis 查询：拦截器必须 fail closed，不得隐式 tenant_id=1
        assertThrows(RuntimeException.class,
                () -> productMapper.selectCount(new LambdaQueryWrapper<Product>()));
    }

    @Test
    void loginForTenant1AndTenant2StillWorks() throws Exception {
        String suffix = Long.toString(System.nanoTime()).substring(8);
        String user1 = "ctx_t1_" + suffix;
        String user2 = "ctx_t2_" + suffix;
        String encoded = passwordEncoder.encode(PASSWORD);
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                user1, encoded, user1);
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,2,0)",
                user2, encoded, user2);

        String token1 = login("test_tenant", user1);
        String token2 = login("demo_tenant", user2);

        assertTrue(token1 != null && !token1.isBlank(), "tenant1 登录应成功");
        assertTrue(token2 != null && !token2.isBlank(), "tenant2 登录应成功");
    }

    // ==================== helpers ====================

    private void assertForbidden(Runnable call) {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        BusinessException ex = assertThrows(BusinessException.class, call::run);
        assertEquals(403, ex.getCode(), "缺上下文必须业务 403: " + ex.getMessage());
    }

    private String login(String tenantCode, String username) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setTenantCode(tenantCode);
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

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
