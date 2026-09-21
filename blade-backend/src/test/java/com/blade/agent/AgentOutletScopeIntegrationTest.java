package com.blade.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Series E3：Agent 档口范围在 capabilities / outlets 出口的真实链路验证。
 *
 * <p>覆盖：ALL/ASSIGNED/NONE、缺少 outlets:read 返回 403、缺 Key 返回 401、
 * 禁用档口只读可见但不可用于新建、停用 Key 立即 401、响应不泄露内部 outlet id。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentOutletScopeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentKeyMapper keyMapper;
    @Autowired private AgentKeyOutletMapper agentKeyOutletMapper;
    @Autowired private SalesOutletMapper salesOutletMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long enabledOutletId;
    private Long disabledOutletId;
    private String enabledCode;
    private String disabledCode;

    private String allKey;
    private String noneKey;
    private String assignedKey;
    private String noOutletsScopeKey;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        String suffix = Long.toString(System.nanoTime());
        enabledCode = "E3-EN-" + suffix.substring(Math.max(0, suffix.length() - 8));
        enabledOutletId = insertOutlet(enabledCode, "E3启用档口", 1);
        disabledCode = "E3-DIS-" + suffix.substring(Math.max(0, suffix.length() - 8));
        disabledOutletId = insertOutlet(disabledCode, "E3禁用档口", 0);

        allKey = issueKey("agk_e3_all_" + suffix, "outlets:read", "ALL", null, null);
        noneKey = issueKey("agk_e3_none_" + suffix, "outlets:read", "NONE", null, null);
        assignedKey = issueKey("agk_e3_assigned_" + suffix, "outlets:read", "ASSIGNED",
                List.of(enabledOutletId, disabledOutletId), enabledOutletId);
        noOutletsScopeKey = issueKey("agk_e3_noscope_" + suffix, "orders:read", "ALL", null, null);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void capabilitiesReturnsOutletScopeAndBriefsWithoutInternalIds() throws Exception {
        mockMvc.perform(get("/api/agent/capabilities").header("X-Agent-Key", allKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outletScopeType").value("ALL"))
                .andExpect(jsonPath("$.data.readableOutlets[?(@.code == '" + enabledCode + "')]").isNotEmpty())
                .andExpect(jsonPath("$.data.usableOutlets[?(@.code == '" + enabledCode + "')]").isNotEmpty())
                // 不泄露内部 outlet id / tenant / key id
                .andExpect(jsonPath("$..outletId").doesNotExist())
                .andExpect(jsonPath("$..tenantId").doesNotExist())
                .andExpect(jsonPath("$..keyId").doesNotExist());
    }

    @Test
    void capabilitiesReflectsOutletDisableImmediately() throws Exception {
        TenantContext.setTenantId(1L);
        SalesOutlet disabled = new SalesOutlet();
        disabled.setId(enabledOutletId);
        disabled.setStatus(0);
        salesOutletMapper.updateById(disabled);

        mockMvc.perform(get("/api/agent/capabilities").header("X-Agent-Key", assignedKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outletScopeType").value("ASSIGNED"))
                // 禁用档口仍可读历史，但不再可用于新建
                .andExpect(jsonPath("$.data.usableOutlets.length()").value(0))
                .andExpect(jsonPath("$.data.readableOutlets[*].code")
                        .value(org.hamcrest.Matchers.hasItem(enabledCode)));
    }

    @Test
    void capabilitiesRejectsDisabledKeyImmediately() throws Exception {
        TenantContext.setTenantId(1L);
        AgentKey key = keyMapper.selectOne(Wrappers.<AgentKey>lambdaQuery()
                .eq(AgentKey::getKeyPrefix, allKey.substring(0, allKey.indexOf('.'))));
        key.setStatus(AgentKey.STATUS_DISABLED);
        key.setDisabledTime(LocalDateTime.now());
        keyMapper.updateById(key);

        mockMvc.perform(get("/api/agent/capabilities").header("X-Agent-Key", allKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void outletsRequiresOutletsReadScope() throws Exception {
        mockMvc.perform(get("/api/agent/outlets").header("X-Agent-Key", noOutletsScopeKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void outletsRejectsMissingOrInvalidKey() throws Exception {
        mockMvc.perform(get("/api/agent/outlets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void outletsAllReturnsTenantEnabledAndExcludesDisabled() throws Exception {
        mockMvc.perform(get("/api/agent/outlets").header("X-Agent-Key", allKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outletScopeType").value("ALL"))
                .andExpect(jsonPath("$.data.items[?(@.code == '" + enabledCode + "')]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.code == '" + disabledCode + "')]").isEmpty());
    }

    @Test
    void outletsNoneReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/agent/outlets").header("X-Agent-Key", noneKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outletScopeType").value("NONE"))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.defaultOutletCode").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void outletsAssignedReturnsOnlyBoundEnabledWithDefault() throws Exception {
        mockMvc.perform(get("/api/agent/outlets").header("X-Agent-Key", assignedKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outletScopeType").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.defaultOutletCode").value(enabledCode))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(enabledCode))
                .andExpect(jsonPath("$.data.items[0].defaultOutlet").value(true));
    }

    // ==================== helpers ====================

    private Long insertOutlet(String code, String name, int status) {
        SalesOutlet outlet = new SalesOutlet();
        outlet.setTenantId(1L);
        outlet.setOutletCode(code);
        outlet.setOutletName(name);
        outlet.setOutletType("STORE");
        outlet.setStatus(status);
        outlet.setSort(0);
        outlet.setDeleted(0);
        salesOutletMapper.insert(outlet);
        return outlet.getId();
    }

    private String issueKey(String prefix, String scopes, String outletScopeType,
                            List<Long> outletIds, Long defaultOutletId) {
        String secret = "secret-" + prefix;
        AgentKey key = new AgentKey();
        key.setTenantId(1L);
        key.setName("E3 outlet scope test");
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
