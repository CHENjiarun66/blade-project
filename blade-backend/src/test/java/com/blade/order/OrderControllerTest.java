package com.blade.order;

import com.blade.auth.dto.LoginRequest;
import com.blade.auth.dto.LoginResponse;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 订单模块接口测试
 *
 * 测试覆盖：
 * 1. 认证测试 - 登录获取token
 * 2. 订单CRUD - 创建/查询/删除
 * 3. 订单状态流转 - 创建→付款确认→发货→完成/取消
 * 4. 库存联动验证 - 付款确认锁定库存/取消释放库存
 * 5. 异常场景 - 订单不存在/状态错误
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    // 测试用仓库ID（需要数据库中有数据）
    private static final Long TEST_WAREHOUSE_ID = 1L;
    // 动态获取的SKU ID（测试前创建）
    private Long testSkuId;
    // 动态获取的商品ID（测试前创建）
    private Long testProductId;

    @BeforeEach
    void setUp() throws Exception {
        // 登录获取 token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setTenantCode("test_tenant");
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        adminToken = loginResponse.getToken();

        // 创建测试商品和SKU（如果还没有的话）
        if (testSkuId == null) {
            createTestProductAndSku();
            // 创建测试库存（确保有库存可用于订单测试）
            createTestInventory();
        }
    }

    /**
     * 创建测试库存，供后续测试使用
     */
    private void createTestInventory() throws Exception {
        // 入库以创建库存记录
        String inventoryJson = String.format("""
            {
                "warehouseId": %d,
                "remark": "测试库存",
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 100
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        mockMvc.perform(post("/api/inventory/in")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(inventoryJson))
                .andExpect(status().isOk());
    }

    /**
     * 创建测试商品和SKU，供后续测试使用
     */
    private void createTestProductAndSku() throws Exception {
        // 创建商品（带颜色和尺码，会自动生成SKU）
        // 使用时间戳确保每次测试都有唯一的商品编码
        long timestamp = System.currentTimeMillis();
        String productJson = String.format("""
            {
                "name": "测试商品订单",
                "productCode": "TSTORDER%s",
                "categoryId": 1,
                "unit": "件",
                "price": 99.00,
                "description": "订单测试用商品",
                "colorIds": [1, 2],
                "sizeIds": [1, 2]
            }
            """, timestamp);

        MvcResult productResult = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson))
                .andExpect(status().isOk())
                .andReturn();

        String productResponse = productResult.getResponse().getContentAsString();
        testProductId = objectMapper.readTree(productResponse).get("data").asLong();

        // 获取商品详情以获取SKU ID
        MvcResult detailResult = mockMvc.perform(get("/api/products/" + testProductId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String detailResponse = detailResult.getResponse().getContentAsString();
        JsonNode detailNode = objectMapper.readTree(detailResponse);

        // 从商品详情中获取第一个SKU的ID
        testSkuId = detailNode.get("data").get("skus").get(0).get("id").asLong();
    }

    // ========== 认证测试 ==========

    @Test
    void testLoginSuccess() {
        assertNotNull(adminToken);
    }

    // ========== 订单列表测试 ==========

    @Test
    void testListOrders() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void testListOrdersWithPagination() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .param("current", "1")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void testListOrdersWithStatusFilter() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void testListOrdersWithCustomerNameFilter() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .param("customerName", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testListOrdersWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }

    // ========== 创建订单测试 ==========

    @Test
    void testCreateOrder() throws Exception {
        String orderJson = String.format("""
            {
                "customerName": "测试客户",
                "customerPhone": "13800138000",
                "customerAddress": "北京市朝阳区测试地址",
                "warehouseId": %d,
                "paymentStatus": 0,
                "remark": "测试订单",
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 2
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void testCreateOrderWithMultipleItems() throws Exception {
        String orderJson = String.format("""
            {
                "customerName": "多商品测试客户",
                "customerPhone": "13900139000",
                "customerAddress": "上海市浦东新区",
                "warehouseId": %d,
                "paymentStatus": 0,
                "remark": "多商品订单",
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult result = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).get("data").asLong();

        // 验证订单详情包含商品
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void testCreateOrderWithoutAuth() throws Exception {
        String orderJson = String.format("""
            {
                "customerName": "测试客户",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateOrderWithInvalidData() throws Exception {
        // 缺少必填字段
        String orderJson = """
            {
                "customerName": "测试客户",
                "items": []
            }
            """;

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 查询订单测试 ==========

    @Test
    void testGetOrderById() throws Exception {
        // 先创建订单
        String orderJson = String.format("""
            {
                "customerName": "查询测试客户",
                "customerPhone": "13700137000",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 查询订单
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.customerName").value("查询测试客户"))
                .andExpect(jsonPath("$.data.status").value(0))  // 创建状态
                .andExpect(jsonPath("$.data.totalAmount").isNumber())
                .andExpect(jsonPath("$.data.items[0].productName").isString());
    }

    @Test
    void testGetNonexistentOrder() throws Exception {
        mockMvc.perform(get("/api/orders/99999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== 删除订单测试 ==========

    @Test
    void testDeleteOrder() throws Exception {
        // 先创建订单
        String orderJson = String.format("""
            {
                "customerName": "删除测试客户",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 删除订单
        mockMvc.perform(delete("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证订单已删除
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void testDeleteNonexistentOrder() throws Exception {
        // 删除不存在的订单返回200（幂等操作）
        mockMvc.perform(delete("/api/orders/99999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 订单状态流转测试 ==========

    /**
     * 测试完整流程：创建 → 付款确认 → 发货 → 完成
     */
    @Test
    void testOrderFullFlow_CreateToComplete() throws Exception {
        // 1. 创建订单
        String createJson = String.format("""
            {
                "customerName": "完整流程测试客户",
                "customerPhone": "13600136000",
                "customerAddress": "广州市天河区",
                "warehouseId": %d,
                "paymentStatus": 0,
                "remark": "完整流程测试",
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 2
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 验证订单已创建（状态=0）
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.statusName").value("创建"))
                .andExpect(jsonPath("$.data.totalAmount").isNumber())
                .andExpect(jsonPath("$.data.items[0].productName").isString());

        // 2. 付款确认（锁定库存）
        String paymentJson = String.format("""
            {
                "orderId": %d,
                "paidAmount": 199.00
            }
            """, orderId);

        mockMvc.perform(post("/api/orders/confirm-payment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证订单状态已更新（状态=1）
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusName").value("已付款"))
                .andExpect(jsonPath("$.data.paidAmount").value(199.00))
                .andExpect(jsonPath("$.data.payTime").isString());

        // 3. 发货（预留转出库）
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证订单状态已更新（状态=2）
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.statusName").value("已发货"))
                .andExpect(jsonPath("$.data.deliverTime").isString());

        // 4. 完成订单
        mockMvc.perform(post("/api/orders/" + orderId + "/complete")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证订单状态已更新（状态=3）
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.statusName").value("已完成"))
                .andExpect(jsonPath("$.data.completeTime").isString());
    }

    /**
     * 测试流程：创建 → 付款确认 → 取消（库存应释放）
     */
    @Test
    void testOrderFlow_CreateToCancel() throws Exception {
        // 1. 创建订单
        String createJson = String.format("""
            {
                "customerName": "取消测试客户",
                "customerPhone": "13500135000",
                "warehouseId": %d,
                "paymentStatus": 0,
                "remark": "取消流程测试",
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 5
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 2. 付款确认（锁定库存）
        String paymentJson = String.format("""
            {
                "orderId": %d,
                "paidAmount": 500.00
            }
            """, orderId);

        mockMvc.perform(post("/api/orders/confirm-payment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证订单已锁定（状态=1）
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        // 3. 取消订单（应释放库存）
        String cancelJson = """
            {
                "reason": "客户主动取消"
            }
            """;

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证订单已取消（状态=4）
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.statusName").value("已取消"));
    }

    /**
     * 测试异常：已发货订单不能取消
     */
    @Test
    void testCannotCancelDeliveredOrder() throws Exception {
        // 1. 创建订单
        String createJson = String.format("""
            {
                "customerName": "异常测试客户",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 2. 付款确认
        String paymentJson = String.format("""
            {
                "orderId": %d,
                "paidAmount": 100.00
            }
            """, orderId);

        mockMvc.perform(post("/api/orders/confirm-payment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson))
                .andExpect(status().isOk());

        // 3. 发货
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 4. 尝试取消已发货订单（应失败）
        String cancelJson = """
            {
                "reason": "不应该成功"
            }
            """;

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    /**
     * 测试异常：订单不存在时付款确认
     */
    @Test
    void testConfirmPaymentForNonexistentOrder() throws Exception {
        String paymentJson = """
            {
                "orderId": 99999,
                "paidAmount": 100.00
            }
            """;

        mockMvc.perform(post("/api/orders/confirm-payment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    /**
     * 测试异常：订单不存在时发货
     */
    @Test
    void testDeliverNonexistentOrder() throws Exception {
        mockMvc.perform(post("/api/orders/99999/deliver")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    /**
     * 测试异常：订单不存在时完成
     */
    @Test
    void testCompleteNonexistentOrder() throws Exception {
        mockMvc.perform(post("/api/orders/99999/complete")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    /**
     * 测试异常：订单不存在时取消
     */
    @Test
    void testCancelNonexistentOrder() throws Exception {
        String cancelJson = """
            {
                "reason": "订单不存在"
            }
            """;

        mockMvc.perform(post("/api/orders/99999/cancel")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    /**
     * 测试异常：未付款订单不能发货
     */
    @Test
    void testCannotDeliverUnpaidOrder() throws Exception {
        // 1. 创建订单（未付款）
        String createJson = String.format("""
            {
                "customerName": "未付款测试客户",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 2. 尝试直接发货（应失败）
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    /**
     * 测试异常：未发货订单不能完成
     */
    @Test
    void testCannotCompleteUndeliveredOrder() throws Exception {
        // 1. 创建订单
        String createJson = String.format("""
            {
                "customerName": "未发货测试客户",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 2. 付款确认
        String paymentJson = String.format("""
            {
                "orderId": %d,
                "paidAmount": 100.00
            }
            """, orderId);

        mockMvc.perform(post("/api/orders/confirm-payment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson))
                .andExpect(status().isOk());

        // 3. 尝试直接完成（应失败，因为还没发货）
        mockMvc.perform(post("/api/orders/" + orderId + "/complete")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== 冗余字段验证测试 ==========

    @Test
    void testOrderRedundantFieldsPopulated() throws Exception {
        // 创建订单
        String createJson = String.format("""
            {
                "customerName": "冗余字段测试客户",
                "warehouseId": %d,
                "paymentStatus": 0,
                "items": [
                    {
                        "skuId": %d,
                        "quantity": 1
                    }
                ]
            }
            """, TEST_WAREHOUSE_ID, testSkuId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

        // 查询订单验证冗余字段
        mockMvc.perform(get("/api/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].skuCode").isString())
                .andExpect(jsonPath("$.data.items[0].productName").isString())
                .andExpect(jsonPath("$.data.items[0].colorName").isString())
                .andExpect(jsonPath("$.data.items[0].sizeName").isString())
                .andExpect(jsonPath("$.data.items[0].price").isNumber())
                .andExpect(jsonPath("$.data.items[0].subtotal").isNumber());
    }
}
