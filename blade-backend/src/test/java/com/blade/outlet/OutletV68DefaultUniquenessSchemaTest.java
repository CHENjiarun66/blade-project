package com.blade.outlet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V68 用户/Agent Key 默认档口唯一性迁移的静态契约（与 OrderV51SchemaTest 模式一致）。
 * 运行期真实唯一键行为由 {@code OutletSubjectDefaultUniquenessIntegrationTest} 验证，
 * 空库连续迁移由 {@code FlywayFreshDatabaseMigrationTest} 验证。
 */
class OutletV68DefaultUniquenessSchemaTest {

    private String v68() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V68__outlet_subject_default_unique.sql"));
    }

    @Test
    void sysUserOutletGetsGeneratedGuardAndUniqueIndex() throws Exception {
        assertThat(v68())
                .contains("ALTER TABLE `sys_user_outlet`")
                .contains("`user_default_guard` bigint GENERATED ALWAYS AS")
                .contains("THEN `user_id` ELSE NULL")
                .contains("STORED")
                .contains("ADD UNIQUE KEY `uk_user_outlet_default` (`tenant_id`, `user_default_guard`)");
    }

    @Test
    void agentKeyOutletGetsGeneratedGuardAndUniqueIndex() throws Exception {
        assertThat(v68())
                .contains("ALTER TABLE `agent_key_outlet`")
                .contains("`agent_key_default_guard` bigint GENERATED ALWAYS AS")
                .contains("THEN `agent_key_id` ELSE NULL")
                .contains("ADD UNIQUE KEY `uk_agent_key_outlet_default` (`tenant_id`, `agent_key_default_guard`)");
    }

    @Test
    void migrationDocumentsFailClosedPreflightAndManualHandling() throws Exception {
        assertThat(v68())
                .containsIgnoringCase("fail-closed")
                .contains("HAVING COUNT(*)>1")
                .contains("人工确认保留哪条");
    }
}
