package com.blade.order;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V51 字段级契约验证（静态 SQL 断言，模式与 OrderDraftV48SchemaTest 一致）。
 * Flyway 连续升级语义由本地/隔离库启动时执行 migration 验证，不在单测中重跑同一版本。
 */
class OrderV51SchemaTest {

    private String v51() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V51__order_lifecycle_finance.sql"));
    }

    @Test
    void saleOrderGainsLifecycleFinanceSnapshotAndVersionColumns() throws Exception {
        String sql = v51();
        assertThat(sql)
                .contains("ADD COLUMN `fulfillment_status` varchar(32) NULL")
                .contains("ADD COLUMN `collection_status` varchar(16) NULL")
                .contains("ADD COLUMN `fulfillment_mode` varchar(24) NOT NULL DEFAULT 'UNDECIDED'")
                .contains("ADD COLUMN `fulfillment_decided_at`")
                .contains("ADD COLUMN `fulfillment_decided_by`")
                .contains("ADD COLUMN `settled_at`")
                .contains("ADD COLUMN `settlement_method`")
                .contains("ADD COLUMN `gross_received_amount`")
                .contains("ADD COLUMN `cash_refund_amount`")
                .contains("ADD COLUMN `sales_return_amount`")
                .contains("ADD COLUMN `net_received_amount`")
                .contains("ADD COLUMN `balance_amount`")
                .contains("ADD COLUMN `version` int NOT NULL DEFAULT 0");
    }

    @Test
    void saleOrderHasTenantLeadingIndexesAndNonNegativeSnapshotCheck() throws Exception {
        String sql = v51();
        assertThat(sql)
                .contains("ADD KEY `idx_so_tenant_fulfillment` (`tenant_id`, `fulfillment_status`)")
                .contains("ADD KEY `idx_so_tenant_collection` (`tenant_id`, `collection_status`)")
                .contains("ADD KEY `idx_so_tenant_settled` (`tenant_id`, `settled_at`)")
                .contains("CONSTRAINT `chk_so_snapshots_nonnegative` CHECK (")
                .contains("`balance_amount` >= 0")
                .contains("`write_off_amount` >= 0");
    }

    @Test
    void financialRecordIsAppendOnlyWithIdempotencyAndReversalGuards() throws Exception {
        String sql = v51();
        assertThat(sql)
                .contains("CREATE TABLE `order_financial_record`")
                .contains("UNIQUE KEY `uk_ofr_idempotency` (`tenant_id`, `idempotency_key`)")
                .contains("UNIQUE KEY `uk_ofr_reversal` (`tenant_id`, `reversed_record_id`)")
                .contains("CONSTRAINT `chk_ofr_amount_positive` CHECK (`amount` > 0)")
                .contains("`record_type` = 'REVERSAL' AND `reversed_record_id` IS NOT NULL")
                .contains("`record_type` <> 'REVERSAL' AND `reversed_record_id` IS NULL")
                .doesNotContain("FOREIGN KEY");
    }

    @Test
    void stateTransitionLogHasTenantScopedIdempotency() throws Exception {
        String sql = v51();
        assertThat(sql)
                .contains("CREATE TABLE `order_state_transition_log`")
                .contains("UNIQUE KEY `uk_ostl_idempotency` (`tenant_id`, `idempotency_key`)")
                .contains("KEY `idx_ostl_tenant_order` (`tenant_id`, `order_id`, `occurred_at`)")
                .contains("refundPayment")
                .contains("reverseFinancialRecord")
                .doesNotContain("FOREIGN KEY");
    }

    @Test
    void migrationDoesNotAutoDeriveLegacyStatus() throws Exception {
        String sql = v51();
        assertThat(sql)
                .doesNotContain("UPDATE `sale_order`")
                .doesNotContain("SET `fulfillment_status`")
                .doesNotContain("SET `collection_status`");
    }
}
