package com.blade.file;

import com.blade.auth.service.JwtTokenProvider;
import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Series E2：previewToken 只建立 JWT 身份，不绕过 order/order_draft 业务档口授权。
 *
 * <p>真实 MVC + Spring Security 过滤器链路：即使文件 visibility=PUBLIC，跨档口 token 仍 403。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FilePreviewTokenAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private FileStorageProperties properties;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private long seedOutlet(long tenantId, String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                + "VALUES(?,?,?,'STORE',1,0,0,0)", tenantId, code, "档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?", Long.class, tenantId, code);
    }

    private long seedUser(long tenantId, String name) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,?,0)",
                name, "$2a$10$abcdefghijklmnopqrstuv", name, tenantId);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private void bindUser(long userId, long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,0,1,0)",
                userId, outletId);
    }

    private long seedOrder(long tenantId, Long outletId, Long salesmanId) {
        String no = "E2T-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,
                  gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,
                  write_off_amount,gross_profit,fulfillment_status,fulfillment_mode,collection_status,
                  salesman_id,source_outlet_id,tenant_id,deleted,version)
                VALUES(?,?,'SPOT','E2T客户',100,0,100,0,0,0,0,0,40,'CONFIRMED','UNDECIDED','PARTIAL',?,?,?,0,0)
                """, no, LocalDate.now(), salesmanId, outletId, tenantId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, no);
    }

    private long seedPublicOrderFile(long tenantId, Long createBy, long orderId) {
        long fileId = seedFileOnDisk(tenantId, createBy, "PUBLIC");
        jdbc.update("INSERT INTO file_business_bind(file_id,business_type,business_id,sort,is_primary,tenant_id,deleted) "
                + "VALUES(?,'order',?,0,1,1,0)", fileId, orderId);
        return fileId;
    }

    /** 在配置的本地存储根下写真实文件，使匿名 PUBLIC 预览可以成功加载。 */
    private long seedFileOnDisk(long tenantId, Long createBy, String visibility) {
        String relative = "e2t/" + System.nanoTime() + ".png";
        String absolutePath;
        try {
            Path base = Path.of(properties.getLocalBasePath()).toAbsolutePath().normalize();
            Path target = base.resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, new byte[]{1, 2, 3});
            absolutePath = target.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        jdbc.update("INSERT INTO file_storage(file_key,original_name,file_name,storage_type,storage_path,status,tenant_id,"
                        + "create_by,visibility,file_type) VALUES(?,?,?,'local',?,1,?,?,?, 'IMAGE')",
                relative, "e2t.png", "e2t.png", absolutePath, tenantId, createBy, visibility);
        return jdbc.queryForObject("SELECT id FROM file_storage WHERE file_key=?", Long.class, relative);
    }

    @Test
    void anonymousPublicNonSensitiveFile_succeedsWithoutTenantContext() throws Exception {
        long anonymousFile = seedFileOnDisk(1L, 999L, "PUBLIC");
        mockMvc.perform(get("/api/files/" + anonymousFile + "/preview"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void anonymousPublicOrderFile_rejected() throws Exception {
        long outletB = seedOutlet(1L, "E2T-ANON");
        long salesB = seedUser(1L, "e2anon" + System.nanoTime() % 100000);
        long orderB = seedOrder(1L, outletB, salesB);
        long fileB = seedPublicOrderFile(1L, salesB, orderB);

        mockMvc.perform(get("/api/files/" + fileB + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void previewToken_crossOutletPublicOrderFile_stillForbidden() throws Exception {
        long outletA = seedOutlet(1L, "E2T-A");
        long outletB = seedOutlet(1L, "E2T-B");
        String username = "e2token" + System.nanoTime() % 100000;
        long salesA = seedUser(1L, username);
        long salesB = seedUser(1L, "e2tokenb" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        long orderB = seedOrder(1L, outletB, salesB);
        long fileB = seedPublicOrderFile(1L, salesB, orderB);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                username, "n/a", List.of());
        String token = jwtTokenProvider.generateToken(userDetails, 1L);
        redisTemplate.opsForValue().set("token:" + token, username);
        redisTemplate.opsForValue().set("token:tenant:" + token, 1L);
        try {
            mockMvc.perform(get("/api/files/" + fileB + "/preview").param("previewToken", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        } finally {
            redisTemplate.delete("token:" + token);
            redisTemplate.delete("token:tenant:" + token);
        }
    }
}
