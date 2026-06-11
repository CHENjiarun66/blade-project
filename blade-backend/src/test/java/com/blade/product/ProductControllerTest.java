package com.blade.product;

import com.blade.auth.dto.LoginRequest;
import com.blade.auth.dto.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 商品模块接口测试
 *
 * 测试覆盖：
 * 1. 正常场景 - 输入正确数据
 * 2. 边界场景 - 空值、极限值
 * 3. 异常场景 - 未认证、数据不存在
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        // 登录获取 token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        adminToken = loginResponse.getToken();
    }

    // ========== 认证测试 ==========

    @Test
    void testLoginSuccess() {
        assertNotNull(adminToken);
    }

    // ========== 列表查询测试 ==========

    @Test
    void testListProducts() throws Exception {
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void testListProductsWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testListColors() throws Exception {
        mockMvc.perform(get("/api/products/colors")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].colorCode").exists());
    }

    @Test
    void testListSizes() throws Exception {
        mockMvc.perform(get("/api/products/sizes")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].sizeCode").exists());
    }

    // ========== 创建商品测试 ==========

    @Test
    void testCreateProduct() throws Exception {
        String productJson = """
            {
                "name": "测试商品A",
                "productCode": "TMPA001",
                "categoryId": 1,
                "unit": "件",
                "price": 99.00,
                "description": "测试描述"
            }
            """;

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void testCreateProductWithSku() throws Exception {
        String productJson = """
            {
                "name": "测试商品B",
                "productCode": "TMPA002",
                "categoryId": 1,
                "unit": "件",
                "price": 199.00,
                "description": "测试描述",
                "colorIds": [1, 2],
                "sizeIds": [1, 2]
            }
            """;

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void testCreateProductWithoutSku() throws Exception {
        String productJson = """
            {
                "name": "测试商品C",
                "productCode": "TMPA003",
                "categoryId": 1,
                "unit": "件",
                "price": 50.00
            }
            """;

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testCreateProductWithoutAuth() throws Exception {
        String productJson = """
            {
                "name": "测试商品",
                "productCode": "TMPX001",
                "categoryId": 1,
                "unit": "件",
                "price": 99.00
            }
            """;

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson))
                .andExpect(status().isForbidden());
    }

    // ========== 更新商品测试 ==========

    @Test
    void testUpdateProduct() throws Exception {
        // 先创建商品
        String createJson = """
            {
                "name": "待修改商品",
                "productCode": "TMPB001",
                "categoryId": 1,
                "unit": "件",
                "price": 100.00
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        Long productId = objectMapper.readTree(createResponse).get("data").asLong();

        // 修改商品
        String updateJson = String.format("""
            {
                "id": %d,
                "name": "修改后的商品",
                "price": 159.00
            }
            """, productId);

        mockMvc.perform(put("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testUpdateProductSyncsSkusWhenColorsChange() throws Exception {
        String createJson = """
            {
                "name": "SKU同步测试商品",
                "productCode": "TMPB002",
                "categoryId": 1,
                "unit": "件",
                "costPrice": 60.00,
                "wholesalePrice": 88.00,
                "colorIds": [1],
                "sizeIds": [1]
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        String updateJson = String.format("""
            {
                "id": %d,
                "colorIds": [1, 2, 3],
                "sizeIds": [1]
            }
            """, productId);

        mockMvc.perform(put("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/products/" + productId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.colors.length()").value(3))
                .andExpect(jsonPath("$.data.skus.length()").value(3));
    }

    @Test
    void testUpdateNonexistentProduct() throws Exception {
        String updateJson = """
            {
                "id": 99999,
                "name": "不存在的商品"
            }
            """;

        mockMvc.perform(put("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== 删除商品测试 ==========

    @Test
    void testDeleteProduct() throws Exception {
        // 先创建商品
        String createJson = """
            {
                "name": "待删除商品",
                "productCode": "TMPC001",
                "categoryId": 1,
                "unit": "件",
                "price": 50.00
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        Long productId = objectMapper.readTree(createResponse).get("data").asLong();

        // 删除商品
        mockMvc.perform(delete("/api/products/" + productId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testDeleteNonexistentProduct() throws Exception {
        mockMvc.perform(delete("/api/products/99999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== 查询商品测试 ==========

    @Test
    void testGetProductById() throws Exception {
        // 先创建商品
        String createJson = """
            {
                "name": "待查询商品",
                "productCode": "TMPD001",
                "categoryId": 1,
                "unit": "件",
                "price": 80.00
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        Long productId = objectMapper.readTree(createResponse).get("data").asLong();

        // 查询商品
        mockMvc.perform(get("/api/products/" + productId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(productId));
    }

    @Test
    void testGetNonexistentProduct() throws Exception {
        mockMvc.perform(get("/api/products/99999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

}
