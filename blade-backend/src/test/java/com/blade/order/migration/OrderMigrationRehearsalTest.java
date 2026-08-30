package com.blade.order.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 历史迁移预演测试（SOW-7，真实隔离库）：
 * 合成历史数据 → dry-run（不写库）→ execute（写库）→ 幂等重放 → 不变量核对。
 * 生产 V42 副本预演由 Codex/发布阶段执行（本机无 V42 备份，见交付报告待办）。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderMigrationRehearsalTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment env;

    private OrderLegacyMigrator migrator() throws Exception {
        String url = env.getProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");
        return new OrderLegacyMigrator(url, user, password);
    }

    private Long seedLegacy(String suffix, Integer legacyStatus, String total, String paid, String writeOff,
                            int planCount, int deliveryDone, Integer isDelivered) {
        String orderNo = "ORDMIG" + suffix + System.nanoTime();
        jdbc.update("""
                INSERT INTO sale_order (order_no, order_date, order_type, customer_name, total_amount, original_amount,
                  total_cost_amount, gross_profit, freight_amount, freight_cost, paid_amount, payment_status,
                  deposit_amount, write_off_amount, refund_amount, sales_return_amount, gross_received_amount,
                  cash_refund_amount, net_received_amount, balance_amount, need_delivery, is_delivered,
                  fulfillment_status, fulfillment_mode, collection_status, version, deleted)
                VALUES (?, CURDATE(), 'SPOT', '迁移预演客户', ?, ?, 0, ?, 0, 0, ?, 0, 0, ?, 0, 0, 0, 0, 0, 0, 0, ?,
                        NULL, 'UNDECIDED', NULL, 0, 0)
                """, orderNo, new BigDecimal(total), new BigDecimal(total), new BigDecimal(total),
                new BigDecimal(paid), new BigDecimal(writeOff), isDelivered);
        Long id = jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no = ?", Long.class, orderNo);
        for (int i = 0; i < planCount; i++) {
            jdbc.update("""
                    INSERT INTO order_delivery_plan (order_id, order_item_id, sku_id, planned_qty, allocated_qty, out_qty, status, tenant_id)
                    VALUES (?, NULL, 1, 1, 1, 0, 'ALLOCATED', 1)
                    """, id);
        }
        for (int i = 0; i < deliveryDone; i++) {
            jdbc.update("""
                    INSERT INTO order_delivery (delivery_no, order_id, warehouse_id, status, total_quantity, tenant_id)
                    VALUES (?, ?, 1, 2, 1, 1)
                    """, "OUTMIG" + suffix + i + System.nanoTime(), id);
        }
        jdbc.update("UPDATE sale_order SET status = ? WHERE id = ?", legacyStatus, id);
        return id;
    }

    @Test
    void dryRunThenExecuteThenReplay_isIdempotentAndConsistent() throws Exception {
        // 合成历史样本
        Long settled = seedLegacy("S1", 0, "100.00", "100.00", "0.00", 0, 0, 0);
        Long partial = seedLegacy("P1", 0, "100.00", "40.00", "0.00", 0, 0, 0);
        Long cancelled = seedLegacy("C1", 6, "100.00", "100.00", "0.00", 0, 0, 0);
        Long returning = seedLegacy("R1", 7, "100.00", "0.00", "0.00", 0, 0, 0);
        Long deliveredNoPay = seedLegacy("D1", 0, "100.00", "0.00", "0.00", 0, 1, 1);
        OrderLegacyMigrator migrator = migrator();

        // ── dry-run：只出报告，不写库 ──
        OrderLegacyMigrator.Report dry = migrator.migrate(1L, false);
        assertEquals(0, countStatus(settled, "fulfillment_status"), "dry-run 不得写库");
        assertTrue(dry.migrated > 0);
        assertEquals(dry.totalOrders, dry.migrated + dry.manualReview + dry.skipped);

        // ── 映射判定（dry-run 报告内核对） ──
        assertMapping(dry, settled, "COMPLETED", "RECORD_ONLY", "SETTLED");
        assertMapping(dry, partial, "CONFIRMED", "UNDECIDED", "PARTIAL");
        // 取消订单按其金额事实映射（迁移只看证据，不重解释经营口径）
        assertMapping(dry, cancelled, "COMPLETED", "RECORD_ONLY", "SETTLED");
        assertTrue(dry.results.stream().anyMatch(r -> r.orderId() == returning && "MANUAL_REVIEW".equals(r.decision())),
                "status=7/8 必须进人工核对");
        assertTrue(dry.results.stream().anyMatch(r -> r.orderId() == deliveredNoPay && "MANUAL_REVIEW".equals(r.decision())),
                "出库证据与无收款冲突必须进人工核对");

        // ── execute：写库 + 期初流水 + 日志 ──
        OrderLegacyMigrator.Report executed = migrator.migrate(1L, true);
        assertTrue(executed.migrated > 0);
        assertEquals("COMPLETED", getStatus(settled, "fulfillment_status"));
        assertEquals("RECORD_ONLY", getStatus(settled, "fulfillment_mode"));
        assertEquals("SETTLED", getStatus(settled, "collection_status"));
        assertEquals("MIGRATION_CONFIRMED", getStatus(settled, "settlement_method"));
        assertEquals(0, getPaid(settled).compareTo(new BigDecimal("100.00")));
        assertEquals("CONFIRMED", getStatus(partial, "fulfillment_status"));
        assertEquals("PARTIAL", getStatus(partial, "collection_status"));

        Integer openings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_financial_record WHERE order_id = ? AND record_type = 'MIGRATION_OPENING'",
                Integer.class, settled);
        assertEquals(1, openings, "已结清历史订单必须落一笔 MIGRATION_OPENING");
        Integer logs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_state_transition_log WHERE order_id = ? AND action = 'migrate'",
                Integer.class, settled);
        assertEquals(1, logs, "迁移必须写状态流转日志");

        // ── 幂等重放：第二次执行不再变更/重复 ──
        int openingsBefore = openings;
        OrderLegacyMigrator.Report replay = migrator.migrate(1L, true);
        assertEquals(0, replay.migrated, "重放时全部订单应已迁移跳过或人工核对，不重复迁移");
        assertTrue(replay.skipped > 0);
        assertEquals(openingsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_financial_record WHERE order_id = ? AND record_type = 'MIGRATION_OPENING'",
                Integer.class, settled), "重放不得重复落期初流水");

        // ── 不变量：迁移不改变订单原始金额总量 ──
        assertEquals(0, executed.sumTotalBefore.compareTo(executed.sumTotalAfter),
                "迁移前后订单金额总量必须一致");
    }

    private void assertMapping(OrderLegacyMigrator.Report report, Long orderId,
                               String fulfillment, String mode, String collection) {
        assertMapping(report, orderId, fulfillment, mode, collection, null);
    }

    private void assertMapping(OrderLegacyMigrator.Report report, Long orderId,
                               String fulfillment, String mode, String collection, String noteContains) {
        List<OrderLegacyMigrator.OrderResult> matches = report.results.stream()
                .filter(r -> r.orderId() == orderId && "MIGRATED".equals(r.decision()))
                .toList();
        assertEquals(1, matches.size(), "订单 " + orderId + " 应恰好出现一次迁移结果，实际结果=" + report.results);
        if (matches.isEmpty()) return;
        OrderLegacyMigrator.OrderResult r = matches.get(0);
        assertEquals(fulfillment, r.toFulfillment());
        assertEquals(mode, r.toMode());
        assertEquals(collection, r.toCollection());
        if (noteContains != null) {
            assertTrue(r.note().contains(noteContains) || r.evidence().contains(noteContains));
        }
    }

    private String getStatus(Long orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM sale_order WHERE id = ?", String.class, orderId);
    }

    private BigDecimal getPaid(Long orderId) {
        return jdbc.queryForObject("SELECT gross_received_amount FROM sale_order WHERE id = ?", BigDecimal.class, orderId);
    }

    private int countStatus(Long orderId, String column) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE id = ? AND " + column + " IS NOT NULL", Integer.class, orderId);
        return n == null ? 0 : n;
    }
}
