package com.blade.outlet.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Series F 历史档口回填工具配置。
 *
 * <p>默认安全：{@code apply=false} 只做 dry-run；任何写库都必须同时显式提供
 * 映射文件、租户、副本环境确认，并通过 {@link OutletBackfillSafetyGate} 的生产特征拒绝。</p>
 */
@Component
@ConfigurationProperties(prefix = "blade.outlet.backfill")
public class OutletBackfillProperties {

    /** 是否执行写库；默认 false（dry-run）。 */
    private boolean apply = false;

    /** 要回填的租户；apply 时必填。 */
    private Long tenantId;

    /** 显式映射 CSV 路径（tenant_id,legacy_source_shop,outlet_code,decision,reason）。 */
    private String mappingFile;

    /** 生产副本环境确认；apply 时必须为 true。 */
    private boolean copyEnvironmentAck = false;

    public boolean isApply() { return apply; }
    public void setApply(boolean apply) { this.apply = apply; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getMappingFile() { return mappingFile; }
    public void setMappingFile(String mappingFile) { this.mappingFile = mappingFile; }
    public boolean isCopyEnvironmentAck() { return copyEnvironmentAck; }
    public void setCopyEnvironmentAck(boolean copyEnvironmentAck) { this.copyEnvironmentAck = copyEnvironmentAck; }
}
