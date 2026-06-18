package com.blade.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "blade.file")
public class FileStorageProperties {

    private String storageType = "local";
    private String localBasePath = "uploads";
    private String previewUrlPrefix = "/api/files";
    private Long maxSizeMb = 200L;
    private List<String> allowedTypes = new ArrayList<>(List.of(
            "image/jpeg", "image/png", "image/webp",
            "video/mp4", "video/webm", "video/quicktime"));

    @NestedConfigurationProperty
    private Cleanup cleanup = new Cleanup();

    @NestedConfigurationProperty
    private Derivative derivative = new Derivative();

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public String getLocalBasePath() { return localBasePath; }
    public void setLocalBasePath(String localBasePath) { this.localBasePath = localBasePath; }
    public String getPreviewUrlPrefix() { return previewUrlPrefix; }
    public void setPreviewUrlPrefix(String previewUrlPrefix) { this.previewUrlPrefix = previewUrlPrefix; }
    public Long getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(Long maxSizeMb) { this.maxSizeMb = maxSizeMb; }
    public List<String> getAllowedTypes() { return allowedTypes; }
    public void setAllowedTypes(List<String> allowedTypes) { this.allowedTypes = allowedTypes; }
    public Cleanup getCleanup() { return cleanup; }
    public void setCleanup(Cleanup cleanup) { this.cleanup = cleanup; }
    public Derivative getDerivative() { return derivative; }
    public void setDerivative(Derivative derivative) { this.derivative = derivative; }

    /**
     * File cleanup configuration (BE-1007/BE-1008).
     */
    public static class Cleanup {
        /** Whether auto-cleanup scheduler is enabled (default: false — safe by default) */
        private boolean enabled = false;
        /** Unbound file retention days before soft-delete (default: 7) */
        private int unboundRetentionDays = 7;
        /** Soft-deleted file retention days before purge metadata marking (default: 30) */
        private int purgeRetentionDays = 30;
        /** Cron expression for scheduled cleanup (default: daily 3AM) */
        private String cron = "0 0 3 * * ?";
        /** Tenant used by the first scheduler slice. Full multi-tenant iteration is a later task. */
        private Long tenantId = 1L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getUnboundRetentionDays() { return unboundRetentionDays; }
        public void setUnboundRetentionDays(int unboundRetentionDays) { this.unboundRetentionDays = unboundRetentionDays; }
        public int getPurgeRetentionDays() { return purgeRetentionDays; }
        public void setPurgeRetentionDays(int purgeRetentionDays) { this.purgeRetentionDays = purgeRetentionDays; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    }

    /**
     * Image derivative configuration (BE-1012).
     */
    public static class Derivative {
        /** Whether derivative generation is enabled (default: true) */
        private boolean enabled = true;
        /** Thumbnail long edge in pixels (default: 320) */
        private int thumbLongEdge = 320;
        /** Card image long edge in pixels (default: 800) */
        private int cardLongEdge = 800;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getThumbLongEdge() { return thumbLongEdge; }
        public void setThumbLongEdge(int thumbLongEdge) { this.thumbLongEdge = thumbLongEdge; }
        public int getCardLongEdge() { return cardLongEdge; }
        public void setCardLongEdge(int cardLongEdge) { this.cardLongEdge = cardLongEdge; }
    }
}
