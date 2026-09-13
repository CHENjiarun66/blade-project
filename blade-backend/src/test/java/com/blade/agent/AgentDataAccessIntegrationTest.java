package com.blade.agent;

import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.product.entity.Product;
import com.blade.product.mapper.ProductMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentDataAccessIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AgentKeyMapper keyMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String rawKey;
    private String readOnlyRawKey;
    private String seededProductCode;
    private String seededOrderNo;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        String suffix = String.valueOf(System.nanoTime());
        rawKey = issueKey("agk_data_" + suffix, "products:read,orders:read,products:create");
        readOnlyRawKey = issueKey("agk_read_" + suffix, "products:read");

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
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(LocalDateTime.now().plusDays(1));
        keyMapper.insert(key);
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
    void readOnlyScopeCannotCreateProduct() throws Exception {
        String code = "AGENT-DENIED-" + System.nanoTime();
        mockMvc.perform(post("/api/agent/products").header("X-Agent-Key", readOnlyRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + code + "\",\"name\":\"must be denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
