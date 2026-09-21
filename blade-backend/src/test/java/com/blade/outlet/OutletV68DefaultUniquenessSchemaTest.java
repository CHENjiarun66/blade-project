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
                .contains("人工确认保留哪条")
                .contains("flyway repair")
                .contains("零 DDL");
    }

    @Test
    void preflightRunsBeforeAnyAlter() throws Exception {
        String sql = v68();
        int userDup = sql.indexOf("SET @v68_user_dup");
        int keyDup = sql.indexOf("SET @v68_key_dup");
        int execute = sql.indexOf("EXECUTE v68_preflight_stmt");
        int userAlter = sql.indexOf("ALTER TABLE `sys_user_outlet`");
        int keyAlter = sql.indexOf("ALTER TABLE `agent_key_outlet`");

        assertThat(userDup).isGreaterThanOrEqualTo(0);
        assertThat(keyDup).isGreaterThanOrEqualTo(0);
        assertThat(execute).isGreaterThan(userDup);
        assertThat(execute).isGreaterThan(keyDup);
        // 任何 ALTER 之前必须先执行统一 preflight，保证任一表重复时两张表都零 DDL
        assertThat(userAlter).isGreaterThan(execute);
        assertThat(keyAlter).isGreaterThan(execute);
        // 两段 ALTER 均在 preflight 之后
        assertThat(userAlter).isLessThan(keyAlter);
    }

    @Test
    void failFastUsesLocatableNonexistentTable() throws Exception {
        assertThat(v68())
                .contains("outlet_default_unique_preflight_failed_v68_see_migration_comment")
                .contains("PREPARE v68_preflight_stmt FROM @v68_preflight_sql");
    }
}
