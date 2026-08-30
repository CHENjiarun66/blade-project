package com.blade.order.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 历史迁移预演测试（SOW-7，真实隔离库）——终审 P0-5 整改覆盖：
 * dry-run 与 execute 同决策、execute 单事务（中途故障整体回滚）、幂等重放、
 * refund_amount 人工核对、取消订单映射、WRITE_OFF 期初、旧字段投影一致。
 * 生产 V42 副本预演由 Codex/发布阶段执行（本机无 V42 备份，见交付报告待办）。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderMigrationRehearsalTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment env;

    private OrderLegacyMigrator migrator() throws Exception {
        return new OrderLegacyMigrator(
                env.getProperty("spring.datasource.url"),
                env.getProperty("spring.datasource.username"),
                env.getProperty("spring.datasource.password"));
    }

    private Long seedLegacy(String suffix, Integer legacyStatus, String total, String paid,
                            String writeOff, String refund, int planCount, int deliveryDone, Integer isDelivered) {
        String orderNo = "ORDMIG" + suffix + System.nanoTime();
        jdbc.update("""
                INSERT INTO sale_order (order_no, order_date, order_type, customer_name, total_amount, original_amount,
                  total_cost_amount, gross_profit, freight_amount, freight_cost, paid_amount, payment_status,
                  deposit_amount, write_off_amount, refund_amount, sales_return_amount, gross_received_amount,
                  cash_refund_amount, net_received_amount, balance_amount, need_delivery, is_delivered,
                  fulfillment_status, fulfillment_mode, collection_status, version, deleted)
                VALUES (?, CURDATE(), 'SPOT', '迁移预演客户', ?, ?, 0, ?, 0, 0, ?, 0, 0, ?, ?, 0, 0, 0, 0, 0, 0, ?,
                        NULL, 'UNDECIDED', NULL, 0, 0)
                """, orderNo, new BigDecimal(total), new BigDecimal(total), new BigDecimal(total),
                new BigDecimal(paid), new BigDecimal(writeOff), new BigDecimal(refund), isDelivered);
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

    private OrderLegacyMigrator.OrderResult findDecision(OrderLegacyMigrator.Report report, Long orderId) {
        return report.results.stream().filter(r -> r.orderId() == orderId).findFirst().orElseThrow();
    }

    private String getStatus(Long orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM sale_order WHERE id = ?", String.class, orderId);
    }

    private int countRecord(Long orderId, String recordType) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_financial_record WHERE order_id = ? AND record_type = ?",
                Integer.class, orderId, recordType);
        return n == null ? 0 : n;
    }

    private int countStatus(Long orderId, String column) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE id = ? AND " + column + " IS NOT NULL", Integer.class, orderId);
        return n == null ? 0 : n;
    }

    @Test
    void dryRunExecuteReplay_mappingGuards() throws Exception {
        Long settled = seedLegacy("S1", 0, "100.00", "100.00", "0.00", "0.00", 0, 0, 0);
        Long partial = seedLegacy("P1", 0, "100.00", "40.00", "0.00", "0.00", 0, 0, 0);
        Long cancelled = seedLegacy("C1", 6, "100.00", "100.00", "0.00", "0.00", 0, 0, 0);
        Long returning = seedLegacy("R1", 7, "100.00", "0.00", "0.00", "0.00", 0, 0, 0);
        Long refunded = seedLegacy("RF1", 0, "100.00", "50.00", "0.00", "50.00", 0, 0, 0);
        Long withWriteOff = seedLegacy("W1", 0, "100.00", "80.00", "20.00", "0.00", 0, 0, 0);
        Long deliveredNoPay = seedLegacy("D1", 0, "100.00", "0.00", "0.00", "0.00", 0, 1, 1);
        OrderLegacyMigrator migrator = migrator();

        // ── dry-run：同决策，不写库 ──
        OrderLegacyMigrator.Report dry = migrator.migrate(1L, false);
        assertEquals(0, countStatus(settled, "fulfillment_status"), "dry-run 不得写库");
        assertEquals("MIGRATED", findDecision(dry, settled).decision());
        assertEquals("CANCELLED", findDecision(dry, cancelled).toFulfillment(), "旧取消订单必须映射 CANCELLED");
        assertEquals("MANUAL_REVIEW", findDecision(dry, returning).decision(), "status=7/8 进人工核对");
        assertEquals("MANUAL_REVIEW", findDecision(dry, refunded).decision(), "refund_amount>0 进人工核对");
        assertEquals("MANUAL_REVIEW", findDecision(dry, deliveredNoPay).decision(), "出库无收款冲突进人工核对");
        assertEquals("PARTIAL", findDecision(dry, partial).toCollection());
        assertEquals("SETTLED", findDecision(dry, withWriteOff).toCollection(), "短款核销订单按金额公式结清");

        // ── execute ──
        migrator.migrate(1L, true);
        assertEquals("COMPLETED", getStatus(settled, "fulfillment_status"));
        assertEquals("RECORD_ONLY", getStatus(settled, "fulfillment_mode"));
        assertEquals("SETTLED", getStatus(settled, "collection_status"));
        assertEquals("MIGRATION_CONFIRMED", getStatus(settled, "settlement_method"));
        // 旧字段经适配器投影：新旧一致
        assertEquals(5, Integer.parseInt(getStatus(settled, "status")));
        assertEquals(2, Integer.parseInt(getStatus(settled, "payment_status")));
        assertEquals("CANCELLED", getStatus(cancelled, "fulfillment_status"));
        assertEquals(6, Integer.parseInt(getStatus(cancelled, "status")));

        // 期初流水：MIGRATION_OPENING + WRITE_OFF 都可复算；人工核对订单零写入
        assertEquals(1, countRecord(settled, "MIGRATION_OPENING"));
        assertEquals(1, countRecord(withWriteOff, "WRITE_OFF"));
        assertEquals(0, countRecord(refunded, "MIGRATION_OPENING"), "人工核对订单不得写任何流水");

        // ── 幂等重放 ──
        OrderLegacyMigrator.Report replay = migrator.migrate(1L, true);
        assertEquals("SKIPPED_ALREADY_MIGRATED", findDecision(replay, settled).decision());
        assertEquals(1, countRecord(settled, "MIGRATION_OPENING"), "重放不得重复落期初流水");
        assertEquals(1, countRecord(withWriteOff, "WRITE_OFF"), "重放不得重复落核销流水");
    }

    @Test
    void faultInjection_midBatchRollsBackEntirely() throws Exception {
        Long good = seedLegacy("FG1", 0, "100.00", "100.00", "0.00", "0.00", 0, 0, 0);
        Long bad = seedLegacy("FB1", 0, "100.00", "50.00", "0.00", "0.00", 0, 0, 0);
        OrderLegacyMigrator migrator = migrator();

        // 故障注入：第二笔订单写库时抛异常 → 整批回滚
        final boolean[] faultFired = {false};
        migrator.setFaultInjector(() -> {
            if (!faultFired[0]) {
                faultFired[0] = true;
                return; // 第一笔放行
            }
            throw new IllegalStateException("注入故障：模拟第二笔写库失败");
        });

        assertThrows(IllegalStateException.class, () -> migrator.migrate(1L, true));

        // 整批回滚：两笔订单都必须仍是未迁移状态（不允许部分提交）
        assertEquals(0, countStatus(good, "fulfillment_status"),
                "第一笔正常订单必须随事务回滚");
        assertEquals(0, countStatus(bad, "fulfillment_status"), "故障订单必须回滚");
        assertEquals(0, countRecord(good, "MIGRATION_OPENING"), "期初流水必须一并回滚");

        // 回滚后可再次正常执行
        migrator.setFaultInjector(null);
        OrderLegacyMigrator.Report retry = migrator.migrate(1L, true);
        assertEquals("MIGRATED", findDecision(retry, good).decision());
        assertEquals("COMPLETED", getStatus(good, "fulfillment_status"));
    }
}
