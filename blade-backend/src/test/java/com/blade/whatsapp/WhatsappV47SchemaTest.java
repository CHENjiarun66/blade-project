package com.blade.whatsapp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappV47SchemaTest {
    @Test
    void migrationAddsAccountAndContactScanScopes() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V47__whatsapp_targeted_rescan.sql"));
        assertThat(sql).contains("ALTER TABLE `wa_scan_job`", "ALTER TABLE `wa_import_batch`")
                .contains("`scope_type` varchar(16) NOT NULL DEFAULT 'ACCOUNT'")
                .contains("`scan_scope_type` varchar(16) NOT NULL DEFAULT 'ACCOUNT'")
                .contains("`target_phone_normalized` varchar(32)")
                .contains("`target_conversation_jid` varchar(191)")
                .contains("idx_wa_scan_job_scope", "idx_wa_batch_scope")
                .doesNotContain("FOREIGN KEY");
    }
}
