package com.blade.agent;

import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 最后 P1 批次：Agent 批量草稿授权语义（真实登录/DB）。
 *
 * <p>请求级 401/403（缺 scope、档口越权/不可用、无默认档口）必须在写入前 fail-fast，
 * 映射为真实 HTTP 403 且整批零写入；普通 400/404/409 保留 per-item ERROR 继续。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentBatchAuthorizationSemanticsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgentKeyMapper keyMapper;
    @Autowired private AgentKeyOutletMapper agentKeyOutletMapper;
    @Autowired private SalesOutletMapper salesOutletMapper;

    private String assignedRawKey;
    private String boundCode;
    private String unboundCode;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        String suffix = Long.toString(System.nanoTime()).substring(8);
        boundCode = "BATCH-A-" + suffix;
        unboundCode = "BATCH-B-" + suffix;
        Long boundId = insertOutlet(boundCode, "批量绑定档口");
        insertOutlet(unboundCode, "批量未绑档口");

        String prefix = "agk_batch_" + suffix;
        String secret = "secret-" + prefix;
        AgentKey key = new AgentKey();
        key.setTenantId(1L);
        key.setName("batch auth semantics");
        key.setKeyPrefix(prefix);
        key.setKeyHash(passwordEncoder.encode(secret));
        key.setScopes("orders:write,outlets:read");
        key.setOutletScopeType("ASSIGNED");
        key.setStatus(AgentKey.STATUS_ACTIVE);
        key.setExpiresTime(LocalDateTime.now().plusDays(1));
        keyMapper.insert(key);

        AgentKeyOutlet binding = new AgentKeyOutlet();
        binding.setTenantId(1L);
        binding.setAgentKeyId(key.getId());
        binding.setOutletId(boundId);
        binding.setIsDefault(1);
        binding.setStatus(1);
        agentKeyOutletMapper.insert(binding);
        assignedRawKey = prefix + "." + secret;
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mixedBatchWithOneOutOfScopeCodeRejectsWholeBatchWith403AndZeroWrites() throws Exception {
        String r1 = ref("MIX-OK");
        String r2 = ref("MIX-DENY");
        String body = batch(order(r1, boundCode, false), order(r2, unboundCode, false));

        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedRawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEqualsZero(r1, r2);
    }

    @Test
    void allOutOfScopeCodesRejectWholeBatchWith403() throws Exception {
        String r1 = ref("ALL-DENY-1");
        String r2 = ref("ALL-DENY-2");
        String body = batch(order(r1, unboundCode, false), order(r2, unboundCode, false));

        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedRawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEqualsZero(r1, r2);
    }

    @Test
    void allValidCodesSucceedAsBatch() throws Exception {
        String r1 = ref("OK-1");
        String r2 = ref("OK-2");
        String body = batch(order(r1, boundCode, false), order(r2, boundCode, false));

        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedRawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].status",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.is("ERROR"))))
                .andExpect(jsonPath("$.data.results[1].status",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.is("ERROR"))));

        assertEquals(1, countByRef(r1));
        assertEquals(1, countByRef(r2));
    }

    @Test
    void validPlusOrdinaryInputErrorKeepsPerItemResults() throws Exception {
        String okRef = ref("ORD-OK");
        String badRef = ref("ORD-BAD");
        // 第二条显式传内部 sourceOutletId：普通输入错误 400，保留 item ERROR；第一条仍成功
        String body = batch(order(okRef, boundCode, false), order(badRef, null, true));

        mockMvc.perform(post("/api/agent/order-drafts/batch").header("X-Agent-Key", assignedRawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].status",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.is("ERROR"))))
                .andExpect(jsonPath("$.data.results[1].status").value("ERROR"));

        assertEquals(1, countByRef(okRef));
        assertEquals(0, countByRef(badRef));
    }

    // ==================== helpers ====================

    private Long insertOutlet(String code, String name) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                + "VALUES(1,?,?,'STORE',1,0,0,0)", code, name);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=1 AND outlet_code=?", Long.class, code);
    }

    private String ref(String tag) {
        return tag + "-" + System.nanoTime();
    }

    private String order(String ref, String code, boolean explicitInternalId) {
        String outletPart = explicitInternalId
                ? "\"sourceOutletId\":1,"
                : (code == null ? "" : "\"sourceOutletCode\":\"" + code + "\",");
        return "{\"externalRefNo\":\"" + ref + "\",\"sourceBatchNo\":\"B\",\"sourceOrderNo\":\"1\","
                + outletPart
                + "\"items\":[{\"sourceRowNo\":1,\"rawDescription\":\"x\",\"quantity\":1,\"salePrice\":10}]}";
    }

    private String batch(String... orders) {
        return "{\"orders\":[" + String.join(",", orders) + "]}";
    }

    private void assertEqualsZero(String... refs) {
        for (String ref : refs) {
            assertEquals(0, countByRef(ref), "越权整批拒绝不得写入: " + ref);
        }
    }

    private int countByRef(String ref) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_draft WHERE external_ref_no = ?", Integer.class, ref);
        return count == null ? 0 : count;
    }
}
