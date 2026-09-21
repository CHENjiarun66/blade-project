package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Series F 历史档口回填（DATA-OUTLET-002 本地预演/副本执行）。
 *
 * <p>只更新 {@code sale_order.source_outlet_id} 与 {@code order_draft.source_outlet_id}，
 * 绝不改写 {@code source_shop} 或其它列；条件更新同时带 {@code tenant_id}、
 * {@code source_outlet_id IS NULL} 与分块 {@code id IN}；已有冲突不覆盖；疑似批次不映射；
 * dry-run 默认；apply 必须携带 {@link OutletBackfillApproval}（包内构造，仅安全闸门签发）。
 * 报告区分 sale_order/order_draft，含结构化分类/样例，并可输出 JSON + Markdown。</p>
 */
@Service
public class OutletBackfillService {

    static final String BUCKET_BLANK_NULL = "BLANK_NULL";
    static final String BUCKET_UNMAPPED = "UNMAPPED";
    static final String BUCKET_MAP_CANDIDATE = "MAP_CANDIDATE";
    static final String BUCKET_SKIP = "SKIP";
    static final String BUCKET_REVIEW = "REVIEW";
    static final String BUCKET_SUSPECT = "SUSPECT";
    static final String BUCKET_ALREADY_APPLIED = "ALREADY_APPLIED";
    static final String BUCKET_CONFLICT = "CONFLICT";
    static final String BUCKET_CONFIRMED_DRAFT_SKIPPED = "CONFIRMED_DRAFT_SKIPPED";

    private static final int CHUNK_SIZE = 500;
    private static final int SAMPLE_LIMIT = 5;

    private static final String SUSPECT_ORDER_SQL =
            "(source_shop REGEXP '^[0-9]+$' OR (source_doc_no IS NOT NULL AND TRIM(source_doc_no) <> '' "
                    + "AND TRIM(source_shop) = SUBSTRING_INDEX(source_doc_no, '_', 1)))";
    private static final String SUSPECT_DRAFT_SQL =
            "(source_shop REGEXP '^[0-9]+$' OR (source_batch_no IS NOT NULL AND TRIM(source_batch_no) <> '' "
                    + "AND TRIM(source_shop) = TRIM(source_batch_no)))";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutletBackfillService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public OutletBackfillReport preview(long tenantId, Path reportDir, List<OutletBackfillMappingRow> rows) {
        return execute(tenantId, reportDir, rows, false);
    }

    /** 仅包内可构造 approval（安全闸门签发），外部包无法绕过 gate 调用写入。 */
    @Transactional
    public OutletBackfillReport apply(OutletBackfillApproval approval, List<OutletBackfillMappingRow> rows) {
        if (approval == null) {
            throw BusinessException.of(400, "apply 需要经安全闸门签发的审批凭证");
        }
        return execute(approval.tenantId(), approval.reportDir(), rows, true);
    }

    private OutletBackfillReport execute(long tenantId, Path reportDir,
                                         List<OutletBackfillMappingRow> rows, boolean apply) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<OutletBackfillReport.ValueGroup> groups = new ArrayList<>();
        validateReportDir(reportDir, apply);
        Map<String, Long> outletIdByShop = validate(tenantId, rows, errors, warnings);
        if (!errors.isEmpty()) {
            throw BusinessException.of(400, "回填校验失败: " + String.join("; ", errors));
        }
        Set<String> mappingValues = new LinkedHashSet<>();
        for (OutletBackfillMappingRow row : rows) {
            mappingValues.add(row.legacySourceShop());
        }

        Map<String, Object> before = snapshot(tenantId);
        Counters counters = new Counters();
        collectBlankNull(tenantId, groups);
        collectUnmapped(tenantId, mappingValues, groups);

        for (OutletBackfillMappingRow row : rows) {
            if (row.isMap()) {
                processMapRow(tenantId, row, outletIdByShop.get(row.legacySourceShop()), apply, counters, groups, warnings);
            } else {
                collectDecidedRow(tenantId, row, groups);
            }
        }
        Map<String, Object> after = snapshot(tenantId);
        boolean consistent = consistent(before, after);
        if (apply && !consistent) {
            throw new IllegalStateException("回填前后对账不一致，已回滚");
        }

