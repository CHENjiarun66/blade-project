package com.blade.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Series E3 整改 P0-2：Agent 查询只接受稳定 sourceOutletCode / sourceOutletCodes。
 *
 * <p>覆盖：内部 ID 400；A code 成功并返回 sourceOutletCode；未授权/跨租户 code 403；
 * ASSIGNED 含禁用档口时历史读取可用但新建拒绝；NONE code 403；分析接口 code 解析与过滤。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentOutletCodeQueryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentKeyMapper keyMapper;
    @Autowired private AgentKeyOutletMapper agentKeyOutletMapper;
    @Autowired private SalesOutletMapper salesOutletMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long enabledOutletId;
    private String enabledCode;
    private Long disabledOutletId;
    private String disabledCode;
    private Long otherOutletId;
    private String otherCode;
    private String crossTenantCode;
    private String enabledOrderNo;

    private String assignedARawKey;
    private String assignedBRawKey;
    private String noneRawKey;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        String suffix = Long.toString(System.nanoTime());
        enabledCode = "E3C-A-" + suffix.substring(Math.max(0, suffix.length() - 8));
        enabledOutletId = insertOutlet(1L, enabledCode, "E3可用", 1);
        disabledCode = "E3C-D-" + suffix.substring(Math.max(0, suffix.length() - 8));
        disabledOutletId = insertOutlet(1L, disabledCode, "E3禁用", 0);
        otherCode = "E3C-B-" + suffix.substring(Math.max(0, suffix.length() - 8));
        otherOutletId = insertOutlet(1L, otherCode, "E3未绑定", 1);
        // 跨租户档口：显式切到 tenant 2 插入，随后恢复 tenant 1
        crossTenantCode = "E3C-X-" + suffix.substring(Math.max(0, suffix.length() - 8));
        TenantContext.setTenantId(2L);
        insertOutlet(2L, crossTenantCode, "E3跨租户", 1);
        TenantContext.setTenantId(1L);

        enabledOrderNo = "E3C-ORD-" + suffix;
        insertOrder(enabledOutletId, enabledOrderNo);
        insertOrder(otherOutletId, "E3C-OTH-" + suffix);
        assignedARawKey = issueKey("agk_e3c_a_" + suffix,
                "orders:read,orders:write,analytics:read,outlets:read", "ASSIGNED",
                List.of(enabledOutletId, disabledOutletId), enabledOutletId);
        assignedBRawKey = issueKey("agk_e3c_b_" + suffix,
                "orders:read,orders:write,analytics:read,outlets:read", "ASSIGNED",
                List.of(otherOutletId), otherOutletId);
        noneRawKey = issueKey("agk_e3c_n_" + suffix, "orders:read,analytics:read", "NONE", null, null);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== Agent orders ====================

    @Test
    void ordersAcceptsReadableCodeAndReturnsSourceOutletCode() throws Exception {
        mockMvc.perform(get("/api/agent/orders").param("sourceOutletCode", enabledCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 只统计/返回 A 档口订单，B 档口订单不出现
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(enabledOrderNo))
                .andExpect(jsonPath("$.data.records[0].sourceOutletCode").value(enabledCode));
    }

    @Test
    void ordersRejectsInternalOutletId() throws Exception {
        mockMvc.perform(get("/api/agent/orders").param("sourceOutletId", String.valueOf(enabledOutletId))
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void ordersRejectsUnboundUnknownAndNoneCode() throws Exception {
        // ASSIGNED-A 访问未绑定的 B code
        mockMvc.perform(get("/api/agent/orders").param("sourceOutletCode", otherCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/agent/orders").param("sourceOutletCode", "NO-SUCH-CODE")
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/agent/orders").param("sourceOutletCode", enabledCode)
                        .header("X-Agent-Key", noneRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ordersAllowsDisabledCodeForHistoricalRead() throws Exception {
        mockMvc.perform(get("/api/agent/orders").param("sourceOutletCode", disabledCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void ordersAndAnalyticsRejectCrossTenantCode() throws Exception {
        mockMvc.perform(get("/api/agent/orders").param("sourceOutletCode", crossTenantCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletCodes", crossTenantCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void draftCreateStillRejectsDisabledCode() throws Exception {
        String body = "{\"orders\":[{\"externalRefNo\":\"E3C-D-" + System.nanoTime()
                + "\",\"sourceBatchNo\":\"E3C\",\"sourceOrderNo\":\"E3C-1\",\"sourceOutletCode\":\""
                + disabledCode + "\",\"items\":[{\"sourceRowNo\":1,\"rawDescription\":\"x\",\"quantity\":1,"
                + "\"salePrice\":10}]}]}";
        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedARawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].status").value("ERROR"));
    }

    // ==================== Agent analytics ====================

    @Test
    void analyticsRejectsInternalOutletIds() throws Exception {
        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletIds", String.valueOf(enabledOutletId))
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/agent/analytics/sku-mix")
                        .param("sourceOutletIds", String.valueOf(enabledOutletId))
                        .param("productName", "x")
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void analyticsResolvesCodesAndRejectsUnbound() throws Exception {
        // 已停用但 readable：历史分析允许
        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletCodes", disabledCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 逗号分隔多个 readable code（含重复）
        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletCodes", enabledCode + "," + disabledCode + "," + enabledCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 混入未绑定 B -> 403
        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletCodes", enabledCode + "," + otherCode)
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // sku-mix 未绑定 -> 403
        mockMvc.perform(get("/api/agent/analytics/sku-mix")
                        .param("sourceOutletCodes", otherCode)
                        .param("productName", "x")
                        .header("X-Agent-Key", assignedARawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // NONE Key -> 403
        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletCodes", enabledCode)
                        .header("X-Agent-Key", noneRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void analyticsAssignedBCannotSeeACode() throws Exception {
        mockMvc.perform(get("/api/agent/analytics/style-trends")
                        .param("sourceOutletCodes", enabledCode)
                        .header("X-Agent-Key", assignedBRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== helpers ====================

    private Long insertOutlet(long tenantId, String code, String name, int status) {
        SalesOutlet outlet = new SalesOutlet();
        outlet.setTenantId(tenantId);
        outlet.setOutletCode(code);
        outlet.setOutletName(name);
        outlet.setOutletType("STORE");
        outlet.setStatus(status);
        outlet.setSort(0);
        outlet.setDeleted(0);
        salesOutletMapper.insert(outlet);
        return outlet.getId();
    }

    private void insertOrder(Long outletId, String orderNo) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setSourceOutletId(outletId);
        order.setOrderDate(LocalDate.now());
        order.setCustomerName("E3 code customer");
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setGrossReceivedAmount(BigDecimal.ZERO);
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setBalanceAmount(new BigDecimal("10.00"));
        order.setTotalCostAmount(BigDecimal.ZERO);
        order.setGrossProfit(BigDecimal.ZERO);
        order.setCollectionStatus("UNPAID");
        order.setFulfillmentStatus("CONFIRMED");
        order.setFulfillmentMode("UNDECIDED");
        order.setOrderType("SPOT");
        order.setStatus(0);
        order.setPaymentStatus(0);
        order.setNeedDelivery(0);
        order.setIsDelivered(0);
        order.setVersion(0);
        order.setTenantId(1L);
        order.setDeleted(0);
        orderMapper.insert(order);
    }

    private String issueKey(String prefix, String scopes, String outletScopeType,
                            List<Long> outletIds, Long defaultOutletId) {
        String secret = "secret-" + prefix;
        AgentKey key = new AgentKey();
        key.setTenantId(1L);
        key.setName("E3 code query test");
        key.setKeyPrefix(prefix);
        key.setKeyHash(passwordEncoder.encode(secret));
        key.setScopes(scopes);
        key.setOutletScopeType(outletScopeType);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(LocalDateTime.now().plusDays(1));
        keyMapper.insert(key);
        if (outletIds != null) {
            for (Long outletId : outletIds) {
                AgentKeyOutlet binding = new AgentKeyOutlet();
                binding.setTenantId(1L);
                binding.setAgentKeyId(key.getId());
                binding.setOutletId(outletId);
                binding.setIsDefault(outletId.equals(defaultOutletId) ? 1 : 0);
                binding.setStatus(1);
                agentKeyOutletMapper.insert(binding);
            }
        }
        return prefix + "." + secret;
    }
}
