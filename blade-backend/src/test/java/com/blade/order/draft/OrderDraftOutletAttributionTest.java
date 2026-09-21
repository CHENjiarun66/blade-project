package com.blade.order.draft;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.AgentOrderDraftService;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.entity.SysUserOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Series C 补漏：草稿“新建”统一接入档口范围（BE-OUTLET-006）。
 *
 * <p>真实隔离库（无 Mock）覆盖：</p>
 * <ul>
 *   <li>Agent：NONE 拒绝且不落库；ASSIGNED 单档口默认自动归属；同 Key 重试幂等；</li>
 *   <li>Agent：显式 code 成功 / 越权·禁用·不存在 code 拒绝 / 传 ID 拒绝；</li>
 *   <li>人工：显式 ID、默认档口回填、无默认拒绝、unassigned 允许写空；</li>
 *   <li>更新：省略保留、改到可用档口刷新快照、不可用拒绝；</li>
 *   <li>View/Summary 暴露 sourceOutletId/sourceOutletCode/sourceShop；</li>
 *   <li>历史 Agent 空档口草稿重试不升级为可读，且不泄漏内部 ID。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderDraftOutletAttributionTest {

    @Autowired private OrderDraftService draftService;
    @Autowired private AgentOrderDraftService agentDraftService;
    @Autowired private OrderDraftMapper draftMapper;
    @Autowired private SalesOutletMapper salesOutletMapper;
    @Autowired private AgentKeyMapper agentKeyMapper;
    @Autowired private AgentKeyOutletMapper agentKeyOutletMapper;
    @Autowired private SysUserOutletMapper sysUserOutletMapper;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ==================== 数据/上下文 ====================

    private SalesOutlet seedOutlet(String name, int status, int isDefault) {
        SalesOutlet outlet = new SalesOutlet();
        outlet.setTenantId(1L);
        outlet.setOutletCode("ATTR-" + UUID.randomUUID().toString().substring(0, 8));
        outlet.setOutletName(name);
        outlet.setOutletType("STORE");
        outlet.setStatus(status);
        outlet.setIsTenantDefault(isDefault);
        outlet.setSort(0);
        outlet.setDeleted(0);
        salesOutletMapper.insert(outlet);
        return outlet;
    }

    private AgentKey seedAgentKey(String scopeType) {
        AgentKey key = new AgentKey();
        key.setTenantId(1L);
        key.setName("归属测试Key");
        key.setKeyPrefix("attr_" + UUID.randomUUID().toString().substring(0, 8));
        key.setKeyHash("hash-" + UUID.randomUUID());
        key.setScopes("orders:write");
        key.setOutletScopeType(scopeType);
        key.setStatus(AgentKey.STATUS_ACTIVE);
        agentKeyMapper.insert(key);
        return key;
    }

    private void bindAgent(AgentKey key, SalesOutlet outlet, int isDefault) {
        AgentKeyOutlet binding = new AgentKeyOutlet();
        binding.setTenantId(1L);
        binding.setAgentKeyId(key.getId());
        binding.setOutletId(outlet.getId());
        binding.setIsDefault(isDefault);
        binding.setStatus(1);
        agentKeyOutletMapper.insert(binding);
    }

    private void agentContext(AgentKey key) {
        TenantContext.setTenantId(1L);
        AgentPrincipal principal = AgentPrincipal.from(key);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new TestingAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    private void userContext(String... extraAuthorities) {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(1L);
        principal.setUsername("admin");
        java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("data:outlet:all"));
        authorities.add(new SimpleGrantedAuthority("data:order:peopleAll"));
        for (String extra : extraAuthorities) {
            authorities.add(new SimpleGrantedAuthority(extra));
        }
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new TestingAuthenticationToken(principal, null, authorities)));
    }

    private void bindUserTo(Long userId, SalesOutlet outlet, int isDefault) {
        SysUserOutlet binding = new SysUserOutlet();
        binding.setTenantId(1L);
        binding.setUserId(userId);
        binding.setOutletId(outlet.getId());
        binding.setIsDefault(isDefault);
        binding.setStatus(1);
        binding.setDeleted(0);
        sysUserOutletMapper.insert(binding);
    }

    private OrderDraftDTO.SaveRequest request(String ref) {
        OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
        request.setExternalRefNo(ref);
        request.setSourceBatchNo("ATTR");
        request.setSourceOrderNo(ref);
        request.setCustomerName("归属测试客户");
        OrderDraftDTO.Item item = new OrderDraftDTO.Item();
        item.setQuantity(1);
        item.setSalePrice(new BigDecimal("10.00"));
        item.setPaperAmount(new BigDecimal("10.00"));
        request.setItems(List.of(item));
        return request;
    }

    private OrderDraftDTO.BatchResult createAgent(AgentKey key, OrderDraftDTO.SaveRequest request) {
        OrderDraftDTO.BatchRequest batch = new OrderDraftDTO.BatchRequest();
        batch.setOrders(List.of(request));
        OrderDraftDTO.BatchResponse response = agentDraftService.createBatch(batch, AgentPrincipal.from(key));
        assertEquals(1, response.getResults().size());
        return response.getResults().get(0);
    }

    private long draftCountByRef(String ref) {
        return draftMapper.selectCount(new LambdaQueryWrapper<OrderDraft>()
                .eq(OrderDraft::getExternalRefNo, ref));
    }

    // ==================== Agent：NONE / 单档口默认 / 幂等 ====================

    @Test
    void agentNoneScope_createRejectedWithoutInsert() {
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_NONE);
        agentContext(key);

        String ref = "agent-none-" + UUID.randomUUID();
        OrderDraftDTO.BatchRequest batch = new OrderDraftDTO.BatchRequest();
        batch.setOrders(List.of(request(ref)));

        // 请求级授权失败：整批抛 403，零写入（不再降级为 item ERROR）
        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentDraftService.createBatch(batch, AgentPrincipal.from(key)));
        assertEquals(403, ex.getCode());
        assertEquals(0, draftCountByRef(ref), "被拒绝的 Agent 请求不得落库");
    }

    @Test
    void agentAssignedSingleOutlet_autoAttributed_andRetryIdempotent() {
        SalesOutlet outlet = seedOutlet("Agent单档口", 1, 0);
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ASSIGNED);
        bindAgent(key, outlet, 0);
        agentContext(key);

        String ref = "agent-assigned-" + UUID.randomUUID();
        OrderDraftDTO.BatchResult first = createAgent(key, request(ref));
        assertNotNull(first.getDraftId(), "ASSIGNED 单可用档口应自动归属");
        assertNotEquals("ERROR", first.getStatus());

        OrderDraft draft = draftMapper.selectById(first.getDraftId());
        assertEquals("AGENT", draft.getEntrySource());
        assertEquals(outlet.getId(), draft.getSourceOutletId(), "Agent 未传 code 时使用默认档口");
        assertEquals("Agent单档口", draft.getSourceShop());

        // 同 Key 同 externalRefNo 重试必须幂等（而非 403），返回同一 draftId
        OrderDraftDTO.BatchResult retry = createAgent(key, request(ref));
        assertEquals("DUPLICATE", retry.getStatus());
        assertEquals(first.getDraftId(), retry.getDraftId());
        assertEquals(1, draftCountByRef(ref), "重试不得产生第二张草稿");
    }

    // ==================== Agent：显式 code / 拒绝矩阵 ====================

    @Test
    void agentExplicitAuthorizedCode_succeeds() {
        SalesOutlet outlet = seedOutlet("Agent显式档口", 1, 0);
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ALL);
        agentContext(key);

        OrderDraftDTO.SaveRequest request = request("agent-code-" + UUID.randomUUID());
        request.setSourceOutletCode(outlet.getOutletCode());
        OrderDraftDTO.BatchResult result = createAgent(key, request);

        assertNotNull(result.getDraftId());
        OrderDraft draft = draftMapper.selectById(result.getDraftId());
        assertEquals(outlet.getId(), draft.getSourceOutletId());
        assertEquals("Agent显式档口", draft.getSourceShop());
    }

    @Test
    void agentUnknownCode_rejectedWithoutInsert() {
        SalesOutlet bound = seedOutlet("Agent已绑档口", 1, 0);
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ASSIGNED);
        bindAgent(key, bound, 0);
        agentContext(key);

        String ref = "agent-unknown-code-" + UUID.randomUUID();
        OrderDraftDTO.SaveRequest request = request(ref);
        request.setSourceOutletCode("NO-SUCH-CODE");
        OrderDraftDTO.BatchRequest batch = new OrderDraftDTO.BatchRequest();
        batch.setOrders(List.of(request));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentDraftService.createBatch(batch, AgentPrincipal.from(key)));
        assertEquals(403, ex.getCode());
        assertEquals(0, draftCountByRef(ref));
    }

    @Test
    void agentDisabledBoundCode_rejected() {
        SalesOutlet disabled = seedOutlet("Agent禁用档口", 0, 0);
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ASSIGNED);
        bindAgent(key, disabled, 0);
        agentContext(key);

        String ref = "agent-disabled-code-" + UUID.randomUUID();
        OrderDraftDTO.SaveRequest request = request(ref);
        request.setSourceOutletCode(disabled.getOutletCode());
        OrderDraftDTO.BatchRequest batch = new OrderDraftDTO.BatchRequest();
        batch.setOrders(List.of(request));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentDraftService.createBatch(batch, AgentPrincipal.from(key)));
        assertEquals(403, ex.getCode(), "禁用档口不得用于新写，请求级 403 整批拒绝");
        assertEquals(0, draftCountByRef(ref));
    }

    @Test
    void agentCrossScopeCode_rejected() {
        TenantContext.setTenantId(1L);
        SalesOutlet bound = seedOutlet("Agent绑A", 1, 0);
        seedOutlet("Agent未绑B", 1, 0);
        // B 不在 Key 绑定内，用它的 code 越权
        SalesOutlet other = salesOutletMapper.selectOne(new LambdaQueryWrapper<SalesOutlet>()
                .eq(SalesOutlet::getOutletName, "Agent未绑B").last("LIMIT 1"));
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ASSIGNED);
        bindAgent(key, bound, 0);
        agentContext(key);

        String ref = "agent-cross-code-" + UUID.randomUUID();
        OrderDraftDTO.SaveRequest request = request(ref);
        request.setSourceOutletCode(other.getOutletCode());
        OrderDraftDTO.BatchRequest batch = new OrderDraftDTO.BatchRequest();
        batch.setOrders(List.of(request));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentDraftService.createBatch(batch, AgentPrincipal.from(key)));
        assertEquals(403, ex.getCode());
        assertEquals(0, draftCountByRef(ref), "越权 code 必须整批拒绝且零写入");
    }

    @Test
    void agentPassingOutletId_rejected() {
        SalesOutlet outlet = seedOutlet("Agent不许传ID", 1, 0);
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ALL);
        agentContext(key);

        String ref = "agent-id-rejected-" + UUID.randomUUID();
        OrderDraftDTO.SaveRequest request = request(ref);
        request.setSourceOutletId(outlet.getId());
        OrderDraftDTO.BatchResult result = createAgent(key, request);

        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getMessage().contains("sourceOutletCode"), "应提示 Agent 使用稳定编码");
        assertEquals(0, draftCountByRef(ref));
    }

    // ==================== 人工：显式 / 默认 / 空档口 ====================

    @Test
    void manualExplicitAuthorizedId_succeedsWithMasterSnapshot() {
        userContext();
        SalesOutlet outlet = seedOutlet("人工显式档口", 1, 0);
        OrderDraftDTO.SaveRequest request = request("manual-id-" + UUID.randomUUID());
        request.setSourceOutletId(outlet.getId());
        request.setSourceShop("客户端伪造名称");

        OrderDraftDTO.BatchResult result = draftService.create(request);
        OrderDraftDTO.View view = draftService.get(result.getDraftId());

        assertEquals(outlet.getId(), view.getSourceOutletId());
        assertEquals(outlet.getOutletCode(), view.getSourceOutletCode());
        assertEquals("人工显式档口", view.getSourceShop(), "名称必须来自主数据，忽略客户端自由文本");
    }

    @Test
    void manualDefaultOutlet_backfilled() {
        userContext();
        SalesOutlet def = seedOutlet("租户默认档口", 1, 1);
        // 第二个可用档口使默认解析必须依赖 is_tenant_default 而非“唯一即默认”
        seedOutlet("租户普通档口", 1, 0);

        OrderDraftDTO.BatchResult result = draftService.create(request("manual-default-" + UUID.randomUUID()));
        OrderDraft draft = draftMapper.selectById(result.getDraftId());

        assertEquals(def.getId(), draft.getSourceOutletId(), "未显式传档口时回填默认档口");
        assertEquals("租户默认档口", draft.getSourceShop());
    }

    @Test
    void manualNoDefaultWithoutUnassigned_rejected() {
        userContext();
        seedOutlet("人工无默认A", 1, 0);
        seedOutlet("人工无默认B", 1, 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> draftService.create(request("manual-no-default-" + UUID.randomUUID())));
        assertEquals(400, ex.getCode(), "无默认且无 unassigned 权限必须拒绝而不是写空");
    }

    @Test
    void manualNoDefaultWithUnassigned_writesNull() {
        userContext("data:outlet:unassigned");
        seedOutlet("人工空档A", 1, 0);
        seedOutlet("人工空档B", 1, 0);

        OrderDraftDTO.BatchResult result = draftService.create(request("manual-unassigned-" + UUID.randomUUID()));
        OrderDraftDTO.View view = draftService.get(result.getDraftId());

        assertNull(view.getSourceOutletId());
        assertNull(view.getSourceOutletCode());
        assertNull(view.getSourceShop());
    }

    // ==================== 更新规则 ====================

    @Test
    void updateKeepsExistingOutletWhenStableFieldsOmitted() {
        userContext();
        SalesOutlet outlet = seedOutlet("更新保留档口", 1, 0);
        OrderDraftDTO.SaveRequest request = request("update-keep-" + UUID.randomUUID());
        request.setSourceOutletId(outlet.getId());
        Long draftId = draftService.create(request).getDraftId();

        request.setSourceOutletId(null);
        request.setSourceOutletCode(null);
        draftService.update(draftId, request);

        OrderDraft draft = draftMapper.selectById(draftId);
        assertEquals(outlet.getId(), draft.getSourceOutletId(), "更新省略档口字段必须保留既有归属");
        assertEquals("更新保留档口", draft.getSourceShop());
    }

    @Test
    void updateChangesOutletAndRefreshesSnapshot() {
        userContext();
        SalesOutlet first = seedOutlet("更新前档口", 1, 0);
        SalesOutlet second = seedOutlet("更新后档口", 1, 0);
        OrderDraftDTO.SaveRequest request = request("update-change-" + UUID.randomUUID());
        request.setSourceOutletId(first.getId());
        Long draftId = draftService.create(request).getDraftId();

        request.setSourceOutletId(second.getId());
        draftService.update(draftId, request);

        OrderDraft draft = draftMapper.selectById(draftId);
        assertEquals(second.getId(), draft.getSourceOutletId());
        assertEquals("更新后档口", draft.getSourceShop(), "改档口必须刷新名称快照");

        OrderDraftDTO.View view = draftService.get(draftId);
        assertEquals(second.getOutletCode(), view.getSourceOutletCode());
    }

    @Test
    void updateRejectsUnusableOutlet() {
        SalesOutlet bound = seedOutlet("更新授权档口", 1, 0);
        SalesOutlet forbidden = seedOutlet("更新越权档口", 1, 0);
        // ASSIGNED 用户：仅绑定 bound，forbidden 属于越权
        TenantContext.setTenantId(1L);
        SecurityContextHolder.setContext(new SecurityContextImpl(new TestingAuthenticationToken(
                user(1L), null, List.of(
                        new SimpleGrantedAuthority("data:order:peopleAll"),
                        new SimpleGrantedAuthority("data:outlet:unassigned")))));
        bindUserTo(1L, bound, 0);

        OrderDraftDTO.SaveRequest request = request("update-forbidden-" + UUID.randomUUID());
        request.setSourceOutletId(bound.getId());
        Long draftId = draftService.create(request).getDraftId();

        request.setSourceOutletId(forbidden.getId());
        BusinessException ex = assertThrows(BusinessException.class, () -> draftService.update(draftId, request));
        assertEquals(403, ex.getCode());
        assertEquals(bound.getId(), draftMapper.selectById(draftId).getSourceOutletId(),
                "越权更新不得改动既有归属");
    }

    private User user(Long id) {
        User principal = new User();
        principal.setId(id);
        principal.setUsername("admin");
        return principal;
    }

    // ==================== View / Summary 字段 ====================

    @Test
    void viewAndSummary_exposeOutletIdAndCode() {
        userContext();
        SalesOutlet outlet = seedOutlet("列表档口", 1, 0);
        OrderDraftDTO.SaveRequest request = request("summary-fields-" + UUID.randomUUID());
        request.setSourceOutletId(outlet.getId());
        Long draftId = draftService.create(request).getDraftId();

        OrderDraftDTO.View view = draftService.get(draftId);
        assertEquals(outlet.getId(), view.getSourceOutletId());
        assertEquals(outlet.getOutletCode(), view.getSourceOutletCode());
        assertEquals("列表档口", view.getSourceShop());

        com.blade.common.result.PageResult<OrderDraftDTO.Summary> page =
                draftService.page(1, 20, "EDITING", null, null, null, false, null, null, null, null);
        OrderDraftDTO.Summary summary = page.getRecords().stream()
                .filter(record -> draftId.equals(record.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(outlet.getId(), summary.getSourceOutletId());
        assertEquals(outlet.getOutletCode(), summary.getSourceOutletCode());
        assertEquals("列表档口", summary.getSourceShop(), "Summary 必须返回服务端权威名称快照");
    }

    // ==================== 历史空档口 Agent 草稿 ====================

    @Test
    void agentHistoricalNullOutletRetry_notEscalatedAndNoIdLeak() {
        SalesOutlet outlet = seedOutlet("Agent历史档口", 1, 0);
        AgentKey key = seedAgentKey(AgentKey.OUTLET_SCOPE_ASSIGNED);
        bindAgent(key, outlet, 0);
        agentContext(key);

        // 直接构造历史空档口 Agent 草稿（模拟迁移前遗留数据）
        String ref = "agent-historical-null-" + UUID.randomUUID();
        OrderDraft historical = new OrderDraft();
        historical.setTenantId(1L);
        historical.setExternalRefNo(ref);
        historical.setSourceBatchNo("ATTR");
        historical.setSourceOrderNo(ref);
        historical.setEntrySource("AGENT");
        historical.setCreatedByAgentKeyId(key.getId());
        historical.setStatus("EDITING");
        historical.setCustomerName("历史客户");
        historical.setWarningAcknowledged(0);
        historical.setDeleted(0);
        draftMapper.insert(historical);

        OrderDraftDTO.BatchRequest batch = new OrderDraftDTO.BatchRequest();
        batch.setOrders(List.of(request(ref)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentDraftService.createBatch(batch, AgentPrincipal.from(key)));

        assertEquals(403, ex.getCode(), "历史空档口不得被 Agent 读回升级（请求级 403）");
        assertFalse(ex.getMessage() != null && ex.getMessage().contains(String.valueOf(historical.getId())),
                "错误信息不得包含内部草稿ID");
        // 历史草稿保持原状，未被覆盖
        assertEquals("EDITING", draftMapper.selectById(historical.getId()).getStatus());
    }
}