        long mapRows = rows.stream().filter(OutletBackfillMappingRow::isMap).count();
        OutletBackfillReport report = new OutletBackfillReport(
                apply ? "APPLY" : "PREVIEW", tenantId, rows.size(), (int) mapRows,
                (int) rows.stream().filter(r -> OutletBackfillMapping.DECISION_SKIP.equals(r.decision())).count(),
                (int) rows.stream().filter(r -> OutletBackfillMapping.DECISION_REVIEW.equals(r.decision())).count(),
                counters.ordersCandidates, counters.draftsCandidates,
                counters.ordersUpdated, counters.draftsUpdated,
                counters.ordersAlreadyApplied, counters.draftsAlreadyApplied,
                counters.ordersConflict, counters.draftsConflict,
                counters.ordersSuspect, counters.draftsSuspect,
                counters.confirmedDraftsSkipped,
                counters.ordersConcurrentSkipped, counters.draftsConcurrentSkipped,
                before, after, consistent, groups, warnings, errors, null, null);
        if (reportDir == null) {
            return report;
        }
        return writeReports(report, reportDir);
    }

    private void validateReportDir(Path reportDir, boolean apply) {
        if (reportDir == null) {
            if (apply) {
                throw BusinessException.of(400, "apply 必须显式提供 report-dir");
            }
            return;
        }
        try {
            Files.createDirectories(reportDir);
        } catch (IOException e) {
            throw BusinessException.of(400, "report-dir 无法创建: " + reportDir);
        }
        if (!Files.isDirectory(reportDir) || !Files.isWritable(reportDir)) {
            throw BusinessException.of(400, "report-dir 不可写: " + reportDir);
        }
    }

    private Map<String, Long> validate(long tenantId,
                                       List<OutletBackfillMappingRow> rows,
                                       List<String> errors,
                                       List<String> warnings) {
        Map<String, Long> outletIdByShop = new LinkedHashMap<>();
        for (OutletBackfillMappingRow row : rows) {
            if (row.tenantId() != tenantId) {
                errors.add("映射行租户 " + row.tenantId() + " 与请求租户 " + tenantId + " 不一致: " + row.legacySourceShop());
                continue;
            }
            if (!row.isMap()) {
                continue;
            }
            List<Map<String, Object>> outlets = jdbc.queryForList(
                    "SELECT id, status, deleted FROM sales_outlet WHERE tenant_id=? AND outlet_code=?",
                    tenantId, row.outletCode());
            if (outlets.isEmpty()) {
                errors.add("档口不存在或跨租户: " + row.outletCode());
                continue;
            }
            Map<String, Object> outlet = outlets.get(0);
            if (Long.valueOf(1L).equals(((Number) outlet.get("deleted")).longValue())) {
                errors.add("档口已删除: " + row.outletCode());
                continue;
            }
            if (!Long.valueOf(1L).equals(((Number) outlet.get("status")).longValue())) {
                errors.add("档口已禁用，不允许回填: " + row.outletCode());
                continue;
            }
            outletIdByShop.put(row.legacySourceShop(), ((Number) outlet.get("id")).longValue());
        }
        return outletIdByShop;
    }

    private void processMapRow(long tenantId,
                               OutletBackfillMappingRow row,
                               Long outletId,
                               boolean apply,
                               Counters counters,
                               List<OutletBackfillReport.ValueGroup> groups,
                               List<String> warnings) {
        String shop = row.legacySourceShop();

        List<Long> orderIds = candidateIds("sale_order", tenantId, shop);
        List<Long> draftIds = candidateIds("order_draft", tenantId, shop);
        counters.ordersCandidates += orderIds.size();
        counters.draftsCandidates += draftIds.size();
        addGroup(groups, "sale_order", BUCKET_MAP_CANDIDATE, shop, orderIds.size(),
                orderSamples(tenantId, "source_outlet_id IS NULL AND TRIM(source_shop)=? AND NOT " + SUSPECT_ORDER_SQL, shop));
        addGroup(groups, "order_draft", BUCKET_MAP_CANDIDATE, shop, draftIds.size(),
                draftSamples(tenantId, "source_outlet_id IS NULL AND confirmed_order_id IS NULL AND TRIM(source_shop)=? AND NOT " + SUSPECT_DRAFT_SQL, shop));

        int orderSuspect = count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND " + SUSPECT_ORDER_SQL, tenantId, shop);
        int draftSuspect = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND " + SUSPECT_DRAFT_SQL, tenantId, shop);
        counters.ordersSuspect += orderSuspect;
        counters.draftsSuspect += draftSuspect;
        if (orderSuspect > 0 || draftSuspect > 0) {
            warnings.add("映射值疑似批次/编号，已跳过 " + (orderSuspect + draftSuspect) + " 行: " + shop);
        }
        addGroup(groups, "sale_order", BUCKET_SUSPECT, shop, orderSuspect,
                orderSamples(tenantId, "TRIM(source_shop)=? AND " + SUSPECT_ORDER_SQL, shop));
        addGroup(groups, "order_draft", BUCKET_SUSPECT, shop, draftSuspect,
                draftSamples(tenantId, "TRIM(source_shop)=? AND " + SUSPECT_DRAFT_SQL, shop));

        int orderAlready = count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id=?", tenantId, shop, outletId);
        int draftAlready = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id=?", tenantId, shop, outletId);
        counters.ordersAlreadyApplied += orderAlready;
        counters.draftsAlreadyApplied += draftAlready;
        addGroup(groups, "sale_order", BUCKET_ALREADY_APPLIED, shop, orderAlready,
                orderSamples(tenantId, "TRIM(source_shop)=? AND source_outlet_id=?", shop, outletId));
        addGroup(groups, "order_draft", BUCKET_ALREADY_APPLIED, shop, draftAlready,
                draftSamples(tenantId, "TRIM(source_shop)=? AND source_outlet_id=?", shop, outletId));

        int rowOrderConflict = count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id IS NOT NULL AND source_outlet_id<>?",
                tenantId, shop, outletId);
        int rowDraftConflict = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id IS NOT NULL AND source_outlet_id<>?",
                tenantId, shop, outletId);
        counters.ordersConflict += rowOrderConflict;
        counters.draftsConflict += rowDraftConflict;
        addGroup(groups, "sale_order", BUCKET_CONFLICT, shop, rowOrderConflict,
                orderSamples(tenantId, "TRIM(source_shop)=? AND source_outlet_id IS NOT NULL AND source_outlet_id<>?",
                        shop, outletId));
        addGroup(groups, "order_draft", BUCKET_CONFLICT, shop, rowDraftConflict,
                draftSamples(tenantId, "TRIM(source_shop)=? AND source_outlet_id IS NOT NULL AND source_outlet_id<>?",
                        shop, outletId));
        if (rowOrderConflict > 0 || rowDraftConflict > 0) {
            warnings.add("映射值与既有 source_outlet_id 冲突，未覆盖: " + shop);
        }

        int confirmedSkipped = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND source_outlet_id IS NULL AND confirmed_order_id IS NOT NULL AND TRIM(source_shop)=? "
                + "AND NOT " + SUSPECT_DRAFT_SQL, tenantId, shop);
        counters.confirmedDraftsSkipped += confirmedSkipped;
        addGroup(groups, "order_draft", BUCKET_CONFIRMED_DRAFT_SKIPPED, shop, confirmedSkipped,
                draftSamples(tenantId, "source_outlet_id IS NULL AND confirmed_order_id IS NOT NULL "
                        + "AND TRIM(source_shop)=? AND NOT " + SUSPECT_DRAFT_SQL, shop));

        if (orderIds.isEmpty() && draftIds.isEmpty()
                && count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 AND TRIM(source_shop)=?", tenantId, shop) == 0
                && count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 AND TRIM(source_shop)=?", tenantId, shop) == 0) {
            warnings.add("映射值在库中无匹配数据: " + shop);
        }
        if (apply) {
            int orderUpdated = updateSourceOutletId("sale_order", orderIds, outletId, tenantId);
            int draftUpdated = updateSourceOutletId("order_draft", draftIds, outletId, tenantId);
            counters.ordersUpdated += orderUpdated;
            counters.draftsUpdated += draftUpdated;
            int orderConcurrent = orderIds.size() - orderUpdated;
            int draftConcurrent = draftIds.size() - draftUpdated;
            if (orderConcurrent > 0 || draftConcurrent > 0) {
                // 候选选取后被并发填入：条件更新已跳过，按冲突记录，绝不覆盖。
                counters.ordersConcurrentSkipped += orderConcurrent;
                counters.draftsConcurrentSkipped += draftConcurrent;
                counters.ordersConflict += orderConcurrent;
                counters.draftsConflict += draftConcurrent;
                warnings.add("检测到并发写入，已跳过 " + (orderConcurrent + draftConcurrent) + " 行: " + shop);
            }
        }
    }

    private void collectDecidedRow(long tenantId, OutletBackfillMappingRow row,
                                   List<OutletBackfillReport.ValueGroup> groups) {
        String bucket = OutletBackfillMapping.DECISION_SKIP.equals(row.decision()) ? BUCKET_SKIP : BUCKET_REVIEW;
        int orderCount = count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 AND TRIM(source_shop)=?",
                tenantId, row.legacySourceShop());
        int draftCount = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 AND TRIM(source_shop)=?",
                tenantId, row.legacySourceShop());
        addGroup(groups, "sale_order", bucket, row.legacySourceShop(), orderCount,
                orderSamples(tenantId, "TRIM(source_shop)=?", row.legacySourceShop()));
        addGroup(groups, "order_draft", bucket, row.legacySourceShop(), draftCount,
                draftSamples(tenantId, "TRIM(source_shop)=?", row.legacySourceShop()));
    }

    private void collectBlankNull(long tenantId, List<OutletBackfillReport.ValueGroup> groups) {
        int orders = count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND (source_shop IS NULL OR TRIM(source_shop)='')", tenantId);
        int drafts = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND (source_shop IS NULL OR TRIM(source_shop)='')", tenantId);
        addGroup(groups, "sale_order", BUCKET_BLANK_NULL, "<blank_or_null>", orders, List.of());
        addGroup(groups, "order_draft", BUCKET_BLANK_NULL, "<blank_or_null>", drafts, List.of());
    }

    private void collectUnmapped(long tenantId, Set<String> mappingValues,
                                 List<OutletBackfillReport.ValueGroup> groups) {
        collectUnmappedTable("sale_order", "order_no", tenantId, mappingValues, groups);
        collectUnmappedTable("order_draft", "external_ref_no", tenantId, mappingValues, groups);
    }

    private void collectUnmappedTable(String table, String refColumn, long tenantId,
                                      Set<String> mappingValues,
                                      List<OutletBackfillReport.ValueGroup> groups) {
        StringBuilder sql = new StringBuilder("SELECT TRIM(source_shop) v, COUNT(*) c FROM ").append(table)
                .append(" WHERE tenant_id=? AND deleted=0 AND source_shop IS NOT NULL AND TRIM(source_shop)<>''");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (!mappingValues.isEmpty()) {
            sql.append(" AND TRIM(source_shop) NOT IN (")
                    .append(String.join(",", Collections.nCopies(mappingValues.size(), "?"))).append(")");
            args.addAll(mappingValues);
        }
        sql.append(" GROUP BY TRIM(source_shop) ORDER BY TRIM(source_shop)");
        List<OutletBackfillReport.ValueGroup> rows = jdbc.query(sql.toString(),
                (rs, rowNum) -> new OutletBackfillReport.ValueGroup(
                        table, BUCKET_UNMAPPED, rs.getString(1), rs.getLong(2), List.of()),
                args.toArray());
        for (OutletBackfillReport.ValueGroup row : rows) {
            List<String> samples = "sale_order".equals(table)
                    ? orderSamples(tenantId, "TRIM(source_shop)=?", row.value())
                    : draftSamples(tenantId, "TRIM(source_shop)=?", row.value());
            addGroup(groups, table, BUCKET_UNMAPPED, row.value(), row.count(), samples);
        }
    }

    private List<Long> candidateIds(String table, long tenantId, String shop) {
        boolean draft = "order_draft".equals(table);
        StringBuilder sql = new StringBuilder("SELECT id FROM ").append(table)
                .append(" WHERE tenant_id=? AND deleted=0 AND source_outlet_id IS NULL");
        if (draft) {
            sql.append(" AND confirmed_order_id IS NULL");
        }
        sql.append(" AND TRIM(source_shop)=? AND NOT ").append(draft ? SUSPECT_DRAFT_SQL : SUSPECT_ORDER_SQL);
        return jdbc.queryForList(sql.toString(), Long.class, tenantId, shop);
    }

    int updateSourceOutletId(String table, List<Long> ids, Long outletId, long tenantId) {
        if (ids.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (int start = 0; start < ids.size(); start += CHUNK_SIZE) {
            List<Long> chunk = ids.subList(start, Math.min(start + CHUNK_SIZE, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            Object[] args = new Object[chunk.size() + 2];
            args[0] = outletId;
            args[1] = tenantId;
            for (int i = 0; i < chunk.size(); i++) {
                args[i + 2] = chunk.get(i);
            }
            updated += jdbc.update("UPDATE " + table + " SET source_outlet_id=? "
                    + "WHERE tenant_id=? AND source_outlet_id IS NULL AND id IN (" + placeholders + ")", args);
        }
        return updated;
    }

    private void addGroup(List<OutletBackfillReport.ValueGroup> groups, String table, String bucket,
                          String value, long count, List<String> samples) {
        if (count <= 0) {
            return;
        }
        groups.add(new OutletBackfillReport.ValueGroup(table, bucket, value, count,
                samples.size() > SAMPLE_LIMIT ? samples.subList(0, SAMPLE_LIMIT) : samples));
    }

    private List<String> orderSamples(long tenantId, String where, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = tenantId;
        System.arraycopy(args, 0, all, 1, args.length);
        return jdbc.query("SELECT order_no FROM sale_order WHERE tenant_id=? AND deleted=0 AND "
                + where + " ORDER BY id LIMIT " + SAMPLE_LIMIT,
                (rs, rowNum) -> rs.getString(1), all);
    }

    private List<String> draftSamples(long tenantId, String where, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = tenantId;
        System.arraycopy(args, 0, all, 1, args.length);
        return jdbc.query("SELECT external_ref_no FROM order_draft WHERE tenant_id=? AND deleted=0 AND "
                + where + " ORDER BY id LIMIT " + SAMPLE_LIMIT,
                (rs, rowNum) -> rs.getString(1), all);
    }

    private Map<String, Object> snapshot(long tenantId) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("saleOrderCount", count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderDraftCount", count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderTotalAmount", amount("SELECT COALESCE(SUM(total_amount),0) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderGrossReceived", amount("SELECT COALESCE(SUM(gross_received_amount),0) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderNetReceived", amount("SELECT COALESCE(SUM(net_received_amount),0) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderCashRefund", amount("SELECT COALESCE(SUM(cash_refund_amount),0) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderSalesReturn", amount("SELECT COALESCE(SUM(sales_return_amount),0) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderWriteOff", amount("SELECT COALESCE(SUM(write_off_amount),0) FROM sale_order WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("orderStatusDigest", digest("SELECT status, COUNT(*) cnt FROM sale_order WHERE tenant_id=? AND deleted=0 GROUP BY status ORDER BY status", tenantId));
        snap.put("sourceShopDigest", sourceShopDigest(tenantId));
        snap.put("orderIdShopDigest", idShopDigest("sale_order", tenantId));
        snap.put("draftIdShopDigest", idShopDigest("order_draft", tenantId));
        snap.put("orderItemCount", count("SELECT COUNT(*) FROM sale_order_item i JOIN sale_order o ON o.id=i.order_id "
                + "WHERE o.tenant_id=? AND o.deleted=0", tenantId));
        snap.put("draftItemCount", count("SELECT COUNT(*) FROM order_draft_item i JOIN order_draft d ON d.id=i.draft_id "
                + "WHERE d.tenant_id=? AND d.deleted=0", tenantId));
        snap.put("fileBindCount", count("SELECT COUNT(*) FROM file_business_bind WHERE tenant_id=? AND deleted=0", tenantId));
        snap.put("sourceOutletNullCount", count("SELECT (SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 AND source_outlet_id IS NULL) "
                + "+ (SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 AND source_outlet_id IS NULL)", tenantId, tenantId));
        snap.put("sourceOutletFilledCount", count("SELECT (SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 AND source_outlet_id IS NOT NULL) "
                + "+ (SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 AND source_outlet_id IS NOT NULL)", tenantId, tenantId));
        return snap;
    }

    private boolean consistent(Map<String, Object> before, Map<String, Object> after) {
        for (String key : before.keySet()) {
            if (key.equals("sourceOutletNullCount") || key.equals("sourceOutletFilledCount")) {
                continue;
            }
            if (!Objects.equals(before.get(key), after.get(key))) {
                return false;
            }
        }
        return true;
    }

    private OutletBackfillReport writeReports(OutletBackfillReport report, Path reportDir) {
        String base = "outlet-backfill-" + report.mode().toLowerCase() + "-tenant" + report.tenantId() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path json = unique(reportDir, base, ".json");
        Path markdown = unique(reportDir, base, ".md");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), report);
            Files.writeString(markdown, toMarkdown(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw BusinessException.of(500, "报告写入失败: " + e.getMessage());
        }
        return new OutletBackfillReport(
                report.mode(), report.tenantId(), report.mappingRows(), report.mapRows(), report.skipRows(),
                report.reviewRows(), report.ordersCandidates(), report.draftsCandidates(),
                report.ordersUpdated(), report.draftsUpdated(), report.ordersAlreadyApplied(),
                report.draftsAlreadyApplied(), report.ordersConflict(), report.draftsConflict(),
                report.ordersSuspect(), report.draftsSuspect(), report.confirmedDraftsSkipped(),
                report.ordersConcurrentSkipped(), report.draftsConcurrentSkipped(),
                report.before(), report.after(), report.reconciliationConsistent(), report.groups(),
                report.warnings(), report.errors(), json.toString(), markdown.toString());
    }

    private Path unique(Path dir, String base, String suffix) {
        Path candidate = dir.resolve(base + suffix);
        int index = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "-" + index + suffix);
            index++;
        }
        return candidate;
    }

    private String toMarkdown(OutletBackfillReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Outlet Backfill Report (").append(report.mode()).append(")\n\n");
        md.append("- tenant_id: ").append(report.tenantId()).append("\n");
        md.append("- mapping rows: ").append(report.mappingRows())
                .append(" (MAP ").append(report.mapRows())
                .append(" / SKIP ").append(report.skipRows())
                .append(" / REVIEW ").append(report.reviewRows()).append(")\n");
        md.append("- orders candidate/updated/conflict/suspect: ")
                .append(report.ordersCandidates()).append("/").append(report.ordersUpdated()).append("/")
                .append(report.ordersConflict()).append("/").append(report.ordersSuspect()).append("\n");
        md.append("- drafts candidate/updated/conflict/suspect: ")
                .append(report.draftsCandidates()).append("/").append(report.draftsUpdated()).append("/")
                .append(report.draftsConflict()).append("/").append(report.draftsSuspect()).append("\n");
        md.append("- confirmed drafts skipped: ").append(report.confirmedDraftsSkipped()).append("\n");
        md.append("- reconciliation consistent: ").append(report.reconciliationConsistent()).append("\n\n");
        md.append("## Groups\n\n| table | bucket | value | count | sample refs |\n|---|---|---|---|---|\n");
        for (OutletBackfillReport.ValueGroup group : report.groups()) {
            md.append("| ").append(group.table()).append(" | ").append(group.bucket()).append(" | ")
                    .append(group.value()).append(" | ").append(group.count()).append(" | ")
                    .append(String.join(", ", group.sampleRefs())).append(" |\n");
        }
        if (!report.warnings().isEmpty()) {
            md.append("\n## Warnings\n\n");
            report.warnings().forEach(w -> md.append("- ").append(w).append("\n"));
        }
        return md.toString();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal amount(String sql, Object... args) {
        return jdbc.queryForObject(sql, java.math.BigDecimal.class, args);
    }

    private String digest(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> rs.getString(1) + "=" + rs.getLong(2), args).toString();
    }

    private String sourceShopDigest(long tenantId) {
        String order = digest("SELECT COALESCE(source_shop,'<NULL>') shop, COUNT(*) cnt FROM sale_order "
                + "WHERE tenant_id=? AND deleted=0 GROUP BY source_shop ORDER BY shop", tenantId);
        String draft = digest("SELECT COALESCE(source_shop,'<NULL>') shop, COUNT(*) cnt FROM order_draft "
                + "WHERE tenant_id=? AND deleted=0 GROUP BY source_shop ORDER BY shop", tenantId);
        return "O:" + order + ";D:" + draft;
    }

    /** id + source_shop 的稳定 SHA-256，保证分布相同但值交换也无法蒙混。 */
    private String idShopDigest(String table, long tenantId) {
        List<String> rows = jdbc.query("SELECT id, COALESCE(source_shop,'<NULL>') FROM " + table
                        + " WHERE tenant_id=? AND deleted=0 ORDER BY id",
                (rs, rowNum) -> rs.getLong(1) + ":" + rs.getString(2), tenantId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String row : rows) {
                digest.update(row.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static class Counters {
        private int ordersCandidates;
        private int draftsCandidates;
        private int ordersUpdated;
        private int draftsUpdated;
        private int ordersAlreadyApplied;
        private int draftsAlreadyApplied;
        private int ordersConflict;
        private int draftsConflict;
        private int ordersSuspect;
        private int draftsSuspect;
        private int confirmedDraftsSkipped;
        private int ordersConcurrentSkipped;
        private int draftsConcurrentSkipped;
    }
}
