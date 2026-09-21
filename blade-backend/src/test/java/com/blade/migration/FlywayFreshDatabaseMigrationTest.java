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
 * 第二批B：Flyway 从空库 V1→V67 的正向证据，以及历史重复默认档口的 fail-closed 反例。
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
    void migratesFromEmptyDatabaseToV67WithGeneratedGuardAndUniqueIndex() throws Exception {
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

            assertEquals("67", flyway.info().current().getVersion().getVersion(),
                    "空库必须迁移到 V67");
            assertTrue(flyway.info().applied().length >= 67, "应记录至少 67 条迁移历史");

            try (Connection conn = DriverManager.getConnection(freshUrl, user, password);
                 Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + db
                                + "' AND TABLE_NAME='sales_outlet' AND COLUMN_NAME='tenant_default_guard'")) {
                    assertTrue(rs.next(), "V67 应创建 tenant_default_guard 生成列");
                    assertTrue(rs.getString(1).toUpperCase().contains("GENERATED"), "guard 必须是生成列");
                }
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='" + db
                                + "' AND TABLE_NAME='sales_outlet' AND INDEX_NAME='uk_outlet_tenant_default'")) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1), "唯一索引应包含 (tenant_id, tenant_default_guard) 两列");
                }
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

    private String freshDbName() {
        return "blade_v67_fresh_" + Long.toString(System.nanoTime()).substring(8);
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
