package com.blade.order.draft;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDraftV59SchemaTest {
    @Test
    void migrationAddsManualDraftFieldsWithoutChangingExistingAgentRows() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V59__manual_quick_order_drafts.sql"));

        assertThat(sql)
                .contains("`entry_source` varchar(20) NOT NULL DEFAULT 'AGENT'")
                .contains("`source_shop`")
                .contains("`order_type`")
                .contains("`paid_amount`")
                .contains("`freight_amount`")
                .contains("`need_delivery`")
                .contains("`cost_price`")
                .doesNotContain("DROP TABLE", "DROP COLUMN", "DELETE FROM", "TRUNCATE");
    }
}
