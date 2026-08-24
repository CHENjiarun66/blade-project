package com.blade.whatsapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WhatsappV44SchemaTest {
    @Test void collectorAndIssueSchemaKeepsAuthAndTenantBoundaries() throws IOException {
        try(var stream=getClass().getResourceAsStream("/db/migration/V44__whatsapp_collector_workbench.sql")){
            assertNotNull(stream);
            String sql=new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE `wa_collector_key`"));
            assertTrue(sql.contains("`key_hash` varchar(100)"));
            assertFalse(sql.contains("collector_key` varchar"));
            assertTrue(sql.contains("CREATE TABLE `wa_collection_issue`"));
            assertTrue(sql.contains("UNIQUE KEY `uk_wa_issue_key` (`tenant_id`, `account_id`, `issue_key_hash`)"));
            assertTrue(sql.contains("CREATE TABLE `wa_scan_job`"));
            assertTrue(sql.contains("p.tenant_id = r.tenant_id"));
            assertTrue(sql.contains("menu:whatsapp"));
        }
    }
}
