package com.blade.outlet.migration;

import java.nio.file.Path;
import java.time.Instant;

/**
 * apply 的副本审批凭证。
 *
 * <p>构造器为包内可见，只有 {@link OutletBackfillSafetyGate#approve(OutletBackfillProperties)}
 * 与同包测试可以创建；外部包无法直接构造，避免绕过安全闸门调用写入入口。</p>
 */
public final class OutletBackfillApproval {

    private final long tenantId;
    private final Path reportDir;
    private final String databaseName;
    private final String mappingFile;
    private final String operator;
    private final Instant approvedAt;

    OutletBackfillApproval(long tenantId, Path reportDir, String databaseName,
                           String mappingFile, String operator, Instant approvedAt) {
        this.tenantId = tenantId;
        this.reportDir = reportDir;
        this.databaseName = databaseName;
        this.mappingFile = mappingFile;
        this.operator = operator;
        this.approvedAt = approvedAt;
    }

    public long tenantId() { return tenantId; }
    public Path reportDir() { return reportDir; }
    public String databaseName() { return databaseName; }
    public String mappingFile() { return mappingFile; }
    public String operator() { return operator; }
    public Instant approvedAt() { return approvedAt; }
}
