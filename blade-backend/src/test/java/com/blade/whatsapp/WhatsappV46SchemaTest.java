package com.blade.whatsapp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappV46SchemaTest {
    @Test
    void migrationAddsClaimedContextSnapshot() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V46__whatsapp_analysis_context_snapshot.sql"));
        assertThat(sql).contains("ALTER TABLE `wa_analysis_job`")
                .contains("`context_message_ids` JSON");
    }
}
