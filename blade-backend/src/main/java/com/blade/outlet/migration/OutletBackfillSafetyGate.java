package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 回填写库安全闸门（Series F，fail-closed）。
 *
 * <p>apply 的正向证明链：显式映射文件 + 显式租户 + 显式 {@code expected-database-name} 且与实际
 * {@code SELECT DATABASE()} <b>完全一致</b> + 实际库名匹配副本命名模式（{@code *_copy/_rehearsal/
 * _staging/_test}）+ 显式可写 report-dir；生产/NAS 黑名单仅作为第二层。
 * 仅 {@link #approve(OutletBackfillProperties)} 能签发 {@link OutletBackfillApproval}。</p>
 */
@Component
public class OutletBackfillSafetyGate {

    private static final List<String> PRODUCTION_MARKERS = List.of("prod", "production", "nas");
    private static final Pattern COPY_DATABASE =
            Pattern.compile(OutletBackfillProperties.COPY_DATABASE_PATTERN);

    private final JdbcTemplate jdbc;
    private final Environment environment;

    public OutletBackfillSafetyGate(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    /** 读取真实连接信息并签发副本审批凭证；任何一项不满足即拒绝。 */
    public OutletBackfillApproval approve(OutletBackfillProperties properties) {
        String databaseName = currentDatabase();
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        requireApplyAllowed(properties, jdbcUrl, databaseName);
        return new OutletBackfillApproval(
                properties.getTenantId(), Path.of(properties.getReportDir()),
                databaseName, properties.getMappingFile(), properties.getOperator().trim(), Instant.now());
    }

    /**
     * 纯校验（包内可见，便于反例测试）：不读取真实连接，由调用方提供 jdbcUrl/databaseName。
     */
    void requireApplyAllowed(OutletBackfillProperties properties, String jdbcUrl, String databaseName) {
        if (!properties.isApply()) {
            throw BusinessException.of(400, "apply 未显式开启（默认 dry-run）");
        }
        if (properties.getMappingFile() == null || properties.getMappingFile().isBlank()) {
            throw BusinessException.of(400, "apply 必须显式提供映射文件");
        }
        if (properties.getTenantId() == null) {
            throw BusinessException.of(400, "apply 必须显式指定租户");
        }
        if (!properties.isCopyEnvironmentAck()) {
            throw BusinessException.of(400, "apply 必须确认连接的是生产副本（blade.outlet.backfill.copy-environment-ack=true）");
        }
        if (properties.getOperator() == null || properties.getOperator().isBlank()) {
            throw BusinessException.of(400, "apply 必须显式提供 operator（操作人）以写入审计报告");
        }
        String expected = properties.getExpectedDatabaseName();
        if (expected == null || expected.isBlank()) {
            throw BusinessException.of(400, "apply 必须显式提供 expected-database-name");
        }
        if (databaseName == null || databaseName.isBlank()) {
            throw BusinessException.of(400, "无法读取当前数据库名，拒绝 apply");
        }
        if (!expected.equals(databaseName)) {
            throw BusinessException.of(403, "expected-database-name 与实际数据库不一致，拒绝 apply");
        }
        if (!COPY_DATABASE.matcher(databaseName).matches()) {
            throw BusinessException.of(403, "目标库名不是正向副本命名（需 *_copy/_rehearsal/_staging/_test），拒绝 apply");
        }
        if ("blade".equalsIgnoreCase(databaseName)) {
            throw BusinessException.of(403, "拒绝生产常用库名 blade");
        }
        String probe = ((jdbcUrl == null ? "" : jdbcUrl) + " " + databaseName).toLowerCase(Locale.ROOT);
        for (String marker : PRODUCTION_MARKERS) {
            if (probe.contains(marker)) {
                throw BusinessException.of(403, "拒绝疑似生产/NAS 连接（命中特征: " + marker + "）");
            }
        }
        Path reportDir = properties.getReportDir() == null || properties.getReportDir().isBlank()
                ? null : Path.of(properties.getReportDir());
        if (reportDir == null) {
            throw BusinessException.of(400, "apply 必须显式提供 report-dir");
        }
        try {
            Files.createDirectories(reportDir);
        } catch (IOException e) {
            throw BusinessException.of(400, "report-dir 无法创建: " + reportDir);
        }
        if (!Files.isDirectory(reportDir) || !Files.isWritable(reportDir)) {
            throw BusinessException.of(400, "report-dir 不可写: " + reportDir);
        }
    }

    private String currentDatabase() {
        return jdbc.queryForObject("SELECT DATABASE()", String.class);
    }
}
