package com.blade.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.entity.Customer;
import com.blade.customer.entity.CustomerOperationLog;
import com.blade.customer.entity.CustomerPhone;
import com.blade.customer.mapper.CustomerMapper;
import com.blade.customer.mapper.CustomerOperationLogMapper;
import com.blade.customer.mapper.CustomerPhoneMapper;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentDataAccessIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AgentKeyMapper keyMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private ProductSkuMapper productSkuMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private CustomerMapper customerMapper;
    @Autowired private CustomerPhoneMapper customerPhoneMapper;
    @Autowired private CustomerOperationLogMapper customerOperationLogMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String rawKey;
    private String readOnlyRawKey;
    private String customerReadOnlyRawKey;
    private String customerCreateOnlyRawKey;
    private String productCreateOnlyRawKey;
    private String assignedOtherOutletRawKey;
    private Long fullAccessKeyId;
    private String seededProductCode;
    private String seededOrderNo;
    private Long seededOrderId;
    private Long seededOutletId;
    private String seededOutletCode;
    private Long otherOutletId;
    private String otherOutletCode;
    @Autowired private com.blade.outlet.mapper.SalesOutletMapper salesOutletMapper;
    @Autowired private AgentKeyOutletMapper agentKeyOutletMapper;
    private Long seededCustomerId;
    private String seededCustomerName;
    private String seededCustomerPhone;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        String suffix = String.valueOf(System.nanoTime());
        String fullAccessPrefix = "agk_data_" + suffix;
        rawKey = issueKey(fullAccessPrefix,
                "products:read,orders:read,products:create,products:cost:write,customers:read,customers:create");
        fullAccessKeyId = keyMapper.selectOne(Wrappers.<AgentKey>lambdaQuery()
                .eq(AgentKey::getKeyPrefix, fullAccessPrefix)).getId();
        readOnlyRawKey = issueKey("agk_read_" + suffix, "products:read");
        customerReadOnlyRawKey = issueKey("agk_customer_read_" + suffix, "customers:read");
        customerCreateOnlyRawKey = issueKey("agk_customer_create_" + suffix, "customers:create");
        productCreateOnlyRawKey = issueKey("agk_product_create_" + suffix, "products:create");

        seededProductCode = "AGENT-READ-" + suffix;
        Product product = new Product();
        product.setProductCode(seededProductCode);
        product.setName("Agent read product");
        product.setWholesalePrice(new BigDecimal("20.00"));
        product.setCostPrice(new BigDecimal("11.00"));
        product.setStatus(1);
        product.setTenantId(1L);
        product.setDeleted(0);
        productMapper.insert(product);

        seededOrderNo = "AOR" + suffix;
        Order order = new Order();
        order.setOrderNo(seededOrderNo);
        order.setSourceOutletId(seedAgentOutlet());
        order.setOrderDate(LocalDate.now());
        order.setCustomerName("Agent customer");
        order.setCustomerPhone("13800000000");
        order.setCustomerAddress("secret address");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setGrossReceivedAmount(new BigDecimal("60.00"));
        order.setNetReceivedAmount(new BigDecimal("60.00"));
        order.setBalanceAmount(new BigDecimal("40.00"));
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setTotalCostAmount(new BigDecimal("50.00"));
        order.setGrossProfit(new BigDecimal("50.00"));
        order.setCollectionStatus("PARTIAL");
        order.setFulfillmentStatus("CONFIRMED");
        order.setFulfillmentMode("UNDECIDED");
        order.setOrderType("SPOT");
        order.setStatus(0);
        order.setPaymentStatus(1);
        order.setNeedDelivery(0);
        order.setIsDelivered(0);
        order.setVersion(0);
        order.setTenantId(1L);
        order.setDeleted(0);
        orderMapper.insert(order);
        seededOrderId = order.getId();

        // E3：绑定到另一个档口的 Agent，用于验证不能通过订单/草稿/能力/选项出口推断种子档口数据
        otherOutletCode = "AG-OTHER-" + Long.toString(System.nanoTime()).substring(10);
        otherOutletId = insertOutlet(otherOutletCode, "Agent 其他档口");
        assignedOtherOutletRawKey = issueAssignedKey("agk_e3_assigned_" + suffix,
                "orders:read,orders:write,outlets:read", otherOutletId);

        seededCustomerPhone = "139" + suffix.substring(Math.max(0, suffix.length() - 8));
        seededCustomerName = "Agent客户-" + suffix;
        Customer customer = new Customer();
        customer.setName(seededCustomerName);
        customer.setAddress("客户敏感地址");
        customer.setRemark("客户敏感备注");
        customer.setCountryCode("+86");
        customer.setCountryName("中国");
        customer.setTenantId(1L);
        customer.setDeleted(0);
        customerMapper.insert(customer);
        seededCustomerId = customer.getId();

        CustomerPhone customerPhone = new CustomerPhone();
        customerPhone.setCustomerId(seededCustomerId);
        customerPhone.setPhone(seededCustomerPhone);
        customerPhone.setIsPrimary(1);
        customerPhone.setTenantId(1L);
        customerPhone.setDeleted(0);
        customerPhoneMapper.insert(customerPhone);
        TenantContext.clear();
    }

    private String issueKey(String prefix, String scopes) {
        String secret = "secret-" + prefix;
        AgentKey key = new AgentKey();
        key.setTenantId(1L);
        key.setName("Agent data integration test");
        key.setKeyPrefix(prefix);
        key.setKeyHash(passwordEncoder.encode(secret));
        key.setScopes(scopes);
        // Series C：给测试 Key 全部档口范围，使订单读取按档口维度放行
        key.setOutletScopeType("ALL");
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(LocalDateTime.now().plusDays(1));
        keyMapper.insert(key);
        return prefix + "." + secret;
    }

    private Long seedAgentOutlet() {
        if (seededOutletId != null) return seededOutletId;
        seededOutletCode = "AG-OUT-" + Long.toString(System.nanoTime()).substring(10);
        seededOutletId = insertOutlet(seededOutletCode, "Agent 测试档口");
        return seededOutletId;
    }

    private Long insertOutlet(String code, String name) {
        com.blade.outlet.entity.SalesOutlet outlet = new com.blade.outlet.entity.SalesOutlet();
        outlet.setTenantId(1L);
        outlet.setOutletCode(code);
        outlet.setOutletName(name);
        outlet.setOutletType("STORE");
        outlet.setStatus(1);
        outlet.setSort(0);
        outlet.setDeleted(0);
        salesOutletMapper.insert(outlet);
        return outlet.getId();
    }

    /** 签发 ASSIGNED 档口范围的 Key，并写入绑定。 */
    private String issueAssignedKey(String prefix, String scopes, Long boundOutletId) {
        String secret = "secret-" + prefix;
        AgentKey key = new AgentKey();
        key.setTenantId(1L);
        key.setName("Agent assigned outlet test");
        key.setKeyPrefix(prefix);
        key.setKeyHash(passwordEncoder.encode(secret));
        key.setScopes(scopes);
        key.setOutletScopeType("ASSIGNED");
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(LocalDateTime.now().plusDays(1));
        keyMapper.insert(key);
        AgentKeyOutlet binding = new AgentKeyOutlet();
        binding.setTenantId(1L);
        binding.setAgentKeyId(key.getId());
        binding.setOutletId(boundOutletId);
        binding.setIsDefault(1);
        binding.setStatus(1);
        agentKeyOutletMapper.insert(binding);
        return prefix + "." + secret;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void readsProductsAndOrdersWithoutCostProfitOrCustomerPii() throws Exception {
        mockMvc.perform(get("/api/agent/products").param("keyword", seededProductCode).header("X-Agent-Key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].productCode").value(seededProductCode))
                .andExpect(jsonPath("$.data.records[0].wholesalePrice").value(20.00))
                .andExpect(jsonPath("$..costPrice").doesNotExist());

        mockMvc.perform(get("/api/agent/orders").param("orderNo", seededOrderNo).header("X-Agent-Key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(seededOrderNo))
                .andExpect(jsonPath("$.data.records[0].totalAmount").value(100.00))
                .andExpect(jsonPath("$.data.records[0].netReceivedAmount").value(60.00))
                .andExpect(jsonPath("$..customerPhone").doesNotExist())
                .andExpect(jsonPath("$..customerAddress").doesNotExist())
                .andExpect(jsonPath("$..costPrice").doesNotExist())
                .andExpect(jsonPath("$..grossProfit").doesNotExist());
    }

    @Test
    void capabilitiesReturnsServerScopesWithoutRequiringAnExtraBusinessScope() throws Exception {
        mockMvc.perform(get("/api/agent/capabilities").header("X-Agent-Key", readOnlyRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keyPrefix").exists())
                .andExpect(jsonPath("$.data.name").value("Agent data integration test"))
                .andExpect(jsonPath("$.data.scopes.length()").value(1))
                .andExpect(jsonPath("$.data.scopes[0]").value("products:read"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.serverTime").exists())
                .andExpect(jsonPath("$..tenantId").doesNotExist())
                .andExpect(jsonPath("$..keyId").doesNotExist());
    }

    @Test
    void capabilitiesRejectsRequestsWithoutAnAgentKey() throws Exception {
        mockMvc.perform(get("/api/agent/capabilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void createIsAdditiveAndDuplicateRetryDoesNotOverwrite() throws Exception {
        String code = "AGENT-CREATE-" + System.nanoTime();
        String body = "{\"productCode\":\"" + code + "\",\"name\":\"Agent create\",\"wholesalePrice\":33.5}";
        mockMvc.perform(post("/api/agent/products").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("CREATED"));

        mockMvc.perform(post("/api/agent/products").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + code + "\",\"name\":\"must not overwrite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DUPLICATE"));
    }

    @Test
    void productCostWriteRequiresIndependentScopeAndAppliesToGeneratedSkus() throws Exception {
        String deniedCode = "COST-D-" + String.valueOf(System.nanoTime()).substring(5);
        mockMvc.perform(post("/api/agent/products").header("X-Agent-Key", productCreateOnlyRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + deniedCode
                                + "\",\"name\":\"cost denied\",\"costPrice\":18.5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        String createdCode = "COST-" + String.valueOf(System.nanoTime()).substring(5);
        mockMvc.perform(post("/api/agent/products").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + createdCode
                                + "\",\"name\":\"uniform cost\",\"costPrice\":18.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("CREATED"))
                .andExpect(jsonPath("$.data.appliedCostPrice").value(18.5));

        TenantContext.setTenantId(1L);
        Product created = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getProductCode, createdCode));
        assertEquals(0, new BigDecimal("18.50").compareTo(created.getCostPrice()));
        var skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, created.getId()));
        org.junit.jupiter.api.Assertions.assertFalse(skus.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(skus.stream()
                .allMatch(sku -> new BigDecimal("18.50").compareTo(sku.getCostPrice()) == 0));
    }

    @Test
    void readOnlyScopeCannotCreateProduct() throws Exception {
        String code = "AGENT-DENIED-" + System.nanoTime();
        mockMvc.perform(post("/api/agent/products").header("X-Agent-Key", readOnlyRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + code + "\",\"name\":\"must be denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void customerScopeReadsSensitiveCustomerListAndDetail() throws Exception {
        mockMvc.perform(get("/api/agent/customers").param("keyword", seededCustomerPhone)
                        .header("X-Agent-Key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(seededCustomerId))
                .andExpect(jsonPath("$.data.records[0].phones[0]").value(seededCustomerPhone))
                .andExpect(jsonPath("$.data.records[0].address").value("客户敏感地址"))
                .andExpect(jsonPath("$.data.records[0].remark").value("客户敏感备注"))
                .andExpect(jsonPath("$..tenantId").doesNotExist())
                .andExpect(jsonPath("$..createdByAgentKeyId").doesNotExist());

        mockMvc.perform(get("/api/agent/customers/{id}", seededCustomerId)
                        .header("X-Agent-Key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(seededCustomerName))
                .andExpect(jsonPath("$.data.phones[0]").value(seededCustomerPhone));
    }

    @Test
    void customerCreateIsAdditiveIdempotentAndAttributedToAgentKey() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String phone = "137" + suffix.substring(Math.max(0, suffix.length() - 8));
        String name = "Agent新增客户-" + suffix;
        String body = "{\"name\":\"" + name + "\",\"phones\":[\"" + phone
                + "\"],\"address\":\"新增地址\",\"remark\":\"新增备注\",\"countryCode\":\"+86\"}";

        mockMvc.perform(post("/api/agent/customers").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("CREATED"));

        mockMvc.perform(post("/api/agent/customers").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DUPLICATE"))
                .andExpect(jsonPath("$.data.duplicatePhone").value(phone));

        TenantContext.setTenantId(1L);
        Customer created = customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getName, name));
        assertEquals(fullAccessKeyId, created.getCreatedByAgentKeyId());
        assertNull(created.getCreateBy());
        CustomerOperationLog log = customerOperationLogMapper.selectOne(
                Wrappers.<CustomerOperationLog>lambdaQuery()
                        .eq(CustomerOperationLog::getCustomerId, created.getId())
                        .eq(CustomerOperationLog::getOperation, "CREATE"));
        assertEquals(fullAccessKeyId, log.getAgentKeyId());
        assertNull(log.getOperatorId());
    }

    @Test
    void customerReadScopeCannotCreateCustomer() throws Exception {
        mockMvc.perform(get("/api/agent/customers").header("X-Agent-Key", readOnlyRawKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/agent/customers").header("X-Agent-Key", customerReadOnlyRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"禁止新增\",\"phones\":[\"13600000000\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/agent/customers").header("X-Agent-Key", customerCreateOnlyRawKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/agent/customers").header("X-Agent-Key", customerCreateOnlyRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重复探测\",\"phones\":[\"" + seededCustomerPhone + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DUPLICATE"))
                .andExpect(jsonPath("$.data.customerId").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value(nullValue()));
    }

    /** TEST-OUTLET-003：Agent A 档口无法通过 orders / draft / capabilities / outlets 任一出口推断 B 档口数据。 */
    @Test
    void assignedOutletCannotReachOtherOutletThroughAnyAgentOutlet() throws Exception {
        // 订单列表不含 B 档口订单
        mockMvc.perform(get("/api/agent/orders").param("orderNo", seededOrderNo)
                        .header("X-Agent-Key", assignedOtherOutletRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        // 订单详情按 ID 直接访问 B 档口订单 -> 业务码 403
        mockMvc.perform(get("/api/agent/orders/{id}", seededOrderId)
                        .header("X-Agent-Key", assignedOtherOutletRawKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // capabilities 不泄露 B 档口 code
        mockMvc.perform(get("/api/agent/capabilities").header("X-Agent-Key", assignedOtherOutletRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outletScopeType").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.usableOutlets[?(@.code == '" + seededOutletCode + "')]").isEmpty())
                .andExpect(jsonPath("$.data.readableOutlets[?(@.code == '" + seededOutletCode + "')]").isEmpty());

        // outlets 只返回绑定的其他档口
        mockMvc.perform(get("/api/agent/outlets").header("X-Agent-Key", assignedOtherOutletRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(otherOutletCode));

        // 草稿使用 B 档口 code -> 请求级档口越权，整批真实 HTTP 403 且零写入
        String codeBody = "{\"orders\":[{\"externalRefNo\":\"E3-B-" + System.nanoTime()
                + "\",\"sourceBatchNo\":\"E3B\",\"sourceOrderNo\":\"E3B-1\",\"sourceOutletCode\":\""
                + seededOutletCode + "\",\"items\":[{\"sourceRowNo\":1,\"rawDescription\":\"x\",\"quantity\":1,"
                + "\"salePrice\":10}]}]}";
        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedOtherOutletRawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(codeBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 草稿显式内部 ID -> 普通输入错误，保留单条 ERROR（HTTP 200）
        String idBody = "{\"orders\":[{\"externalRefNo\":\"E3-ID-" + System.nanoTime()
                + "\",\"sourceBatchNo\":\"E3I\",\"sourceOrderNo\":\"E3I-1\",\"sourceOutletId\":" + seededOutletId
                + ",\"items\":[{\"sourceRowNo\":1,\"rawDescription\":\"x\",\"quantity\":1,"
                + "\"salePrice\":10}]}]}";
        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedOtherOutletRawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(idBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].status").value("ERROR"));
    }
}
