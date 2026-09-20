package com.blade.outlet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 静态契约：scripts/outlet-source-shop-audit.sql 必须全程只读，
 * 且包含发布人员可编辑的名称映射 CTE 与映射分类输出。
 */
class OutletAuditSqlReadOnlyTest {

    private String sqlBodyWithoutComments() throws Exception {
        String sql = Files.readString(Path.of("../scripts/outlet-source-shop-audit.sql"));
        return sql.lines()
                .map(line -> {
                    int idx = line.indexOf("--");
                    return idx >= 0 ? line.substring(0, idx) : line;
                })
                .collect(Collectors.joining("\n"))
                .toLowerCase();
    }

    @Test
    void auditSqlContainsNoWriteOrDdlStatements() throws Exception {
        String body = sqlBodyWithoutComments();
        for (String forbidden : List.of(
                "insert ", "update ", "delete ", "drop ", "create ", "alter ",
                "truncate", "into outfile", "load data", "grant ")) {
            assertThat(body).as("审计脚本不得包含写语句/DDL: %s", forbidden).doesNotContain(forbidden);
        }
        assertThat(body).contains("select");
    }

    @Test
    void auditSqlContainsEditableMappingAndClassificationOutputs() throws Exception {
        String body = sqlBodyWithoutComments();
        assertThat(body)
                .contains("name_mapping")
                .contains("可自动映射")
                .contains("未映射")
                .contains("疑似批次/纯数字");
    }
}
