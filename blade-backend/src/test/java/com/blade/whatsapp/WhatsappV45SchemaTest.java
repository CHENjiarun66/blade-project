package com.blade.whatsapp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappV45SchemaTest {
    @Test
    void migrationDefinesTenantSafeAnalysisTablesAndPermissions() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V45__whatsapp_agent_analysis.sql"));
        assertThat(sql).contains("CREATE TABLE `wa_analysis_job`", "CREATE TABLE `wa_customer_analysis`",
                "CREATE TABLE `wa_followup_recommendation`", "`tenant_id` BIGINT NOT NULL",
                "uk_wa_analysis_context", "uk_wa_customer_analysis_job", "uk_wa_followup_analysis",
                "btn:whatsapp:recommendation");
        assertThat(sql).doesNotContain("FOREIGN KEY");
    }
}
