package com.blade.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.entity.FileDerivative;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileDerivativeMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileDerivativeService;
import com.blade.file.service.ImageDerivativeGenerator;
import com.blade.file.storage.FileStorageService;
import com.blade.file.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class FileDerivativeServiceImpl implements FileDerivativeService {

    private static final Logger log = LoggerFactory.getLogger(FileDerivativeServiceImpl.class);
    private static final int MAX_BACKFILL_LIMIT = 500;

    private final FileDerivativeMapper fileDerivativeMapper;
    private final FileStorageMapper fileStorageMapper;
    private final FileStorageService storageService;
    private final ImageDerivativeGenerator generator;
    private final FileStorageProperties properties;

    public FileDerivativeServiceImpl(FileDerivativeMapper fileDerivativeMapper,
                                     FileStorageMapper fileStorageMapper,
                                     FileStorageService storageService,
                                     ImageDerivativeGenerator generator,
                                     FileStorageProperties properties) {
        this.fileDerivativeMapper = fileDerivativeMapper;
        this.fileStorageMapper = fileStorageMapper;
        this.storageService = storageService;
        this.generator = generator;
        this.properties = properties;
    }

    /**
     * Generate thumb and card derivatives for a newly uploaded IMAGE file.
     * <p>
     * <b>Fix #2:</b> This method runs outside the upload transaction.
     * FileServiceImpl calls it <em>after</em> the upload commits via
     * TransactionSynchronization.afterCommit(). Derivative failures never
     * affect the original upload.
     */
    @Override
    public void generate(FileStorage file) {
        if (!properties.getDerivative().isEnabled()) {
            return;
        }
        if (!"IMAGE".equals(file.getFileType())) {
            return;
        }

        Long tenantId = file.getTenantId();
        String fileKey = file.getFileKey();
        if (fileKey == null || fileKey.isBlank()) {
            log.warn("Cannot generate derivatives for fileId={}: null/blank fileKey", file.getId());
            recordFailed(file.getId(), "thumb", "无法解析原图路径", tenantId);
            recordFailed(file.getId(), "card", "无法解析原图路径", tenantId);
            return;
        }

        byte[] originalBytes;
        try {
            originalBytes = loadOriginalBytes(file);
        } catch (Exception e) {
            log.error("Failed to load original bytes for fileId={}", file.getId(), e);
            recordFailed(file.getId(), "thumb", "无法加载原图: " + e.getMessage(), tenantId);
            recordFailed(file.getId(), "card", "无法加载原图: " + e.getMessage(), tenantId);
            return;
        }

        generateOne(file.getId(), originalBytes, fileKey, "thumb",
                properties.getDerivative().getThumbLongEdge(), tenantId);
        generateOne(file.getId(), originalBytes, fileKey, "card",
                properties.getDerivative().getCardLongEdge(), tenantId);
    }

    @Override
    public Resource loadVariantResource(Long fileId, String variantType) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        LambdaQueryWrapper<FileDerivative> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileDerivative::getFileId, fileId);
        wrapper.eq(FileDerivative::getVariantType, variantType);
        wrapper.eq(FileDerivative::getTenantId, tenantId);
        wrapper.eq(FileDerivative::getStatus, "READY");
        FileDerivative derivative = fileDerivativeMapper.selectOne(wrapper);
        if (derivative == null || derivative.getStoragePath() == null) {
            return null;
        }
        try {
            return storageService.load(derivative.getStoragePath());
        } catch (Exception e) {
            log.warn("Derivative storage unreadable for fileId={} variant={}, falling back to original",
                    fileId, variantType, e);
            return null;
        }
    }

    /**
     * Fix #3: Query only files that are missing at least one READY thumb or card.
     * This prevents starvation — later files always appear if they need generation.
     *
     * Fix #8: Load/path failures write FAILED records for each missing variant,
     * not just increment counters.
     */
    @Override
    public BackfillResult backfill(int limit) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        int batchSize = Math.min(Math.max(1, limit), MAX_BACKFILL_LIMIT);

        // Select IMAGE files with status=1 that are missing READY thumb OR missing READY card.
        // Uses NOT EXISTS to avoid starvation — each call reaches distinct files.
        List<FileStorage> candidates = fileStorageMapper.selectList(
                new LambdaQueryWrapper<FileStorage>()
                        .eq(FileStorage::getTenantId, tenantId)
                        .eq(FileStorage::getStatus, 1)
                        .eq(FileStorage::getFileType, "IMAGE")
                        .and(w -> w
                                .notExists("SELECT 1 FROM file_derivative fd WHERE fd.file_id = file_storage.id AND fd.variant_type = 'thumb' AND fd.status = 'READY' AND fd.tenant_id = " + tenantId)
                                .or()
                                .notExists("SELECT 1 FROM file_derivative fd WHERE fd.file_id = file_storage.id AND fd.variant_type = 'card' AND fd.status = 'READY' AND fd.tenant_id = " + tenantId)
                        )
                        .orderByAsc(FileStorage::getId)
                        .last("LIMIT " + batchSize));

        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        for (FileStorage file : candidates) {
            processed++;

            String fileKey = file.getFileKey();
            if (fileKey == null || fileKey.isBlank()) {
                // Fix #8: write FAILED for both variants, not just increment counter
                recordFailed(file.getId(), "thumb", "无法解析原图路径(fileKey为空)", tenantId);
                recordFailed(file.getId(), "card", "无法解析原图路径(fileKey为空)", tenantId);
                failed++;
                continue;
            }

            byte[] bytes;
            try {
                bytes = loadOriginalBytes(file);
            } catch (Exception e) {
                log.error("Backfill: failed to load bytes for fileId={}", file.getId(), e);
                // Fix #8: write FAILED for both variants
                recordFailed(file.getId(), "thumb", "无法加载原图: " + e.getMessage(), tenantId);
                recordFailed(file.getId(), "card", "无法加载原图: " + e.getMessage(), tenantId);
                failed++;
                continue;
            }

            boolean anySuccess = false;
            if (!isReady(file.getId(), "thumb", tenantId)) {
                anySuccess |= generateOne(file.getId(), bytes, fileKey, "thumb",
                        properties.getDerivative().getThumbLongEdge(), tenantId);
            }
            if (!isReady(file.getId(), "card", tenantId)) {
                anySuccess |= generateOne(file.getId(), bytes, fileKey, "card",
                        properties.getDerivative().getCardLongEdge(), tenantId);
            }
            if (!isReady(file.getId(), "thumb", tenantId) || !isReady(file.getId(), "card", tenantId)) {
                failed++;
            } else {
                succeeded++;
            }
        }

        return new BackfillResult(processed, succeeded, failed, skipped);
    }

    // === private helpers ===

    /**
     * Fix #4: Insert PENDING before generation, then update to READY on success
     * or FAILED on error. All update operations use tenant-qualified wrappers.
     */
    private boolean generateOne(Long fileId, byte[] originalBytes, String originalFileKey,
                                 String variantType, int targetLongEdge, Long tenantId) {
        // 1. Insert or update to PENDING first
        upsertStatus(fileId, variantType, "PENDING", null, tenantId);

        try {
            ImageDerivativeGenerator.DerivativeResult result = generator.generate(originalBytes, targetLongEdge);

            // 2. Store via provider-neutral API (Fix #1)
            StoredFile stored;
            try (InputStream in = new ByteArrayInputStream(result.getBytes())) {
                stored = storageService.storeDerivative(originalFileKey, variantType, in);
            }

            // 3. Update to READY via tenant-qualified wrapper
            LambdaUpdateWrapper<FileDerivative> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FileDerivative::getFileId, fileId)
                    .eq(FileDerivative::getVariantType, variantType)
                    .eq(FileDerivative::getTenantId, tenantId)
                    .set(FileDerivative::getStatus, "READY")
                    .set(FileDerivative::getStorageType, stored.getStorageType())
                    .set(FileDerivative::getStoragePath, stored.getStoragePath())
                    .set(FileDerivative::getContentType, "image/jpeg")
                    .set(FileDerivative::getFileSize, (long) result.getBytes().length)
                    .set(FileDerivative::getWidth, result.getWidth())
                    .set(FileDerivative::getHeight, result.getHeight())
                    .set(FileDerivative::getErrorMessage, null);
            fileDerivativeMapper.update(null, updateWrapper);

            log.info("Derivative {} generated for fileId={} {}x{}", variantType, fileId,
                    result.getWidth(), result.getHeight());
            return true;
        } catch (Exception e) {
            log.error("Failed to generate {} derivative for fileId={}", variantType, fileId, e);
            // Update to FAILED via tenant-qualified wrapper
            String truncated = e.getMessage() != null && e.getMessage().length() > 500
                    ? e.getMessage().substring(0, 500) : e.getMessage();
            LambdaUpdateWrapper<FileDerivative> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FileDerivative::getFileId, fileId)
                    .eq(FileDerivative::getVariantType, variantType)
                    .eq(FileDerivative::getTenantId, tenantId)
                    .set(FileDerivative::getStatus, "FAILED")
                    .set(FileDerivative::getErrorMessage, truncated);
            fileDerivativeMapper.update(null, updateWrapper);

            return false;
        }
    }

    /**
     * Fix #4: Insert a PENDING row or update existing to PENDING.
     */
    private void upsertStatus(Long fileId, String variantType, String status,
                               String errorMessage, Long tenantId) {
        LambdaQueryWrapper<FileDerivative> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileDerivative::getFileId, fileId)
                .eq(FileDerivative::getVariantType, variantType)
                .eq(FileDerivative::getTenantId, tenantId);
        FileDerivative existing = fileDerivativeMapper.selectOne(queryWrapper);

        if (existing != null) {
            LambdaUpdateWrapper<FileDerivative> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FileDerivative::getId, existing.getId())
                    .eq(FileDerivative::getTenantId, tenantId)
                    .set(FileDerivative::getStatus, status)
                    .set(FileDerivative::getErrorMessage, errorMessage);
            fileDerivativeMapper.update(null, updateWrapper);
        } else {
            FileDerivative derivative = new FileDerivative();
            derivative.setFileId(fileId);
            derivative.setVariantType(variantType);
            derivative.setStatus(status);
            derivative.setErrorMessage(errorMessage);
            derivative.setTenantId(tenantId);
            fileDerivativeMapper.insert(derivative);
        }
    }

    private boolean isReady(Long fileId, String variantType, Long tenantId) {
        LambdaQueryWrapper<FileDerivative> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileDerivative::getFileId, fileId);
        wrapper.eq(FileDerivative::getVariantType, variantType);
        wrapper.eq(FileDerivative::getTenantId, tenantId);
        wrapper.eq(FileDerivative::getStatus, "READY");
        return fileDerivativeMapper.selectCount(wrapper) > 0;
    }

    /**
     * Fix #5: recordFailed accepts the explicit tenantId from the FileStorage entity.
     * It does NOT read TenantContext.
     */
    private void recordFailed(Long fileId, String variantType, String errorMessage, Long tenantId) {
        String truncated = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) : errorMessage;
        upsertStatus(fileId, variantType, "FAILED", truncated, tenantId);
    }

    private byte[] loadOriginalBytes(FileStorage file) throws IOException {
        Resource resource = storageService.load(file.getStoragePath());
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }
}
