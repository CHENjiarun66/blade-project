package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Series F 历史档口回填本地集成验证（真实隔离库，事务回滚）。
 *
 * <p>覆盖：dry-run 报告（分类/样例）、只更新 source_outlet_id、source_shop/金额/状态/明细不变、
 * 冲突不覆盖、确认草稿跳过、疑似批次跳过、跨租户隔离、幂等、非法档口拒绝、JSON/Markdown 输出。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutletBackfillServiceIntegrationTest {

    private static final long TENANT = 987654L;
    private static final long OTHER_TENANT = 987655L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutletBackfillService service;

    @TempDir Path tempDir;

    private Path mappingFile;
    private long enabledA;
    private long enabledB;
    private long o1;
    private long o3Conflict;
    private long o4;
    private long o5Other;
    private long d1;
    private long d2Confirmed;
    private long d3Conflict;
    private String o1No;

    @BeforeEach
    void setUp() throws Exception {
        mappingFile = tempDir.resolve("mapping-e3f.csv");
        Files.writeString(mappingFile,
                "tenant_id,legacy_source_shop,outlet_code,decision,reason\n"
                        + TENANT + ",御龙,E3F-A,MAP,\n"
                        + TENANT + ",总店,E3F-B,MAP,\n",
                java.nio.charset.StandardCharsets.UTF_8);
        enabledA = insertOutlet(TENANT, "E3F-A", 1, 0);
        enabledB = insertOutlet(TENANT, "E3F-B", 1, 0);
        insertOutlet(TENANT, "E3F-D", 0, 0);
        insertOutlet(TENANT, "E3F-X", 1, 1);
        insertOutlet(OTHER_TENANT, "E3F-C", 1, 0);

        o1No = "F-ORD-1";
        o1 = insertOrder(TENANT, o1No, "御龙", null, "TB1_1", "100.00", 0);
        o3Conflict = insertOrder(TENANT, "F-ORD-3", "御龙", enabledB, null, "50.00", 1);
        o4 = insertOrder(TENANT, "F-ORD-4", "总店", null, null, "30.00", 2);
        o5Other = insertOrder(OTHER_TENANT, "F-ORD-5", "御龙", null, null, "70.00", 0);
        insertOrder(TENANT, "F-ORD-S", "TB1", null, "TB1_9", "20.00", 0);
        insertOrder(TENANT, "F-ORD-BLANK", null, null, null, "10.00", 0);
        insertOrder(TENANT, "F-ORD-UNMAPPED", "OTHER-SHOP", null, null, "11.00", 0);
        insertOrderItem(o1, TENANT, "10.00");

        d1 = insertDraft(TENANT, "F-D-1", "御龙", null, null);
        d2Confirmed = insertDraft(TENANT, "F-D-2", "御龙", null, 999L);
        d3Conflict = insertDraft(TENANT, "F-D-3", "御龙", enabledB, null);
        insertDraftItem(d1, TENANT);
    }

    private OutletBackfillApproval approval() {
        return new OutletBackfillApproval(TENANT, tempDir, "blade_rehearsal", mappingFile.toString(),
                "ops-dsh", Instant.now());
    }

    @Test
    void previewReportsCandidatesGroupsAndSamplesWithoutWriting() throws Exception {
        OutletBackfillReport report = service.preview(TENANT, tempDir, List.of(
                map("御龙", "E3F-A"), map("总店", "E3F-B"),
                skip("TB1", "疑似批次"), review("未知店")));

        assertEquals("PREVIEW", report.mode());
        assertEquals(2, report.ordersCandidates());
        assertEquals(1, report.draftsCandidates());
        assertEquals(1, report.ordersConflict());
        assertEquals(1, report.draftsConflict());
        assertEquals(1, report.confirmedDraftsSkipped());
        assertGroup(report, "sale_order", OutletBackfillService.BUCKET_BLANK_NULL, 1);
        assertGroup(report, "sale_order", OutletBackfillService.BUCKET_UNMAPPED, 1);
        assertBucketTotal(report, "sale_order", OutletBackfillService.BUCKET_MAP_CANDIDATE, 2);
        assertGroup(report, "order_draft", OutletBackfillService.BUCKET_CONFIRMED_DRAFT_SKIPPED, 1);
        assertGroup(report, "sale_order", OutletBackfillService.BUCKET_SKIP, 1);
        // MAP candidate 样例包含订单号，且限本租户
        OutletBackfillReport.ValueGroup candidate = group(report, "sale_order", OutletBackfillService.BUCKET_MAP_CANDIDATE);
        assertNotNull(candidate);
        assertTrue(candidate.sampleRefs().contains(o1No));
        assertNullOutlet(o1, "sale_order");
        assertNullOutlet(d1, "order_draft");
        // dry-run 也按显式 report-dir 输出 JSON + Markdown
        assertNotNull(report.reportJsonPath());
        assertNotNull(report.reportMarkdownPath());
        assertTrue(Files.exists(Path.of(report.reportMarkdownPath())));
        assertTrue(Files.readString(Path.of(report.reportMarkdownPath())).contains("Outlet Backfill Report"));
    }

    @Test
    void applyUpdatesOnlySourceOutletIdWritesReportsAndIsIdempotent() throws Exception {
        OutletBackfillReport first = service.apply(approval(), List.of(
                map("御龙", "E3F-A"), map("总店", "E3F-B")));

        assertEquals("APPLY", first.mode());
        assertEquals(2, first.ordersUpdated());
        assertEquals(1, first.draftsUpdated());
        assertTrue(first.reconciliationConsistent());
        // 审计证据：operator/时间/库名/映射文件摘要/逐条决策/赋值摘要
        assertEquals("ops-dsh", first.operator());
        assertNotNull(first.startedAt());
        assertNotNull(first.finishedAt());
        assertEquals("blade_rehearsal", first.expectedDatabaseName());
        assertEquals(jdbc.queryForObject("SELECT DATABASE()", String.class), first.actualDatabaseName());
        assertEquals(sha256(mappingFile), first.mappingFileSha256());
        assertEquals(2, first.mappingDecisions().size());
        OutletBackfillReport.MappingDecision yulong = first.mappingDecisions().stream()
                .filter(d -> "御龙".equals(d.legacySourceShop())).findFirst().orElseThrow();
        assertEquals("E3F-A", yulong.outletCode());
        assertEquals(1, yulong.ordersUpdated());
        assertEquals(1, yulong.draftsUpdated());
        OutletBackfillReport.MappingDecision zongdian = first.mappingDecisions().stream()
                .filter(d -> "总店".equals(d.legacySourceShop())).findFirst().orElseThrow();
        assertEquals(1, zongdian.ordersUpdated());
        assertEquals(0, zongdian.draftsUpdated());
        assertTrue(first.before().containsKey("orderIdOutletDigest"));
        assertTrue(first.before().containsKey("draftIdOutletDigest"));
        assertTrue(first.after().containsKey("orderIdOutletDigest"));
        assertNotNull(first.reportJsonPath());
        assertNotNull(first.reportMarkdownPath());
        assertTrue(Files.size(Path.of(first.reportJsonPath())) > 0);
        assertEquals(enabledA, outletId(o1, "sale_order"));
        assertEquals(enabledB, outletId(o4, "sale_order"));
        assertEquals(enabledA, outletId(d1, "order_draft"));
        assertNullOutlet(o5Other, "sale_order"); // 其他租户不受影响
        assertEquals(enabledB, outletId(o3Conflict, "sale_order")); // 冲突不覆盖
        assertEquals(enabledB, outletId(d3Conflict, "order_draft"));
        assertNotNull(jdbc.queryForObject("SELECT confirmed_order_id FROM order_draft WHERE id=?", Long.class, d2Confirmed));
        assertEquals("御龙", jdbc.queryForObject("SELECT source_shop FROM sale_order WHERE id=?", String.class, o1));

        OutletBackfillReport second = service.apply(approval(), List.of(
                map("御龙", "E3F-A"), map("总店", "E3F-B")));
        assertEquals(0, second.ordersUpdated());
        assertEquals(0, second.draftsUpdated());
        assertEquals(2, second.ordersAlreadyApplied());
        assertEquals(1, second.draftsAlreadyApplied());
        assertTrue(second.reconciliationConsistent());
    }

    @Test
    void rejectsUnknownDisabledDeletedAndCrossTenantOutletsWithoutWriting() {
        assertThrows(BusinessException.class, () -> service.apply(approval(), List.of(map("御龙", "NO-SUCH"))));
        assertThrows(BusinessException.class, () -> service.apply(approval(), List.of(map("御龙", "E3F-D"))));
        assertThrows(BusinessException.class, () -> service.apply(approval(), List.of(map("御龙", "E3F-X"))));
        assertThrows(BusinessException.class, () -> service.apply(approval(), List.of(map("御龙", "E3F-C"))));
        assertNullOutlet(o1, "sale_order");
        assertNullOutlet(d1, "order_draft");
    }

    @Test
    void marksBatchSuspectRowsAsSkipped() {
        OutletBackfillReport report = service.preview(TENANT, null, List.of(map("TB1", "E3F-A")));

        assertEquals(1, report.ordersSuspect());
        assertEquals(0, report.ordersCandidates());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("疑似批次")));
        assertGroup(report, "sale_order", OutletBackfillService.BUCKET_SUSPECT, 1);
    }

    @Test
    void previewReportsAuditEvidenceAndMarksPreviewOperator() throws Exception {
        OutletBackfillReport defaulted = service.preview(TENANT, tempDir, List.of(map("御龙", "E3F-A")));
        assertEquals("PREVIEW", defaulted.operator());
        assertNull(defaulted.mappingFileSha256());

        OutletBackfillReport explicit = service.preview(TENANT, tempDir, List.of(map("御龙", "E3F-A")),
                "auditor-1", "blade_rehearsal", mappingFile.toString());
        assertEquals("auditor-1", explicit.operator());
        assertEquals("blade_rehearsal", explicit.expectedDatabaseName());
        assertEquals(sha256(mappingFile), explicit.mappingFileSha256());
        assertNotNull(explicit.startedAt());
        assertNotNull(explicit.finishedAt());
        assertEquals(jdbc.queryForObject("SELECT DATABASE()", String.class), explicit.actualDatabaseName());

        String markdown = Files.readString(Path.of(explicit.reportMarkdownPath()));
        assertTrue(markdown.contains("operator: auditor-1"));
        assertTrue(markdown.contains("mapping_file_sha256: " + sha256(mappingFile)));
        assertTrue(markdown.contains("expected_database: blade_rehearsal"));
        assertTrue(markdown.contains("## Mapping decisions"));
        assertTrue(markdown.contains("御龙"));
        // 报告只含审计元数据，绝不泄露连接凭据
        String json = Files.readString(Path.of(explicit.reportJsonPath())).toLowerCase();
        assertTrue(!json.contains("password"));
        assertTrue(!json.contains("jdbc:"));
    }

    @Test
    void updateHelperEnforcesTenantAndNullPredicate() {
        // 传入一个未填 ID 与一个已填（并发/冲突）ID：只有未填且同租户的行被更新
        int updated = service.updateSourceOutletId("sale_order", List.of(o1, o3Conflict), enabledA, TENANT);

        assertEquals(1, updated);
        assertEquals(enabledA, outletId(o1, "sale_order"));
        assertEquals(enabledB, outletId(o3Conflict, "sale_order"));
    }

    @Test
    void rejectsWholeBatchBeforeWritingWhenAnyMappingInvalid() {
        List<OutletBackfillMappingRow> rows = List.of(map("御龙", "E3F-A"), map("总店", "E3F-D"));
        assertThrows(BusinessException.class, () -> service.apply(approval(), rows));
        assertNullOutlet(o1, "sale_order");
    }

    // ==================== helpers ====================

    private void assertGroup(OutletBackfillReport report, String table, String bucket, long expected) {
        OutletBackfillReport.ValueGroup found = group(report, table, bucket);
        long actual = found == null ? 0 : found.count();
        assertEquals(expected, actual, table + "/" + bucket);
    }

    private void assertBucketTotal(OutletBackfillReport report, String table, String bucket, long expected) {
        long total = report.groups().stream()
                .filter(g -> g.table().equals(table) && g.bucket().equals(bucket))
                .mapToLong(OutletBackfillReport.ValueGroup::count)
                .sum();
        assertEquals(expected, total, table + "/" + bucket + " total");
    }

    private OutletBackfillReport.ValueGroup group(OutletBackfillReport report, String table, String bucket) {
        return report.groups().stream()
                .filter(g -> g.table().equals(table) && g.bucket().equals(bucket))
                .findFirst().orElse(null);
    }

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

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }
}
