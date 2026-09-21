package com.blade.outlet.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Series F 历史档口回填工具配置。
 *
 * <p>默认安全：{@code apply=false} 只做 dry-run；写库必须通过
 * {@link OutletBackfillSafetyGate} 的 fail-closed 正向副本身份验证：
 * 显式映射文件、显式租户、显式 {@code expected-database-name} 与实际库名完全一致、
 * 实际库名匹配副本命名模式、显式可写 {@code report-dir}，并保留生产/NAS 黑名单。</p>
 */
@Component
@ConfigurationProperties(prefix = "blade.outlet.backfill")
public class OutletBackfillProperties {

    /** 副本命名模式：库名必须以 *_copy / *_rehearsal / *_staging / *_test 结尾。 */
    public static final String COPY_DATABASE_PATTERN = "(?i)^[a-z0-9_]+_(copy|rehearsal|staging|test)$";

    /** 是否执行写库；默认 false（dry-run）。 */
    private boolean apply = false;

    /** 要回填的租户；apply 时必填。 */
    private Long tenantId;

    /** 显式映射 CSV 路径（tenant_id,legacy_source_shop,outlet_code,decision,reason）。 */
    private String mappingFile;

    /** 期望连接的副本库名；apply 时必须与实际 {@code SELECT DATABASE()} 完全一致。 */
    private String expectedDatabaseName;

    /** 显式报告输出目录；apply 时必须可写，dry-run 可选。 */
    private String reportDir;

    /** 生产副本环境确认；apply 时必须为 true（作为第二层，不是唯一依据）。 */
    private boolean copyEnvironmentAck = false;

    /** 显式操作人；apply 必须提供（写入审计报告），preview 可留空并记为 PREVIEW。 */
    private String operator;

    public boolean isApply() { return apply; }
    public void setApply(boolean apply) { this.apply = apply; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getMappingFile() { return mappingFile; }
    public void setMappingFile(String mappingFile) { this.mappingFile = mappingFile; }
    public String getExpectedDatabaseName() { return expectedDatabaseName; }
    public void setExpectedDatabaseName(String expectedDatabaseName) { this.expectedDatabaseName = expectedDatabaseName; }
    public String getReportDir() { return reportDir; }
    public void setReportDir(String reportDir) { this.reportDir = reportDir; }
    public boolean isCopyEnvironmentAck() { return copyEnvironmentAck; }
    public void setCopyEnvironmentAck(boolean copyEnvironmentAck) { this.copyEnvironmentAck = copyEnvironmentAck; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
