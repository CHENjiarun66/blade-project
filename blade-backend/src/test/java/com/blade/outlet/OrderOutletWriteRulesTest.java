package com.blade.outlet;

import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Series D（BE-OUTLET-007）正式订单/草稿档口写入规则真实集成测试。
 *
 * <p>覆盖：新建显式/默认/失败、禁用/跨租户、永不新写 NULL、source_shop 只能来自主数据、
 * 列表显式筛选与越权 403、待归档筛选权限、改档口权限/原因/审计/历史 NULL 归档/
 * 已完成订单高权限例外、自由文本 sourceShop 失效、草稿列表结构化筛选。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderOutletWriteRulesTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderService orderService;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderDraftService draftService;
    @Autowired private OrderDraftMapper draftMapper;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ==================== 上下文 / 种子 ====================

    private void auth(long userId, String... authorities) {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(userId);
        principal.setUsername("u" + userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private long seedOutlet(String code, int status, int isDefault) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                        + "VALUES(1,?,?,'STORE',?,0,0,?)",
                code, "档口" + code, status, isDefault);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE outlet_code=?", Long.class, code);
    }

    private long seedCrossTenantOutlet(String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                        + "VALUES(2,?,?,'STORE',1,0,0,0)",
                code, "跨租户档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE outlet_code=?", Long.class, code);
    }

    private long seedUser(String name) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,1,0)",
                name, "x", name);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private void bindUser(long userId, long outletId, int isDefault) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,?,1,0)",
                userId, outletId, isDefault);
    }

    private long seedSku() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbc.update("INSERT INTO product(product_code,name,category_id,unit,status,tenant_id,deleted) VALUES(?,?,1,'件',1,1,0)",
                "DR-" + suffix, "档口规则测试商品");
        Long productId = jdbc.queryForObject("SELECT id FROM product WHERE product_code=?", Long.class, "DR-" + suffix);
        jdbc.update("INSERT INTO product_sku(product_id,color_id,size_id,sku_code,sku_type,price,cost_price,status,tenant_id,deleted) "
                        + "VALUES(?,1,1,?,'NORMAL',50,10,1,1,0)",
                productId, "DR-" + suffix + "-BLACK-XS");
        return jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code=?", Long.class, "DR-" + suffix + "-BLACK-XS");
    }

    private OrderCreateDTO createDto(Long outletId) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setCustomerName("档口写入测试客户");
        dto.setSourceDocNo("OUTLET-DOC-" + UUID.randomUUID());
        dto.setSourceOutletId(outletId);
        OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
        item.setSkuId(seedSku());
        item.setQuantity(1);
        item.setPrice(new BigDecimal("50.00"));
        dto.setItems(List.of(item));
        return dto;
    }

    private long seedOrder(String no, Long salesmanId, Long outletId, String sourceShop, String fulfillmentStatus) {
        jdbc.update("""
                INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,
                  freight_amount,freight_cost,payment_status,status,fulfillment_status,fulfillment_mode,collection_status,
                  gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,
                  salesman_id,source_outlet_id,source_shop,tenant_id,deleted,version)
                VALUES(?,CURDATE(),'SPOT','档口规则客户',100,0,0,0,0,0,?,'UNDECIDED','UNPAID',0,0,0,0,100,?,?,?,1,0,0)
                """, no, fulfillmentStatus, salesmanId, outletId, sourceShop);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, no);
    }

    private long countChangeLogs(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_outlet_change_log WHERE order_id=?", Long.class, orderId);
    }

    private OrderPageDTO pageDto() {
        OrderPageDTO dto = new OrderPageDTO();
        dto.setCurrent(1L);
        dto.setSize(100L);
        return dto;
    }

    private void update(long orderId, OrderUpdateDTO dto) {
        dto.setId(orderId);
        orderService.update(dto);
    }

    // ==================== 新建 ====================

    @Test
    void create_explicitAuthorizedOutlet_usesMasterSnapshot_ignoringClientText() {
        long outletId = seedOutlet("D-EXPL", 1, 0);
        long userId = seedUser("dexpl" + System.nanoTime() % 100000);
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        OrderCreateDTO dto = createDto(outletId);
        dto.setSourceShop("客户端伪造档口");
        Long orderId = orderService.create(dto);

        assertEquals(outletId, orderMapper.selectById(orderId).getSourceOutletId());
        assertEquals("档口D-EXPL", orderMapper.selectById(orderId).getSourceShop(), "名称必须来自主数据");

        OrderVO vo = orderService.getById(orderId);
        assertEquals(outletId, vo.getSourceOutletId());
        assertEquals("D-EXPL", vo.getSourceOutletCode());
        assertEquals("档口D-EXPL", vo.getSourceShop());
    }

    @Test
    void create_withoutOutlet_usesTenantDefault() {
        long def = seedOutlet("D-DEF", 1, 1);
        seedOutlet("D-OTHER", 1, 0);
        long userId = seedUser("ddef" + System.nanoTime() % 100000);
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        Long orderId = orderService.create(createDto(null));

        assertEquals(def, orderMapper.selectById(orderId).getSourceOutletId());
    }

    @Test
    void create_withoutOutletAndNoDefault_failsWithoutInsert() {
        seedOutlet("D-NODEF-A", 1, 0);
        seedOutlet("D-NODEF-B", 1, 0);
        long userId = seedUser("dnodef" + System.nanoTime() % 100000);
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        OrderCreateDTO dto = createDto(null);
        dto.setCustomerName("无默认档口客户");
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.create(dto));
        assertEquals(400, ex.getCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE tenant_id=1 AND customer_name='无默认档口客户' AND deleted=0",
                Long.class), "被拒绝的请求不得落库");
    }

    @Test
    void create_disabledOutlet_rejectedWithoutInsert() {
        long disabled = seedOutlet("D-DISABLED", 0, 0);
        long userId = seedUser("ddis" + System.nanoTime() % 100000);
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        OrderCreateDTO dto = createDto(disabled);
        dto.setCustomerName("禁用档口客户");
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.create(dto));
        assertEquals(403, ex.getCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE tenant_id=1 AND customer_name='禁用档口客户' AND deleted=0",
                Long.class));
    }

    @Test
    void create_crossTenantOutlet_rejectedWithoutInsert() {
        long cross = seedCrossTenantOutlet("D-CROSS");
        long userId = seedUser("dcross" + System.nanoTime() % 100000);
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        OrderCreateDTO dto = createDto(cross);
        dto.setCustomerName("跨租户档口客户");
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.create(dto));
        assertEquals(403, ex.getCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE tenant_id=1 AND customer_name='跨租户档口客户' AND deleted=0",
                Long.class));
    }

    @Test
    void create_unassignedOwnerWithoutDefault_stillRejected_neverWritesNull() {
        seedOutlet("D-UN-A", 1, 0);
        seedOutlet("D-UN-B", 1, 0);
        long userId = seedUser("dun" + System.nanoTime() % 100000);
        // 拥有待归档读取权限，但新建正式订单仍必须有具体档口
        auth(userId, "data:outlet:all", "data:order:peopleAll", "data:outlet:unassigned");

        OrderCreateDTO dto = createDto(null);
        dto.setCustomerName("待归档新建客户");
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.create(dto));
        assertEquals(400, ex.getCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE tenant_id=1 AND customer_name='待归档新建客户' AND source_outlet_id IS NULL AND deleted=0",
                Long.class));
    }

    // ==================== 列表筛选 ====================

    @Test
    void page_explicitOutletFilter_onlyReturnsThatOutlet() {
        long outletA = seedOutlet("D-FLT-A", 1, 0);
        long outletB = seedOutlet("D-FLT-B", 1, 0);
        long userId = seedUser("dflt" + System.nanoTime() % 100000);
        long orderA = seedOrder("D-FLT-A-" + System.nanoTime(), userId, outletA, "档口D-FLT-A", "CONFIRMED");
        seedOrder("D-FLT-B-" + System.nanoTime(), userId, outletB, "档口D-FLT-B", "CONFIRMED");
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        OrderPageDTO dto = pageDto();
        dto.setSourceOutletId(outletA);
        PageResult<OrderVO> page = orderService.pageList(dto);
        assertEquals(1, page.getRecords().stream().filter(vo -> orderA == vo.getId()).count());
        assertTrue(page.getRecords().stream().allMatch(vo -> outletA == vo.getSourceOutletId()));
    }

    @Test
    void page_unauthorizedOutletFilter_returns403() {
        long outletA = seedOutlet("D-UNA-A", 1, 0);
        long outletB = seedOutlet("D-UNA-B", 1, 0);
        long userId = seedUser("duna" + System.nanoTime() % 100000);
        bindUser(userId, outletA, 0);
        auth(userId, "data:order:peopleAll");

        OrderPageDTO dto = pageDto();
        dto.setSourceOutletId(outletB);
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.pageList(dto));
        assertEquals(403, ex.getCode(), "伪造/越权档口筛选必须 403，而不是静默空结果");
    }

    @Test
    void page_unassignedOnly_requiresPermission() {
        long userId = seedUser("dpend" + System.nanoTime() % 100000);
        seedOrder("D-PEND-" + System.nanoTime(), userId, null, null, "CONFIRMED");

        auth(userId, "data:outlet:all", "data:order:peopleAll");
        OrderPageDTO denied = pageDto();
        denied.setUnassignedOnly(true);
        assertEquals(403, assertThrows(BusinessException.class, () -> orderService.pageList(denied)).getCode());

        auth(userId, "data:outlet:all", "data:order:peopleAll", "data:outlet:unassigned");
        OrderPageDTO allowed = pageDto();
        allowed.setUnassignedOnly(true);
        assertTrue(orderService.pageList(allowed).getRecords().stream().allMatch(vo -> vo.getSourceOutletId() == null));
    }

    @Test
    void page_outletAndUnassignedFilter_areMutuallyExclusive() {
        long outlet = seedOutlet("D-MUTEX", 1, 0);
        long userId = seedUser("dmutex" + System.nanoTime() % 100000);
        auth(userId, "data:outlet:all", "data:order:peopleAll", "data:outlet:unassigned");
        OrderPageDTO dto = pageDto();
        dto.setSourceOutletId(outlet);
        dto.setUnassignedOnly(true);
        assertEquals(400, assertThrows(BusinessException.class, () -> orderService.pageList(dto)).getCode());
    }

    // ==================== 改档口 ====================

    @Test
    void update_changeOutlet_requiresPermission_reason_andWritesAudit() {
        long outletA = seedOutlet("D-CHG-A", 1, 0);
        long outletB = seedOutlet("D-CHG-B", 1, 0);
        long userId = seedUser("dchg" + System.nanoTime() % 100000);
        long orderId = seedOrder("D-CHG-" + System.nanoTime(), userId, outletA, "档口D-CHG-A", "CONFIRMED");

        // 缺少 btn:order:changeOutlet：拒绝且不写审计
        auth(userId, "data:outlet:all", "data:order:peopleAll");
        OrderUpdateDTO denied = new OrderUpdateDTO();
        denied.setSourceOutletId(outletB);
        denied.setOutletChangeReason("搬迁");
        assertEquals(403, assertThrows(BusinessException.class, () -> update(orderId, denied)).getCode());
        assertEquals(outletA, orderMapper.selectById(orderId).getSourceOutletId());
        assertEquals(0, countChangeLogs(orderId));

        // 缺少原因：拒绝
        auth(userId, "data:outlet:all", "data:order:peopleAll", "btn:order:changeOutlet");
        OrderUpdateDTO noReason = new OrderUpdateDTO();
        noReason.setSourceOutletId(outletB);
        assertEquals(400, assertThrows(BusinessException.class, () -> update(orderId, noReason)).getCode());
        assertEquals(0, countChangeLogs(orderId));

        // 正常改档口：写审计 + 刷新名称快照
        OrderUpdateDTO ok = new OrderUpdateDTO();
        ok.setSourceOutletId(outletB);
        ok.setOutletChangeReason("门店搬迁");
        update(orderId, ok);
        assertEquals(outletB, orderMapper.selectById(orderId).getSourceOutletId());
        assertEquals("档口D-CHG-B", orderMapper.selectById(orderId).getSourceShop());
        assertEquals(1, countChangeLogs(orderId));
        assertEquals("门店搬迁", jdbc.queryForObject(
                "SELECT reason FROM order_outlet_change_log WHERE order_id=?", String.class, orderId));
    }

    @Test
    void update_sameOutlet_noAudit() {
        long outletA = seedOutlet("D-SAME-A", 1, 0);
        long userId = seedUser("dsame" + System.nanoTime() % 100000);
        long orderId = seedOrder("D-SAME-" + System.nanoTime(), userId, outletA, "档口D-SAME-A", "CONFIRMED");
        auth(userId, "data:outlet:all", "data:order:peopleAll", "btn:order:changeOutlet");

        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setSourceOutletId(outletA);
        update(orderId, dto);

        assertEquals(0, countChangeLogs(orderId), "传入与原值相同不写审计");
    }

    @Test
    void update_archiveHistoricalNull_requiresUnassignedAndWritesAudit() {
        long outlet = seedOutlet("D-ARC", 1, 0);
        long userId = seedUser("darc" + System.nanoTime() % 100000);
        long orderId = seedOrder("D-ARC-" + System.nanoTime(), userId, null, null, "CONFIRMED");

        auth(userId, "data:outlet:all", "data:order:peopleAll", "btn:order:changeOutlet");
        OrderUpdateDTO denied = new OrderUpdateDTO();
        denied.setSourceOutletId(outlet);
        denied.setOutletChangeReason("历史归档");
        assertEquals(403, assertThrows(BusinessException.class, () -> update(orderId, denied)).getCode());

        auth(userId, "data:outlet:all", "data:order:peopleAll", "btn:order:changeOutlet", "data:outlet:unassigned");
        OrderUpdateDTO ok = new OrderUpdateDTO();
        ok.setSourceOutletId(outlet);
        ok.setOutletChangeReason("历史归档");
        update(orderId, ok);
        assertEquals(outlet, orderMapper.selectById(orderId).getSourceOutletId());
        assertEquals(1, countChangeLogs(orderId));
        assertNull(jdbc.queryForObject("SELECT old_outlet_id FROM order_outlet_change_log WHERE order_id=?",
                Long.class, orderId));
    }

    @Test
    void update_completedOrder_allowsOnlyOutletChangeAndRemark_images() {
        long outletA = seedOutlet("D-CMP-A", 1, 0);
        long outletB = seedOutlet("D-CMP-B", 1, 0);
        long userId = seedUser("dcmp" + System.nanoTime() % 100000);
        long orderId = seedOrder("D-CMP-" + System.nanoTime(), userId, outletA, "档口D-CMP-A", "COMPLETED");
        jdbc.update("UPDATE sale_order SET freight_amount=8.00 WHERE id=?", orderId);
        auth(userId, "data:outlet:all", "data:order:peopleAll", "btn:order:changeOutlet");

        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setSourceOutletId(outletB);
        dto.setOutletChangeReason("高权限修正");
        dto.setFreightAmount(new BigDecimal("99.00"));
        dto.setRemark("补充备注");
        update(orderId, dto);

        assertEquals(outletB, orderMapper.selectById(orderId).getSourceOutletId(), "已完成订单允许高权限改档口");
        assertEquals(0, orderMapper.selectById(orderId).getFreightAmount().compareTo(new BigDecimal("8.00")),
                "已完成订单金额不得被放开");
        assertEquals("补充备注", orderMapper.selectById(orderId).getRemark());
        assertEquals(1, countChangeLogs(orderId));
    }

    @Test
    void update_freeTextSourceShop_isIgnored() {
        long outletA = seedOutlet("D-TXT-A", 1, 0);
        long userId = seedUser("dtxt" + System.nanoTime() % 100000);
        long orderId = seedOrder("D-TXT-" + System.nanoTime(), userId, outletA, "档口D-TXT-A", "CONFIRMED");
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setSourceShop("自由文本伪造");
        update(orderId, dto);

        assertEquals(outletA, orderMapper.selectById(orderId).getSourceOutletId());
        assertEquals("档口D-TXT-A", orderMapper.selectById(orderId).getSourceShop());
        assertEquals(0, countChangeLogs(orderId));
    }

    // ==================== 草稿列表结构化筛选 ====================

    @Test
    void draftPage_filterByOutlet_andUnassignedPermission() {
        long outletA = seedOutlet("D-DRF-A", 1, 0);
        long outletB = seedOutlet("D-DRF-B", 1, 0);
        long userId = seedUser("ddrf" + System.nanoTime() % 100000);
        long draftA = seedDraft("D-DRF-A-" + System.nanoTime(), userId, outletA);
        seedDraft("D-DRF-B-" + System.nanoTime(), userId, outletB);
        seedDraft("D-DRF-N-" + System.nanoTime(), userId, null);
        auth(userId, "data:outlet:all", "data:order:peopleAll");

        PageResult<OrderDraftDTO.Summary> byOutlet = draftService.page(
                1, 50, "EDITING", null, null, null, false, null, null, outletA, null);
        OrderDraftDTO.Summary summaryA = byOutlet.getRecords().stream()
                .filter(s -> draftA == s.getId())
                .findFirst()
                .orElseThrow();
        assertEquals(outletA, summaryA.getSourceOutletId());
        assertEquals("D-DRF-A", summaryA.getSourceOutletCode());
        assertEquals("档口D-DRF-A", summaryA.getSourceShop(), "Summary 必须返回服务端名称快照");
        assertTrue(byOutlet.getRecords().stream().allMatch(s -> outletA == s.getSourceOutletId()));

        // 无 unassigned：待归档筛选 403
        assertEquals(403, assertThrows(BusinessException.class, () -> draftService.page(
                1, 50, "EDITING", null, null, null, false, null, null, null, true)).getCode());

        // 有 unassigned：仅返回待归档
        auth(userId, "data:outlet:all", "data:order:peopleAll", "data:outlet:unassigned");
        PageResult<OrderDraftDTO.Summary> pending = draftService.page(
                1, 50, "EDITING", null, null, null, false, null, null, null, true);
        assertTrue(pending.getRecords().stream().allMatch(s -> s.getSourceOutletId() == null));

        // 越权档口筛选 403
        long otherUser = seedUser("ddrfo" + System.nanoTime() % 100000);
        bindUser(otherUser, outletA, 0);
        auth(otherUser, "data:order:peopleAll");
        assertEquals(403, assertThrows(BusinessException.class, () -> draftService.page(
                1, 50, "EDITING", null, null, null, false, null, null, outletB, null)).getCode());
    }

    private long seedDraft(String ref, Long createdByUserId, Long outletId) {
        OrderDraft draft = new OrderDraft();
        draft.setTenantId(1L);
        draft.setExternalRefNo(ref);
        draft.setSourceBatchNo("D");
        draft.setSourceOrderNo(ref);
        draft.setStatus("EDITING");
        draft.setCustomerName("草稿筛选客户");
        draft.setSourceOutletId(outletId);
        draft.setSourceShop(outletId == null ? null
                : jdbc.queryForObject("SELECT outlet_name FROM sales_outlet WHERE id=?", String.class, outletId));
        draft.setCreatedByUserId(createdByUserId);
        draft.setWarningAcknowledged(0);
        draft.setDeleted(0);
        draftMapper.insert(draft);
        return draft.getId();
    }

    @Test
    void changeOutletPermission_grantedOnlyToOwnerAndAdmin() {
        List<String> roles = jdbc.queryForList(
                "SELECT r.role_code FROM sys_role_permission rp "
                        + "JOIN sys_role r ON r.id = rp.role_id "
                        + "JOIN sys_permission p ON p.id = rp.permission_id "
                        + "WHERE p.code = 'btn:order:changeOutlet' "
                        + "AND rp.deleted = 0 AND r.deleted = 0 AND r.status = 1 AND p.deleted = 0",
                String.class);
        assertEquals(java.util.Set.of("ROLE_OWNER", "ROLE_ADMIN"), new java.util.HashSet<>(roles),
                "改档口高权限只能授予 OWNER/ADMIN");
    }
}
