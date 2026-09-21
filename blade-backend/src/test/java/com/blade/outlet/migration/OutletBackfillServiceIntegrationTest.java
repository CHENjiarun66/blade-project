package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Series F 历史档口回填本地集成验证（真实隔离库，事务回滚）。
 *
 * <p>使用独立合成租户，覆盖：dry-run 报告、只更新 source_outlet_id、source_shop/金额/状态/明细不变、
 * 冲突不覆盖、确认草稿跳过、疑似批次跳过、跨租户隔离、幂等、非法档口拒绝。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutletBackfillServiceIntegrationTest {

    private static final long TENANT = 987654L;
    private static final long OTHER_TENANT = 987655L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutletBackfillService service;

    private long enabledA;
    private long enabledB;
    private long disabledOutlet;
    private long o1;
    private long o3Conflict;
    private long o5Other;
    private long d1;
    private long d2Confirmed;
    private long d3Conflict;

    @BeforeEach
    void setUp() {
        enabledA = insertOutlet(TENANT, "E3F-A", 1, 0);
        enabledB = insertOutlet(TENANT, "E3F-B", 1, 0);
        disabledOutlet = insertOutlet(TENANT, "E3F-D", 0, 0);
        insertOutlet(TENANT, "E3F-X", 1, 1);
        insertOutlet(OTHER_TENANT, "E3F-C", 1, 0);

        o1 = insertOrder(TENANT, "F-ORD-1", "御龙", null, "TB1_1", "100.00", 0);
        o3Conflict = insertOrder(TENANT, "F-ORD-3", "御龙", enabledB, null, "50.00", 1);
        insertOrder(TENANT, "F-ORD-4", "总店", null, null, "30.00", 2);
        o5Other = insertOrder(OTHER_TENANT, "F-ORD-5", "御龙", null, null, "70.00", 0);
        insertOrder(TENANT, "F-ORD-S", "TB1", null, "TB1_9", "20.00", 0);
        insertOrderItem(o1, TENANT, "10.00");

        d1 = insertDraft(TENANT, "F-D-1", "御龙", null, null);
        d2Confirmed = insertDraft(TENANT, "F-D-2", "御龙", null, 999L);
        d3Conflict = insertDraft(TENANT, "F-D-3", "御龙", enabledB, null);
        insertDraftItem(d1, TENANT);
    }

    @Test
    void previewReportsCandidatesWithoutWriting() {
        OutletBackfillReport report = service.preview(TENANT, List.of(
                map("御龙", "E3F-A"), map("总店", "E3F-B"),
                skip("TB1", "疑似批次"), review("未知店")));

        assertEquals("PREVIEW", report.mode());
        assertEquals(2, report.ordersCandidates());
        assertEquals(1, report.draftsCandidates());
        assertEquals(1, report.ordersConflict());
        assertEquals(1, report.draftsConflict());
        assertEquals(1, report.confirmedDraftsSkipped());
        assertNullOutlet(o1, "sale_order");
        assertNullOutlet(d1, "order_draft");
    }

    @Test
    void applyUpdatesOnlySourceOutletIdAndIsIdempotent() {
        OutletBackfillReport first = service.apply(TENANT, List.of(
                map("御龙", "E3F-A"), map("总店", "E3F-B")));

        assertEquals("APPLY", first.mode());
        assertEquals(2, first.ordersUpdated());
        assertEquals(1, first.draftsUpdated());
        assertTrue(first.reconciliationConsistent());
        assertEquals(enabledA, outletId(o1, "sale_order"));
        assertEquals(enabledA, outletId(d1, "order_draft"));
        assertNullOutlet(o5Other, "sale_order"); // 其他租户不受影响
        assertEquals(enabledB, outletId(o3Conflict, "sale_order")); // 冲突不覆盖
        assertEquals(enabledB, outletId(d3Conflict, "order_draft"));
        assertNotNull(jdbc.queryForObject("SELECT confirmed_order_id FROM order_draft WHERE id=?", Long.class, d2Confirmed));
        assertEquals("御龙", jdbc.queryForObject("SELECT source_shop FROM sale_order WHERE id=?", String.class, o1));

        OutletBackfillReport second = service.apply(TENANT, List.of(
                map("御龙", "E3F-A"), map("总店", "E3F-B")));
        assertEquals(0, second.ordersUpdated());
        assertEquals(0, second.draftsUpdated());
        assertEquals(2, second.ordersAlreadyApplied());
        assertEquals(1, second.draftsAlreadyApplied());
        assertTrue(second.reconciliationConsistent());
    }

    @Test
    void rejectsUnknownDisabledDeletedAndCrossTenantOutletsWithoutWriting() {
        assertThrows(BusinessException.class,
                () -> service.apply(TENANT, List.of(map("御龙", "NO-SUCH"))));
        assertThrows(BusinessException.class,
                () -> service.apply(TENANT, List.of(map("御龙", "E3F-D"))));
        assertThrows(BusinessException.class,
                () -> service.apply(TENANT, List.of(map("御龙", "E3F-X"))));
        assertThrows(BusinessException.class,
                () -> service.apply(TENANT, List.of(map("御龙", "E3F-C"))));
        assertNullOutlet(o1, "sale_order");
        assertNullOutlet(d1, "order_draft");
    }

    @Test
    void marksBatchSuspectRowsAsSkipped() {
        OutletBackfillReport report = service.preview(TENANT, List.of(map("TB1", "E3F-A")));

        assertEquals(1, report.ordersSuspect());
        assertEquals(0, report.ordersCandidates());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("疑似批次")));
    }

    @Test
    void rejectsWholeBatchBeforeWritingWhenAnyMappingInvalid() {
        List<OutletBackfillMappingRow> rows = List.of(map("御龙", "E3F-A"), map("总店", "E3F-D"));
        assertThrows(BusinessException.class, () -> service.apply(TENANT, rows));
        assertNullOutlet(o1, "sale_order");
    }

    // ==================== helpers ====================

    private OutletBackfillMappingRow map(String shop, String code) {
        return new OutletBackfillMappingRow(TENANT, shop, code, OutletBackfillMapping.DECISION_MAP, "");
    }

    private OutletBackfillMappingRow skip(String shop, String reason) {
        return new OutletBackfillMappingRow(TENANT, shop, "", OutletBackfillMapping.DECISION_SKIP, reason);
    }

    private OutletBackfillMappingRow review(String shop) {
        return new OutletBackfillMappingRow(TENANT, shop, "", OutletBackfillMapping.DECISION_REVIEW, "需人工");
    }

    private long insertOutlet(long tenantId, String code, int status, int deleted) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id, outlet_code, outlet_name, outlet_type, status, deleted, sort, is_tenant_default) "
                + "VALUES(?,?,?,'STORE',?,?,0,0)", tenantId, code, "档口" + code, status, deleted);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?",
                Long.class, tenantId, code);
    }

    private long insertOrder(long tenantId, String orderNo, String shop, Long outletId,
                             String docNo, String amount, int status) {
        jdbc.update("INSERT INTO sale_order(tenant_id, order_no, customer_name, total_amount, status, deleted, "
                        + "source_shop, source_outlet_id, source_doc_no) VALUES(?,?,?,?,?,0,?,?,?)",
                tenantId, orderNo, "客户", new BigDecimal(amount), status, shop, outletId, docNo);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE tenant_id=? AND order_no=?",
                Long.class, tenantId, orderNo);
    }

    private void insertOrderItem(long orderId, long tenantId, String price) {
        jdbc.update("INSERT INTO sale_order_item(tenant_id, order_id, product_id, product_name, price, quantity, subtotal) "
                + "VALUES(?,?,1,'商品',?,1,?)", tenantId, orderId, new BigDecimal(price), new BigDecimal(price));
    }

    private long insertDraft(long tenantId, String ref, String shop, Long outletId, Long confirmedOrderId) {
        jdbc.update("INSERT INTO order_draft(tenant_id, external_ref_no, source_shop, source_outlet_id, confirmed_order_id, deleted) "
                + "VALUES(?,?,?,?,?,0)", tenantId, ref, shop, outletId, confirmedOrderId);
        return jdbc.queryForObject("SELECT id FROM order_draft WHERE tenant_id=? AND external_ref_no=?",
                Long.class, tenantId, ref);
    }

    private void insertDraftItem(long draftId, long tenantId) {
        jdbc.update("INSERT INTO order_draft_item(tenant_id, draft_id) VALUES(?,?)", tenantId, draftId);
    }

    private Long outletId(long rowId, String table) {
        return jdbc.queryForObject("SELECT source_outlet_id FROM " + table + " WHERE id=?", Long.class, rowId);
    }

    private void assertNullOutlet(long rowId, String table) {
        assertEquals(null, outletId(rowId, table));
    }
}
