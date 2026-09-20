package com.blade.outlet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 空库 Flyway V1→最新版本验证（环境允许时）。
 *
 * 在本地 MySQL 上创建一个一次性空 schema，从 V1 连续迁移到最新版本，
 * 校验迁移链完整（无 pending）且 V63 的档口表与 source_outlet_id 列已落地，
 * 最后删除该临时 schema。只操作自建的临时库，不触碰任何现有数据库或生产/NAS。
 */
class OutletFlywayMigrationTest {

    private static final String USER = envOr("BLADE_DB_USERNAME", "root");
    private static final String PASS = envOr("BLADE_DB_PASSWORD", "root123");
    private static final String ADMIN_URL = "jdbc:mysql://" + host()
            + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    @Test
    void emptyDatabaseMigratesFromV1ToLatest() throws Exception {
        Assumptions.assumeTrue(canConnect(),
                "本地 MySQL 不可用，跳过空库 Flyway V1→最新版本验证");

        String schema = "blade_outlet_verify_" + System.nanoTime();
        String schemaUrl = "jdbc:mysql://" + host() + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(schemaUrl, USER, PASS)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();

            assertEquals(0, flyway.info().pending().length,
                    "空库应一次性应用全部迁移，不得有未执行迁移");

            try (Connection c = DriverManager.getConnection(schemaUrl, USER, PASS);
                 Statement st = c.createStatement()) {
                assertObjectExists(st, "table", "sales_outlet");
                assertObjectExists(st, "table", "sys_user_outlet");
                assertObjectExists(st, "table", "agent_key_outlet");
                assertObjectExists(st, "table", "order_outlet_change_log");
                assertColumnExists(st, "sale_order", "source_outlet_id");
                assertColumnExists(st, "order_draft", "source_outlet_id");
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASS);
                 Statement st = admin.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            } catch (Exception ignored) {
                // 清理失败不影响主断言结果
            }
        }
    }

    // ==================== 辅助方法 ====================

    private static boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static String host() {
        String url = System.getenv("BLADE_DB_URL");
        if (url == null || url.isEmpty()) {
            return "localhost:3306";
        }
        int start = url.indexOf("//");
        if (start < 0) {
            return "localhost:3306";
        }
        String rest = url.substring(start + 2);
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        return rest;
    }

    private static void assertObjectExists(Statement st, String kind, String name) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = DATABASE() AND table_name = '" + name + "'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), kind + " '" + name + "' 应存在");
        }
    }

    private static void assertColumnExists(Statement st, String table, String column) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = DATABASE() AND table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "列 " + table + "." + column + " 应存在");
        }
    }
}
