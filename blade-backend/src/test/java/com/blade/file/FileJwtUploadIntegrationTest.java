package com.blade.file;

import com.blade.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Login -> real JWT filter -> upload, private preview and product binding. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FileJwtUploadIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private RedisTemplate<String, Object> redis;
    private final List<String> redisKeys = new ArrayList<>();
    private final List<Path> uploadedPaths = new ArrayList<>();

    @AfterEach
    void cleanup() throws Exception {
        if (!redisKeys.isEmpty()) redis.delete(redisKeys);
        for (Path path : uploadedPaths) Files.deleteIfExists(path);
        TenantContext.clear();
    }

    private record Login(long userId, String token) {}

    private Login login(boolean productPermission) throws Exception {
        String username = "upload" + Long.toHexString(System.nanoTime());
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                username, passwords.encode("upload-test-password"), username);
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        assertNotEquals(1L, userId);
        if (productPermission) {
            jdbc.update("INSERT INTO sys_role(role_name,role_code,status,tenant_id,deleted) VALUES(?,?,1,1,0)",
                    username, username);
            long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, username);
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id,deleted) VALUES(?,?,1,0)", userId, roleId);
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id,tenant_id,deleted) "
                    + "SELECT ?,id,1,0 FROM sys_permission WHERE code='menu:product' AND deleted=0", roleId);
        }
        String tenantCode = jdbc.queryForObject("SELECT tenant_code FROM sys_tenant WHERE id=1", String.class);
        String body = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tenantCode", tenantCode, "username", username,
                                "password", "upload-test-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);
        String token = response.get("accessToken").asText();
        String refresh = response.get("refreshToken").asText();
        redisKeys.addAll(List.of("token:" + token, "token:tenant:" + token,
                "token:tenant:" + refresh, "refresh:" + refresh));
        return new Login(userId, token);
    }

    private long product() {
        String code = "UPLOAD-" + System.nanoTime();
        jdbc.update("INSERT INTO product(product_code,name,unit,status,tenant_id,deleted) VALUES(?,?,'件',1,1,0)",
                code, code);
        return jdbc.queryForObject("SELECT id FROM product WHERE product_code=?", Long.class, code);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "upload-regression.png", "image/png", Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII="));
    }

    @Test
    void realLoginCanUploadPreviewAndBindProductImageAsActualUser() throws Exception {
        long productId = product();
        Login login = login(true);
        String bearer = "Bearer " + login.token();
        String body = mvc.perform(multipart("/api/files/upload").file(image())
                        .param("businessType", "product").param("businessId", Long.toString(productId))
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long fileId = json.readTree(body).at("/data/id").asLong();
        String path = jdbc.queryForObject("SELECT storage_path FROM file_storage WHERE id=?", String.class, fileId);
        uploadedPaths.add(Path.of(path));
        assertTrue(Files.exists(Path.of(path)));
        assertEquals(login.userId(), jdbc.queryForObject("SELECT create_by FROM file_storage WHERE id=?", Long.class, fileId));
        assertEquals(1L, jdbc.queryForObject("SELECT tenant_id FROM file_storage WHERE id=?", Long.class, fileId));

        // Ordinary product permission must suffice; the account has no file viewAll permission.
        mvc.perform(get("/api/files/" + fileId + "/preview").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(image().getBytes()));
        mvc.perform(put("/api/products/" + productId + "/file-bindings")
                        .header("Authorization", bearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("mainFileId", fileId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertEquals(Long.toString(fileId), jdbc.queryForObject("SELECT image_url FROM product WHERE id=?", String.class, productId));
        assertEquals(login.userId(), jdbc.queryForObject(
                "SELECT create_by FROM file_business_bind WHERE file_id=? AND bind_role='main' AND deleted=0", Long.class, fileId));
        mvc.perform(get("/api/user/info").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(Long.toString(login.userId())));
    }

    @Test
    void realLoginCanReadOwnUnboundUploadButAnotherUserCannot() throws Exception {
        Login owner = login(false);
        String body = mvc.perform(multipart("/api/files/upload").file(image()).param("businessType", "temp")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long fileId = json.readTree(body).at("/data/id").asLong();
        uploadedPaths.add(Path.of(jdbc.queryForObject(
                "SELECT storage_path FROM file_storage WHERE id=?", String.class, fileId)));
        mvc.perform(get("/api/files/" + fileId + "/preview").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        Login other = login(false);
        mvc.perform(get("/api/files/" + fileId + "/preview").header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void realLoginWithoutProductPermissionCannotUploadToProduct() throws Exception {
        long productId = product();
        Login login = login(false);
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM file_storage", Long.class);
        mvc.perform(multipart("/api/files/upload").file(image()).param("businessType", "product")
                        .param("businessId", Long.toString(productId)).header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM file_storage", Long.class));
    }
}
