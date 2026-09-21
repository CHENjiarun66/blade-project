package com.blade.file.scheduler;

import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件清理定时任务（Series E2 第二轮整改）。
 *
 * <p>默认 disabled（{@code blade.file.cleanup.enabled=false}），需手动启用。
 * 多租户语义：未显式配置 {@code blade.file.cleanup.tenant-id} 时，显式遍历存在文件的
 * tenant，每个 tenant try/finally 设置与清理 TenantContext；一个租户失败不阻断其他租户。
 * 若显式配置了单租户，则只处理该租户且不使用默认 1。</p>
 */
@Component
@ConditionalOnProperty(value = "blade.file.cleanup.enabled", havingValue = "true")
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final FileCleanupService fileCleanupService;
    private final FileStorageProperties properties;
    private final FileStorageMapper fileStorageMapper;

    public FileCleanupScheduler(FileCleanupService fileCleanupService,
                                FileStorageProperties properties,
                                FileStorageMapper fileStorageMapper) {
        this.fileCleanupService = fileCleanupService;
        this.properties = properties;
        this.fileStorageMapper = fileStorageMapper;
    }

    @Scheduled(cron = "${blade.file.cleanup.cron:0 0 3 * * ?}")
    public void runCleanup() {
        List<Long> tenantIds = resolveTenants();
        if (tenantIds.isEmpty()) {
            log.info("文件清理定时任务：没有需要处理的租户");
            return;
        }
        log.info("文件清理定时任务开始，租户数 {}", tenantIds.size());
        int unboundDays = properties.getCleanup().getUnboundRetentionDays();
        int purgeDays = properties.getCleanup().getPurgeRetentionDays();

        for (Long tenantId : tenantIds) {
            TenantContext.setTenantId(tenantId);
            try {
                long unboundCount = fileCleanupService.softDeleteUnbound(unboundDays);
                long purgedCount = fileCleanupService.markPurged(purgeDays);
                log.info("租户 {} 清理完成：软删除 {}，标记 {}（保留 {} / {} 天）",
                        tenantId, unboundCount, purgedCount, unboundDays, purgeDays);
            } catch (Exception e) {
                // 单租户失败不得阻断其他租户
                log.error("租户 {} 文件清理失败，继续处理其他租户", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("文件清理定时任务执行完成");
    }

    private List<Long> resolveTenants() {
        Long configured = properties.getCleanup().getTenantId();
        if (configured != null) {
            return List.of(configured);
        }
        return fileStorageMapper.selectDistinctTenantIds();
    }
}
