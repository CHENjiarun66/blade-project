package com.blade.file.service;

/**
 * 文件清理服务 — 仅操作元数据，不做物理删除。
 * BE-1007: 未绑定文件治理
 * BE-1008: 文件清理定时任务
 */
public interface FileCleanupService {

    /**
     * 统计未绑定、未归档、超过保留期的文件数量。
     * @param retentionDays 保留天数
     */
    long countUnboundCandidates(int retentionDays);

    /**
     * 软删除未绑定、未归档、超过保留期的文件（status=0, deletedTime=now）。
     * 写入 file_cleanup_log（cleanupType=soft_delete_unbound）。
     * @param retentionDays 保留天数
     * @return 处理的文件数
     */
    long softDeleteUnbound(int retentionDays);

    /**
     * 标记软删除超过保留期且符合条件的文件为已清理（purgedTime=now）。
     * 仅标记元数据，不物理删除文件。
     * 仅处理 purpose IN ('temp','ocr','import') 或 purpose IS NULL 且无绑定无文件夹的文件。
     * 写入 file_cleanup_log（cleanupType=purge_mark）。
     * @param retentionDays 软删除后保留天数
     * @return 处理的文件数
     */
    long markPurged(int retentionDays);
}
