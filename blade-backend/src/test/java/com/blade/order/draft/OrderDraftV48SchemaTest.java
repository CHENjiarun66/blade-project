package com.blade.order.draft;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDraftV48SchemaTest {
    @Test
    void migrationKeepsPaperValuesAndTenantScopedIdempotency() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V48__order_draft_agent_import.sql"));

        assertThat(sql)
                .contains("CREATE TABLE `order_draft`", "CREATE TABLE `order_draft_item`")
                .contains("UNIQUE KEY `uk_order_draft_tenant_external_ref` (`tenant_id`, `external_ref_no`)")
                .contains("`raw_sale_price`", "`sale_price`", "`paper_amount`")
                .contains("`system_reference_price`", "`confirmed_order_id`")
                .doesNotContain("FOREIGN KEY");
    }
}
