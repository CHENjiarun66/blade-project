package com.blade.file.scheduler;

import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.service.FileCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文件清理定时任务。
 * 默认 disabled（blade.file.cleanup.enabled=false），需手动启用。
 * 启用后按配置的 cron 表达式执行两步。
 * 第一版只处理配置的 tenantId；全租户遍历另行实现。
 * 1. 软删除未绑定文件
 * 2. 标记可清理的软删除文件为已清理（仅元数据）
 */
@Component
@ConditionalOnProperty(value = "blade.file.cleanup.enabled", havingValue = "true")
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final FileCleanupService fileCleanupService;
    private final FileStorageProperties properties;

    public FileCleanupScheduler(FileCleanupService fileCleanupService,
                                 FileStorageProperties properties) {
        this.fileCleanupService = fileCleanupService;
        this.properties = properties;
    }

    @Scheduled(cron = "${blade.file.cleanup.cron:0 0 3 * * ?}")
    public void runCleanup() {
        log.info("文件清理定时任务开始执行");

        Long tenantId = properties.getCleanup().getTenantId() != null
                ? properties.getCleanup().getTenantId()
                : 1L;
        TenantContext.setTenantId(tenantId);
        try {
            // Step 1: 软删除未绑定文件
            int unboundDays = properties.getCleanup().getUnboundRetentionDays();
            long unboundCount = fileCleanupService.softDeleteUnbound(unboundDays);
            log.info("未绑定文件软删除完成，处理 {} 个文件（保留 {} 天）", unboundCount, unboundDays);

            // Step 2: 标记可清理的软删除文件
            int purgeDays = properties.getCleanup().getPurgeRetentionDays();
            long purgedCount = fileCleanupService.markPurged(purgeDays);
            log.info("清理标记完成，标记 {} 个文件（保留 {} 天）", purgedCount, purgeDays);

            log.info("文件清理定时任务执行完成");
        } catch (Exception e) {
            log.error("文件清理定时任务执行异常", e);
        } finally {
            TenantContext.clear();
        }
    }
}
