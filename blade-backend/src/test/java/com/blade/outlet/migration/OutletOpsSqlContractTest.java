package com.blade.outlet.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Series F 运维 SQL 静态契约：只读、必须显式租户闸门、涉及档口/订单/用户的语句必须带 tenant_id。
 */
class OutletOpsSqlContractTest {

    private static final Path SCRIPTS = Path.of("..", "scripts");
    private static final List<String> READ_ONLY_FILES = List.of(
            "outlet-source-shop-audit.sql",
            "outlet-user-outlet-authorization-suggestions.sql",
            "outlet-scope-explain.sql");
    private static final Pattern WRITE_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|REPLACE|MERGE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void opsSqlFilesAreReadOnly() throws IOException {
        for (String file : READ_ONLY_FILES) {
            String sql = stripComments(read(file));
            assertFalse(WRITE_KEYWORDS.matcher(sql).find(),
                    file + " 含写/DDL 关键字");
        }
    }

    @Test
    void tenantScopedSqlRequiresExplicitTenantGate() throws IOException {
        String audit = read("outlet-source-shop-audit.sql");
        String suggestions = read("outlet-user-outlet-authorization-suggestions.sql");
        String explain = read("outlet-scope-explain.sql");

        assertTrue(audit.contains("@tenant_id = NULL"), "审计 SQL 必须默认 @tenant_id=NULL（fail-closed）");
        assertTrue(audit.contains("tenant_id = @tenant_id"), "审计 SQL 必须使用 @tenant_id 过滤");
        assertTrue(suggestions.contains("@tenant_id = NULL"), "建议 SQL 必须默认 @tenant_id=NULL（fail-closed）");
        assertTrue(suggestions.contains("tenant_id = @tenant_id"), "建议 SQL 必须使用 @tenant_id 过滤");
        assertTrue(explain.contains(":tenant_id"), "EXPLAIN SQL 必须显式 tenant 参数");

        for (String file : READ_ONLY_FILES) {
            for (String statement : read(file).split(";")) {
                String lower = stripComments(statement).toLowerCase(Locale.ROOT);
                boolean touchesScopedTables = lower.contains("sale_order") || lower.contains("order_draft")
                        || lower.contains("sales_outlet") || lower.contains("sys_user_outlet")
                        || lower.contains("sys_user") || lower.contains("sys_role");
                if (touchesScopedTables && lower.contains("select")) {
                    assertTrue(lower.contains("tenant_id"),
                            file + " 中涉及档口/订单/用户的语句缺少 tenant_id: " + statement.trim());
                }
            }
        }
    }

    private String read(String file) throws IOException {
        return Files.readString(SCRIPTS.resolve(file), StandardCharsets.UTF_8);
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ");
    }
}
