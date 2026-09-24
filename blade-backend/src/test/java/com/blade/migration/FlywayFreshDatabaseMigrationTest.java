package com.blade.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二批B/收口：Flyway 从空库 V1→V69 的正向证据，以及历史重复默认档口的 fail-closed 反例。
 *
 * <p>真实 MySQL：临时建库、Flyway migrate、断言终态版本与唯一索引/生成列，最后 drop 临时库，
 * 不触碰 blade_project 与任何生产库。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayFreshDatabaseMigrationTest {

    private static final Pattern DB_SEGMENT = Pattern.compile("/[^/?]+(\\?|$)");

    @Autowired private Environment environment;

    @Test
    void migratesFromEmptyDatabaseToV69WithGeneratedGuardsAndUniqueIndexes() throws Exception {
        String db = freshDbName();
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username", "root");
        String password = environment.getProperty("spring.datasource.password", "root123");
        String freshUrl = replaceDatabase(url, db);
        String serverUrl = replaceDatabase(url, "");

        createDatabase(serverUrl, user, password, db);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();

            assertEquals("69", flyway.info().current().getVersion().getVersion(),
                    "空库必须迁移到 V69");
            assertTrue(flyway.info().applied().length >= 69, "应记录至少 69 条迁移历史");

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                assertGeneratedColumn(st, db, "sales_outlet", "tenant_default_guard");
                assertUniqueIndex(st, db, "sales_outlet", "uk_outlet_tenant_default", 2);
                assertGeneratedColumn(st, db, "sys_user_outlet", "user_default_guard");
                assertUniqueIndex(st, db, "sys_user_outlet", "uk_user_outlet_default", 2);
                assertGeneratedColumn(st, db, "agent_key_outlet", "agent_key_default_guard");
                assertUniqueIndex(st, db, "agent_key_outlet", "uk_agent_key_outlet_default", 2);
            }
        } finally {
            dropDatabase(serverUrl, user, password, db);
        }
    }

    @Test
    void failsClosedWhenHistoricalDuplicateDefaultsExist() throws Exception {
        String db = freshDbName();
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username", "root");
        String password = environment.getProperty("spring.datasource.password", "root123");
        String freshUrl = replaceDatabase(url, db);
        String serverUrl = replaceDatabase(url, "");

        createDatabase(serverUrl, user, password, db);
        try {
            Flyway toV66 = Flyway.configure()
                    .dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .target("66")
                    .load();
            toV66.migrate();

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,is_tenant_default,deleted,sort) "
                        + "VALUES(1,'DUP-A','重复A','STORE',1,1,0,0),(1,'DUP-B','重复B','STORE',1,1,0,0)");
            }

            Flyway toV67 = Flyway.configure()
                    .dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            assertThrows(FlywayException.class, toV67::migrate,
                    "历史存在同租户重复默认档口时 V67 必须 fail closed，不自动清理");
        } finally {
            dropDatabase(serverUrl, user, password, db);
        }
    }

    @Test
    void failsClosedWhenHistoricalDuplicateUserDefaultsExist() throws Exception {
        String db = freshDbName();
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username", "root");
        String password = environment.getProperty("spring.datasource.password", "root123");
        String freshUrl = replaceDatabase(url, db);
        String serverUrl = replaceDatabase(url, "");

        createDatabase(serverUrl, user, password, db);
        try {
            Flyway.configure().dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration").baselineOnMigrate(true).target("67").load().migrate();

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) "
                        + "VALUES(1,77,1,1,1,0),(1,77,2,1,1,0)");
            }

            Flyway toV68 = Flyway.configure().dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration").baselineOnMigrate(true).load();
            assertThrows(FlywayException.class, toV68::migrate,
                    "历史存在同用户重复默认绑定时 V68 必须 fail closed");

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                // 统一前置检查保证：任一表重复时两张表都不得产生 V68 结构（零 DDL）
                assertNoGeneratedColumn(st, db, "sys_user_outlet", "user_default_guard");
                assertNoIndex(st, db, "sys_user_outlet", "uk_user_outlet_default");
                assertNoGeneratedColumn(st, db, "agent_key_outlet", "agent_key_default_guard");
                assertNoIndex(st, db, "agent_key_outlet", "uk_agent_key_outlet_default");
            }
        } finally {
            dropDatabase(serverUrl, user, password, db);
        }
    }

    @Test
    void agentDuplicateFailsClosedWithZeroDdlThenRepairAndRetrySucceeds() throws Exception {
        String db = freshDbName();
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username", "root");
        String password = environment.getProperty("spring.datasource.password", "root123");
        String freshUrl = replaceDatabase(url, db);
        String serverUrl = replaceDatabase(url, "");

        createDatabase(serverUrl, user, password, db);
        try {
            Flyway.configure().dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration").baselineOnMigrate(true).target("67").load().migrate();

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO agent_key_outlet(tenant_id,agent_key_id,outlet_id,is_default,status) "
                        + "VALUES(1,88,1,1,1),(1,88,2,1,1)");
            }

            Flyway failed = Flyway.configure().dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration").baselineOnMigrate(true).load();
            assertThrows(FlywayException.class, failed::migrate,
                    "历史存在同 Key 重复默认绑定时 V68 必须 fail closed");

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                // 核心反例：agent 表重复也不得让任何一张表产生 V68 结构（零 DDL）
                assertNoGeneratedColumn(st, db, "sys_user_outlet", "user_default_guard");
                assertNoIndex(st, db, "sys_user_outlet", "uk_user_outlet_default");
                assertNoGeneratedColumn(st, db, "agent_key_outlet", "agent_key_default_guard");
                assertNoIndex(st, db, "agent_key_outlet", "uk_agent_key_outlet_default");

                // 人工清理重复默认（保留 outlet_id=2），随后 repair + retry 必须成功
                st.executeUpdate("UPDATE agent_key_outlet SET is_default=0 "
                        + "WHERE tenant_id=1 AND agent_key_id=88 AND outlet_id=1");
            }

            Flyway retry = Flyway.configure().dataSource(freshUrl, user, password)
                    .locations("classpath:db/migration").baselineOnMigrate(true).load();
            retry.repair();
            retry.migrate();

            assertEquals("69", retry.info().current().getVersion().getVersion(),
                    "清理重复并 repair 后必须能继续迁移到当前最新版本 V69");
            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                assertGeneratedColumn(st, db, "sys_user_outlet", "user_default_guard");
                assertUniqueIndex(st, db, "sys_user_outlet", "uk_user_outlet_default", 2);
                assertGeneratedColumn(st, db, "agent_key_outlet", "agent_key_default_guard");
                assertUniqueIndex(st, db, "agent_key_outlet", "uk_agent_key_outlet_default", 2);
            }
        } finally {
            dropDatabase(serverUrl, user, password, db);
        }
    }

    private void assertNoGeneratedColumn(Statement st, String db, String table, String column) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + table + "' AND COLUMN_NAME='" + column + "'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "preflight 失败时 " + table + "." + column + " 不得存在（零 DDL）");
        }
    }

    private void assertNoIndex(Statement st, String db, String table, String index) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + table + "' AND INDEX_NAME='" + index + "'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "preflight 失败时 " + table + "." + index + " 不得存在（零 DDL）");
        }
    }

    private void assertGeneratedColumn(Statement st, String db, String table, String column) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + table + "' AND COLUMN_NAME='" + column + "'")) {
            assertTrue(rs.next(), table + "." + column + " 生成列应存在");
            assertTrue(rs.getString(1).toUpperCase().contains("GENERATED"), table + "." + column + " 必须是生成列");
        }
    }

    private void assertUniqueIndex(Statement st, String db, String table, String index, int expectedColumns) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + table + "' AND INDEX_NAME='" + index + "'")) {
            assertTrue(rs.next());
            assertEquals(expectedColumns, rs.getInt(1), table + "." + index + " 唯一索引列数");
        }
    }

    private String freshDbName() {
        return "blade_v69_fresh_" + Long.toString(System.nanoTime()).substring(8);
    }

    private String replaceDatabase(String url, String database) {
        Matcher matcher = DB_SEGMENT.matcher(url);
        if (!matcher.find()) {
            throw new IllegalStateException("无法解析 datasource url: " + url);
        }
        String replacement = database == null || database.isBlank() ? "/" + matcher.group(1) : "/" + database + matcher.group(1);
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private void createDatabase(String serverUrl, String user, String password, String db) throws Exception {
        try (Connection conn = DriverManager.getConnection(serverUrl, user, password);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4");
        }
    }

    private void dropDatabase(String serverUrl, String user, String password, String db) {
        try (Connection conn = DriverManager.getConnection(serverUrl, user, password);
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + db + "`");
        } catch (Exception ignored) {
            // 清理失败不掩盖断言结果
        }
    }
}
