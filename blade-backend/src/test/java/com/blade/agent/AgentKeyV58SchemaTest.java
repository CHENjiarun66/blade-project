package com.blade.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentKeyV58SchemaTest {
    @Test
    void migrationAddsLifecycleAuditAndOwnerOnlyManagementPermission() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V58__agent_key_management.sql"));

        assertThat(sql)
                .contains("`created_by_user_id`", "`disabled_time`", "`rotated_from_key_id`")
                .contains("'agent-key:manage'", "'btn:system:agentKey'")
                .contains("r.role_code = 'ROLE_OWNER'")
                .doesNotContain("ROLE_ADMIN");
    }
}
