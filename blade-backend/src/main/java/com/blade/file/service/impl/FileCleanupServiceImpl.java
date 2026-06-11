package com.blade.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileCleanupLog;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileCleanupLogMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileCleanupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FileCleanupServiceImpl implements FileCleanupService {

    private final FileStorageMapper fileStorageMapper;
    private final FileBusinessBindMapper fileBusinessBindMapper;
    private final FileCleanupLogMapper fileCleanupLogMapper;

    public FileCleanupServiceImpl(FileStorageMapper fileStorageMapper,
                                  FileBusinessBindMapper fileBusinessBindMapper,
                                  FileCleanupLogMapper fileCleanupLogMapper) {
        this.fileStorageMapper = fileStorageMapper;
        this.fileBusinessBindMapper = fileBusinessBindMapper;
        this.fileCleanupLogMapper = fileCleanupLogMapper;
    }

    // ==================== countUnboundCandidates ====================

    @Override
    public long countUnboundCandidates(int retentionDays) {
        validateRetentionDays(retentionDays);
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        // 1. 查找所有当前租户有绑定记录的 fileId
        Set<Long> boundFileIds = getBoundFileIds(tenantId);

        // 2. 统计：status=1, folder_id IS NULL, createTime < cutoff, 不在 boundFileIds 中
        LambdaQueryWrapper<FileStorage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileStorage::getTenantId, tenantId);
        wrapper.eq(FileStorage::getStatus, 1);
        wrapper.isNull(FileStorage::getFolderId);
        wrapper.lt(FileStorage::getCreateTime, cutoff);
        if (!boundFileIds.isEmpty()) {
            wrapper.notIn(FileStorage::getId, boundFileIds);
        }
        return fileStorageMapper.selectCount(wrapper);
    }

    // ==================== softDeleteUnbound ====================

    @Override
    @Transactional
    public long softDeleteUnbound(int retentionDays) {
        validateRetentionDays(retentionDays);
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        Set<Long> boundFileIds = getBoundFileIds(tenantId);

        // 查询符合条件文件的 ID
        LambdaQueryWrapper<FileStorage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileStorage::getTenantId, tenantId);
        queryWrapper.eq(FileStorage::getStatus, 1);
        queryWrapper.isNull(FileStorage::getFolderId);
        queryWrapper.lt(FileStorage::getCreateTime, cutoff);
        if (!boundFileIds.isEmpty()) {
            queryWrapper.notIn(FileStorage::getId, boundFileIds);
        }
        // 只查询需要的字段
        queryWrapper.select(FileStorage::getId, FileStorage::getStoragePath, FileStorage::getFileSize, FileStorage::getTenantId);
        List<FileStorage> candidates = fileStorageMapper.selectList(queryWrapper);

        if (candidates.isEmpty()) {
            return 0L;
        }

        List<Long> ids = candidates.stream().map(FileStorage::getId).collect(Collectors.toList());

        // 批量软删除
        LambdaUpdateWrapper<FileStorage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(FileStorage::getId, ids);
        updateWrapper.eq(FileStorage::getTenantId, tenantId);
        updateWrapper.eq(FileStorage::getStatus, 1);
        updateWrapper.set(FileStorage::getStatus, 0);
        updateWrapper.set(FileStorage::getDeletedTime, LocalDateTime.now());
        fileStorageMapper.update(null, updateWrapper);

        // 写入清理日志
        for (FileStorage f : candidates) {
            FileCleanupLog log = new FileCleanupLog();
            log.setFileId(f.getId());
            log.setCleanupType("soft_delete_unbound");
            log.setStoragePath(f.getStoragePath());
            log.setFileSize(f.getFileSize() != null ? f.getFileSize() : 0L);
            log.setReason("未绑定未归档超过 " + retentionDays + " 天");
            log.setTenantId(tenantId);
            fileCleanupLogMapper.insert(log);
        }

        return candidates.size();
    }

    // ==================== markPurged ====================

    @Override
    @Transactional
    public long markPurged(int retentionDays) {
        validateRetentionDays(retentionDays);
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        Set<Long> boundFileIds = getBoundFileIds(tenantId);

        // 构建查询：status=0, deletedTime < cutoff, purgedTime IS NULL, tenantId
        // 且 (purpose IN ('temp','ocr','import') OR (purpose IS NULL AND 无绑定 AND folder_id IS NULL))
        LambdaQueryWrapper<FileStorage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileStorage::getTenantId, tenantId);
        wrapper.eq(FileStorage::getStatus, 0);
        wrapper.lt(FileStorage::getDeletedTime, cutoff);
        wrapper.isNull(FileStorage::getPurgedTime);
        wrapper.isNull(FileStorage::getFolderId);

        // 条件：purpose in safe-cleanup list OR (purpose null AND no binding AND no folder)
        wrapper.and(w -> {
            w.in(FileStorage::getPurpose, "temp", "ocr", "import");
            w.or(w2 -> w2.isNull(FileStorage::getPurpose));
        });
        if (!boundFileIds.isEmpty()) {
            wrapper.notIn(FileStorage::getId, boundFileIds);
        }

        wrapper.select(FileStorage::getId, FileStorage::getStoragePath, FileStorage::getFileSize,
                FileStorage::getTenantId, FileStorage::getPurpose);
        List<FileStorage> candidates = fileStorageMapper.selectList(wrapper);

        if (candidates.isEmpty()) {
            return 0L;
        }

        List<FileStorage> qualified = candidates.stream()
                .filter(f -> isSafeCleanupPurpose(f.getPurpose()))
                .filter(f -> !boundFileIds.contains(f.getId()))
                .collect(Collectors.toList());

        if (qualified.isEmpty()) {
            return 0L;
        }

        List<Long> ids = qualified.stream().map(FileStorage::getId).collect(Collectors.toList());

        // 标记 purgedTime
        LambdaUpdateWrapper<FileStorage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(FileStorage::getId, ids);
        updateWrapper.eq(FileStorage::getTenantId, tenantId);
        updateWrapper.eq(FileStorage::getStatus, 0);
        updateWrapper.isNull(FileStorage::getPurgedTime);
        updateWrapper.set(FileStorage::getPurgedTime, LocalDateTime.now());
        fileStorageMapper.update(null, updateWrapper);

        // 写入清理日志
        for (FileStorage f : qualified) {
            FileCleanupLog log = new FileCleanupLog();
            log.setFileId(f.getId());
            log.setCleanupType("purge_mark");
            log.setStoragePath(f.getStoragePath());
            log.setFileSize(f.getFileSize() != null ? f.getFileSize() : 0L);
            log.setReason("软删除超过 " + retentionDays + " 天，标记元数据清理");
            log.setTenantId(tenantId);
            fileCleanupLogMapper.insert(log);
        }

        return qualified.size();
    }

    // ==================== 内部辅助 ====================

    /**
     * 获取当前租户所有有有效绑定的 fileId。
     */
    private Set<Long> getBoundFileIds(Long tenantId) {
        LambdaQueryWrapper<FileBusinessBind> bindQuery = new LambdaQueryWrapper<>();
        bindQuery.select(FileBusinessBind::getFileId);
        bindQuery.eq(FileBusinessBind::getTenantId, tenantId);
        bindQuery.eq(FileBusinessBind::getDeleted, 0);
        return fileBusinessBindMapper.selectList(bindQuery).stream()
                .map(FileBusinessBind::getFileId)
                .collect(Collectors.toSet());
    }

    private void validateRetentionDays(int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be greater than 0");
        }
    }

    private boolean isSafeCleanupPurpose(String purpose) {
        return purpose == null
                || "temp".equals(purpose)
                || "ocr".equals(purpose)
                || "import".equals(purpose);
    }
}
