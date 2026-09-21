package com.blade.outlet.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blade.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Series F 历史档口回填（DATA-OUTLET-002 本地预演/副本执行）。
 *
 * <p>只更新 {@code sale_order.source_outlet_id} 与 {@code order_draft.source_outlet_id}，
 * 绝不改写 {@code source_shop} 或其它列；已有 {@code source_outlet_id} 冲突不覆盖；
 * 纯数字/批次疑似行不更新；apply 在单事务内做前后对账，不一致回滚。dry-run 为默认。</p>
 */
@Service
@RequiredArgsConstructor
public class OutletBackfillService {

    private static final String SUSPECT_ORDER_SQL =
            "(source_shop REGEXP '^[0-9]+$' OR (source_doc_no IS NOT NULL AND TRIM(source_doc_no) <> '' "
                    + "AND TRIM(source_shop) = SUBSTRING_INDEX(source_doc_no, '_', 1)))";
    private static final String SUSPECT_DRAFT_SQL =
            "(source_shop REGEXP '^[0-9]+$' OR (source_batch_no IS NOT NULL AND TRIM(source_batch_no) <> '' "
                    + "AND TRIM(source_shop) = TRIM(source_batch_no)))";

    private final JdbcTemplate jdbc;

    public OutletBackfillReport preview(long tenantId, List<OutletBackfillMappingRow> rows) {
        return execute(tenantId, rows, false);
    }

    @Transactional
    public OutletBackfillReport apply(long tenantId, List<OutletBackfillMappingRow> rows) {
        return execute(tenantId, rows, true);
    }

    private OutletBackfillReport execute(long tenantId, List<OutletBackfillMappingRow> rows, boolean apply) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Long> outletIdByShop = validate(tenantId, rows, errors, warnings);
        if (!errors.isEmpty()) {
            throw BusinessException.of(400, "回填校验失败: " + String.join("; ", errors));
        }

        Map<String, Object> before = snapshot(tenantId);
        Counters counters = new Counters();
        for (OutletBackfillMappingRow row : rows) {
            if (row.isMap()) {
                processRow(tenantId, row, outletIdByShop.get(row.legacySourceShop()), apply, counters, warnings);
            }
        }
        Map<String, Object> after = snapshot(tenantId);
        boolean consistent = consistent(before, after);
        if (apply && !consistent) {
            throw new IllegalStateException("回填前后对账不一致，已回滚");
        }

        long mapRows = rows.stream().filter(OutletBackfillMappingRow::isMap).count();
        return new OutletBackfillReport(
                apply ? "APPLY" : "PREVIEW", tenantId, rows.size(), (int) mapRows,
                (int) rows.stream().filter(r -> OutletBackfillMapping.DECISION_SKIP.equals(r.decision())).count(),
                (int) rows.stream().filter(r -> OutletBackfillMapping.DECISION_REVIEW.equals(r.decision())).count(),
                counters.ordersCandidates, counters.draftsCandidates,
                counters.ordersUpdated, counters.draftsUpdated,
                counters.ordersAlreadyApplied, counters.draftsAlreadyApplied,
                counters.ordersConflict, counters.draftsConflict,
                counters.ordersSuspect, counters.draftsSuspect,
                counters.confirmedDraftsSkipped,
                before, after, consistent, warnings, errors);
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

    private void processRow(long tenantId,
                            OutletBackfillMappingRow row,
                            Long outletId,
                            boolean apply,
                            Counters counters,
                            List<String> warnings) {
        String shop = row.legacySourceShop();
        int ordersSuspect = count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND " + SUSPECT_ORDER_SQL, tenantId, shop);
        int draftsSuspect = count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND " + SUSPECT_DRAFT_SQL, tenantId, shop);
        counters.ordersSuspect += ordersSuspect;
        counters.draftsSuspect += draftsSuspect;
        if (ordersSuspect > 0 || draftsSuspect > 0) {
            warnings.add("映射值疑似批次/编号，已跳过 " + (ordersSuspect + draftsSuspect) + " 行: " + shop);
        }

        counters.ordersAlreadyApplied += count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id=?", tenantId, shop, outletId);
        counters.draftsAlreadyApplied += count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id=?", tenantId, shop, outletId);
        counters.ordersConflict += count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id IS NOT NULL AND source_outlet_id<>?",
                tenantId, shop, outletId);
        counters.draftsConflict += count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND TRIM(source_shop)=? AND source_outlet_id IS NOT NULL AND source_outlet_id<>?",
                tenantId, shop, outletId);
        if (counters.ordersConflict > 0 || counters.draftsConflict > 0) {
            warnings.add("映射值与既有 source_outlet_id 冲突，未覆盖: " + shop);
        }

        List<Long> orderIds = ids("SELECT id FROM sale_order WHERE tenant_id=? AND deleted=0 "
                + "AND source_outlet_id IS NULL AND TRIM(source_shop)=? AND NOT " + SUSPECT_ORDER_SQL, tenantId, shop);
        List<Long> draftIds = ids("SELECT id FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND source_outlet_id IS NULL AND confirmed_order_id IS NULL AND TRIM(source_shop)=? "
                + "AND NOT " + SUSPECT_DRAFT_SQL, tenantId, shop);
        counters.confirmedDraftsSkipped += count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 "
                + "AND source_outlet_id IS NULL AND confirmed_order_id IS NOT NULL AND TRIM(source_shop)=? "
                + "AND NOT " + SUSPECT_DRAFT_SQL, tenantId, shop);

        counters.ordersCandidates += orderIds.size();
        counters.draftsCandidates += draftIds.size();
        if (orderIds.isEmpty() && draftIds.isEmpty()
                && count("SELECT COUNT(*) FROM sale_order WHERE tenant_id=? AND deleted=0 AND TRIM(source_shop)=?",
                        tenantId, shop) == 0
                && count("SELECT COUNT(*) FROM order_draft WHERE tenant_id=? AND deleted=0 AND TRIM(source_shop)=?",
                        tenantId, shop) == 0) {
            warnings.add("映射值在库中无匹配数据: " + shop);
        }
        if (apply) {
            counters.ordersUpdated += updateSourceOutletId("sale_order", orderIds, outletId);
            counters.draftsUpdated += updateSourceOutletId("order_draft", draftIds, outletId);
        }
    }

    private int updateSourceOutletId(String table, List<Long> ids, Long outletId) {
        if (ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 1];
        args[0] = outletId;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        return jdbc.update("UPDATE " + table + " SET source_outlet_id=? WHERE id IN (" + placeholders + ")", args);
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

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private java.math.BigDecimal amount(String sql, Object... args) {
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

    private List<Long> ids(String sql, Object... args) {
        return jdbc.queryForList(sql, Long.class, args);
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
    }
}
