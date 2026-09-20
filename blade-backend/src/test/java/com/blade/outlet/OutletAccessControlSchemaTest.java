package com.blade.outlet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V63 档口数据模型契约验证（静态 SQL 断言，模式与 OrderV51SchemaTest / OrderDraftV59SchemaTest 一致）。
 * Flyway 连续升级语义由 OutletFlywayMigrationTest（空库 V1→最新）验证。
 */
class OutletAccessControlSchemaTest {

    private String v63() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V63__outlet_access_control.sql"));
    }

    @Test
    void salesOutletHasTenantCodeUniqueAndStatusIndex() throws Exception {
        String sql = v63();
        assertThat(sql)
                .contains("CREATE TABLE `sales_outlet`")
                .contains("`tenant_id` bigint NOT NULL")
                .contains("`outlet_code` varchar(30) NOT NULL")
                .contains("`outlet_name` varchar(100) NOT NULL")
                .contains("`outlet_type` varchar(20) NOT NULL DEFAULT 'STORE'")
                .contains("`is_tenant_default` tinyint NOT NULL DEFAULT 0")
                .contains("`status` tinyint NOT NULL DEFAULT 1")
                .contains("`deleted` tinyint NOT NULL DEFAULT 0")
                .contains("UNIQUE KEY `uk_outlet_code_tenant` (`tenant_id`, `outlet_code`)")
                .contains("KEY `idx_outlet_tenant_status` (`tenant_id`, `status`, `deleted`)");
    }

    @Test
    void sysUserOutletHasUniqueAndBidirectionalIndexes() throws Exception {
        String sql = v63();
        assertThat(sql)
                .contains("CREATE TABLE `sys_user_outlet`")
                .contains("`tenant_id` bigint NOT NULL")
                .contains("`user_id` bigint NOT NULL")
                .contains("`outlet_id` bigint NOT NULL")
                .contains("`is_default` tinyint NOT NULL DEFAULT 0")
                .contains("UNIQUE KEY `uk_user_outlet_tenant` (`tenant_id`, `user_id`, `outlet_id`)")
                .contains("KEY `idx_user_outlet_user` (`tenant_id`, `user_id`, `status`, `deleted`)")
                .contains("KEY `idx_user_outlet_outlet` (`tenant_id`, `outlet_id`, `status`, `deleted`)");
    }

    @Test
    void agentKeyOutletHasUniqueAndKeyIndex() throws Exception {
        String sql = v63();
        assertThat(sql)
                .contains("CREATE TABLE `agent_key_outlet`")
                .contains("`tenant_id` bigint NOT NULL")
                .contains("`agent_key_id` bigint NOT NULL")
                .contains("`outlet_id` bigint NOT NULL")
                .contains("UNIQUE KEY `uk_agent_key_outlet` (`tenant_id`, `agent_key_id`, `outlet_id`)")
                .contains("KEY `idx_agent_key_outlet_key` (`tenant_id`, `agent_key_id`, `status`)");
    }

    @Test
    void orderOutletChangeLogIsAppendOnlyWithTenantLeadingIndex() throws Exception {
        String sql = v63();
        assertThat(sql)
                .contains("CREATE TABLE `order_outlet_change_log`")
                .contains("`tenant_id` bigint NOT NULL")
                .contains("`order_id` bigint NOT NULL")
                .contains("`old_outlet_id` bigint DEFAULT NULL")
                .contains("`new_outlet_id` bigint NOT NULL")
                .contains("`new_outlet_name` varchar(100) NOT NULL")
                .contains("`reason` varchar(500) NOT NULL")
                .contains("`operator_id` bigint NOT NULL")
                .contains("KEY `idx_outlet_change_order` (`tenant_id`, `order_id`, `create_time`)");
    }

    @Test
    void saleOrderAndDraftGainNullableSourceOutletId() throws Exception {
        String sql = v63();
        assertThat(sql)
                // 正式订单 source_outlet_id 必须保持可空（历史未回填前禁止 NOT NULL）
                .contains("ADD COLUMN `source_outlet_id` bigint DEFAULT NULL")
                .contains("KEY `idx_so_source_outlet` (`tenant_id`, `source_outlet_id`)")
                .contains("KEY `idx_order_draft_source_outlet` (`tenant_id`, `source_outlet_id`)")
                .doesNotContain("`source_outlet_id` bigint NOT NULL");
    }

    @Test
    void agentKeyGainsOutletScopeTypeDefaultingToNone() throws Exception {
        String sql = v63();
        assertThat(sql)
                .contains("ADD COLUMN `outlet_scope_type` varchar(20) NOT NULL DEFAULT 'NONE'")
                .contains("ALL/ASSIGNED/NONE");
    }

    @Test
    void migrationIsAdditiveOnly() throws Exception {
        String sql = v63();
        assertThat(sql)
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DELETE FROM")
                .doesNotContain("TRUNCATE")
                .doesNotContain("UPDATE `sale_order`")
                .doesNotContain("UPDATE `order_draft`");
    }
}
