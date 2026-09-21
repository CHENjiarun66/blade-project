package com.blade.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.dto.FileVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.policy.FileBusinessAccessPolicy;
import com.blade.file.service.FileDerivativeService;
import com.blade.file.service.FileService;
import com.blade.file.storage.FileStorageService;
import com.blade.file.storage.StoredFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private final FileStorageMapper fileStorageMapper;
    private final FileStorageService storageService;
    private final FileStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final FileBusinessBindMapper fileBusinessBindMapper;
    private final FileDerivativeService derivativeService;
    private final FileBusinessAccessPolicy fileBusinessAccessPolicy;

    public FileServiceImpl(FileStorageMapper fileStorageMapper,
                           FileStorageService storageService,
                           FileStorageProperties properties,
                           ObjectMapper objectMapper,
                           FileBusinessBindMapper fileBusinessBindMapper,
                           FileDerivativeService derivativeService,
                           FileBusinessAccessPolicy fileBusinessAccessPolicy) {
        this.fileStorageMapper = fileStorageMapper;
        this.storageService = storageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fileBusinessBindMapper = fileBusinessBindMapper;
        this.derivativeService = derivativeService;
        this.fileBusinessAccessPolicy = fileBusinessAccessPolicy;
    }

    @Override
    @Transactional
    public FileUploadVO upload(MultipartFile file, String businessType, Long businessId, Long operatorId) {
        validateFile(file);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw com.blade.common.exception.BusinessException.of(403, "缺少租户上下文");
        }
        // 带业务目标的上传必须在存储文件之前完成目标授权
        if (businessId != null) {
            fileBusinessAccessPolicy.requireTargetAccess(businessType, businessId);
        }
        StoredFile storedFile = storageService.store(file, businessType);

        FileStorage entity = new FileStorage();
        entity.setFileKey(storedFile.getFileKey());
        entity.setOriginalName(file.getOriginalFilename());
        entity.setFileName(storedFile.getFileName());
        entity.setContentType(file.getContentType());
        entity.setFileSize(file.getSize());
        entity.setStorageType(storedFile.getStorageType());
        entity.setStoragePath(storedFile.getStoragePath());
        entity.setBusinessType(businessType);
        entity.setBusinessId(businessId);
        entity.setStatus(1);
        entity.setTenantId(tenantId);
        entity.setCreateBy(operatorId);

        // BE-1010: classify fileType and extract fileExt
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                entity.setFileType("IMAGE");
            } else if (contentType.startsWith("video/")) {
                entity.setFileType("VIDEO");
            } else {
                entity.setFileType("OTHER");
            }
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            entity.setFileExt(ext);
        }

        fileStorageMapper.insert(entity);

        // BE-1012: Generate thumb/card derivatives for IMAGE files.
        // If a transaction is active, register an after-commit callback so the
        // original upload commits independently before derivatives are attempted.
        // If no transaction is active (e.g. unit tests), call directly.
        // A derivative failure never affects the upload.
        if ("IMAGE".equals(entity.getFileType())) {
            final FileStorage entityForDerivative = entity;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    derivativeService.generate(entityForDerivative);
                                } catch (Exception e) {
                                    log.error("Derivative generation failed after commit for fileId={}",
                                            entityForDerivative.getId(), e);
                                }
                            }
                        });
            } else {
                try {
                    derivativeService.generate(entityForDerivative);
                } catch (Exception e) {
                    log.error("Derivative generation failed for fileId={}, upload continues",
                            entityForDerivative.getId(), e);
                }
            }
        }

        String accessUrl = properties.getPreviewUrlPrefix() + "/" + entity.getId() + "/preview";
        entity.setAccessUrl(accessUrl);
        fileStorageMapper.updateById(entity);

        // The file center treats file_business_bind as the canonical source of
        // binding state.  When an existing business object is supplied (for
        // example, adding an image from the order edit page), create that
        // canonical binding immediately instead of relying on a later order
        // save to repair the legacy file_storage.business_* fields.
        if (businessId != null) {
            bindFiles(businessType, businessId, List.of(entity.getId()));
        }

        FileUploadVO vo = new FileUploadVO();
        vo.setId(entity.getId());
        vo.setOriginalName(entity.getOriginalName());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setUrl(accessUrl);
        vo.setFileType(entity.getFileType());
        vo.setFileExt(entity.getFileExt());
        return vo;
    }

    @Override
    public FileStorage getActiveFile(Long id) {
        Long tenantId = requiredTenantId();
        LambdaQueryWrapper<FileStorage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileStorage::getId, id);
        wrapper.eq(FileStorage::getTenantId, tenantId);
        wrapper.eq(FileStorage::getStatus, 1);
        FileStorage file = fileStorageMapper.selectOne(wrapper);
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        return file;
    }

    @Override
    public Resource loadResource(Long id) {
        FileStorage file = getActiveFile(id);
        return storageService.load(file.getStoragePath());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        FileStorage file = getActiveFile(id);
        fileBusinessAccessPolicy.requireFileRead(file);
        file.setStatus(0);
        fileStorageMapper.updateById(file);
    }

    @Override
    @Transactional
    public void bindFiles(String businessType, Long businessId, List<Long> fileIds) {
        if (businessType == null || businessType.isBlank() || businessId == null || fileIds == null || fileIds.isEmpty()) {
            return;
        }
        Long tenantId = requiredTenantId();
        fileBusinessAccessPolicy.requireTargetAccess(businessType, businessId);
        List<Long> uniqueFileIds = new ArrayList<>(new LinkedHashSet<>(fileIds));
        Long activeCount = fileStorageMapper.selectCount(new LambdaQueryWrapper<FileStorage>()
                .in(FileStorage::getId, uniqueFileIds)
                .eq(FileStorage::getTenantId, tenantId)
                .eq(FileStorage::getStatus, 1));
        if (activeCount != uniqueFileIds.size()) {
            throw new RuntimeException("文件不存在");
        }
        fileBusinessAccessPolicy.requireFilesRead(loadActiveFiles(uniqueFileIds, tenantId));

        LambdaUpdateWrapper<FileStorage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(FileStorage::getId, uniqueFileIds);
        wrapper.eq(FileStorage::getTenantId, tenantId);
        wrapper.eq(FileStorage::getStatus, 1);
        wrapper.set(FileStorage::getBusinessType, businessType);
        wrapper.set(FileStorage::getBusinessId, businessId);
        fileStorageMapper.update(null, wrapper);

        int sort = 0;
        for (Long fileId : uniqueFileIds) {
            Long existing = fileBusinessBindMapper.selectCount(new LambdaQueryWrapper<FileBusinessBind>()
                    .eq(FileBusinessBind::getFileId, fileId)
                    .eq(FileBusinessBind::getBusinessType, businessType)
                    .eq(FileBusinessBind::getBusinessId, businessId)
                    .eq(FileBusinessBind::getTenantId, tenantId)
                    .eq(FileBusinessBind::getDeleted, 0));
            if (existing == 0) {
                FileBusinessBind bind = new FileBusinessBind();
                bind.setFileId(fileId);
                bind.setBusinessType(businessType);
                bind.setBusinessId(businessId);
                bind.setBindRole("order_draft".equals(businessType) ? "source" : "attachment");
                bind.setSort(sort);
                bind.setIsPrimary(sort == 0 ? 1 : 0);
                bind.setTenantId(tenantId);
                bind.setDeleted(0);
                fileBusinessBindMapper.insert(bind);
            }
            sort++;
        }
    }

    @Override
    @Transactional
    public void syncFiles(String businessType, Long businessId, List<Long> fileIds) {
        if (businessType == null || businessType.isBlank() || businessId == null) {
            throw new IllegalArgumentException("businessType和businessId不能为空");
        }
        Long tenantId = requiredTenantId();
        fileBusinessAccessPolicy.requireTargetAccess(businessType, businessId);
        List<Long> targetFileIds = fileIds == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(fileIds));
        if (!targetFileIds.isEmpty()) {
            Long activeCount = fileStorageMapper.selectCount(new LambdaQueryWrapper<FileStorage>()
                    .in(FileStorage::getId, targetFileIds)
                    .eq(FileStorage::getTenantId, tenantId)
                    .eq(FileStorage::getStatus, 1));
            if (activeCount != targetFileIds.size()) {
                throw new RuntimeException("文件不存在");
            }
            fileBusinessAccessPolicy.requireFilesRead(loadActiveFiles(targetFileIds, tenantId));
        }

        List<FileBusinessBind> activeBindings = fileBusinessBindMapper.selectList(
                new LambdaQueryWrapper<FileBusinessBind>()
                        .eq(FileBusinessBind::getBusinessType, businessType)
                        .eq(FileBusinessBind::getBusinessId, businessId)
                        .eq(FileBusinessBind::getTenantId, tenantId)
                        .eq(FileBusinessBind::getDeleted, 0)
                        .orderByAsc(FileBusinessBind::getSort)
                        .orderByAsc(FileBusinessBind::getId));
        // 被移除的旧绑定文件也必须在操作前可读
        fileBusinessAccessPolicy.requireFilesRead(loadActiveFiles(
                activeBindings.stream().map(FileBusinessBind::getFileId).distinct().toList(), tenantId));
        Set<Long> targetSet = new LinkedHashSet<>(targetFileIds);
        Set<Long> retainedFileIds = new LinkedHashSet<>();
        Set<Long> removedFileIds = new LinkedHashSet<>();

        for (FileBusinessBind bind : activeBindings) {
            boolean retain = targetSet.contains(bind.getFileId()) && retainedFileIds.add(bind.getFileId());
            if (retain) continue;
            fileBusinessBindMapper.update(null, new LambdaUpdateWrapper<FileBusinessBind>()
                    .eq(FileBusinessBind::getId, bind.getId())
                    .eq(FileBusinessBind::getTenantId, tenantId)
                    .eq(FileBusinessBind::getDeleted, 0)
                    .set(FileBusinessBind::getDeleted, 1));
            if (!targetSet.contains(bind.getFileId())) {
                removedFileIds.add(bind.getFileId());
            }
        }

        String bindRole = "order_draft".equals(businessType) ? "source" : "attachment";
        for (int sort = 0; sort < targetFileIds.size(); sort++) {
            Long fileId = targetFileIds.get(sort);
            FileBusinessBind existing = activeBindings.stream()
                    .filter(bind -> retainedFileIds.contains(bind.getFileId()))
                    .filter(bind -> fileId.equals(bind.getFileId()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                FileBusinessBind bind = new FileBusinessBind();
                bind.setFileId(fileId);
                bind.setBusinessType(businessType);
                bind.setBusinessId(businessId);
                bind.setBindRole(bindRole);
                bind.setSort(sort);
                bind.setIsPrimary(sort == 0 ? 1 : 0);
                bind.setTenantId(tenantId);
                bind.setDeleted(0);
                fileBusinessBindMapper.insert(bind);
            } else {
                fileBusinessBindMapper.update(null, new LambdaUpdateWrapper<FileBusinessBind>()
                        .eq(FileBusinessBind::getId, existing.getId())
                        .eq(FileBusinessBind::getTenantId, tenantId)
                        .eq(FileBusinessBind::getDeleted, 0)
                        .set(FileBusinessBind::getBindRole, bindRole)
                        .set(FileBusinessBind::getSort, sort)
                        .set(FileBusinessBind::getIsPrimary, sort == 0 ? 1 : 0));
            }
        }

        if (!removedFileIds.isEmpty()) {
            fileStorageMapper.update(null, new LambdaUpdateWrapper<FileStorage>()
                    .in(FileStorage::getId, removedFileIds)
                    .eq(FileStorage::getTenantId, tenantId)
                    .eq(FileStorage::getBusinessType, businessType)
                    .eq(FileStorage::getBusinessId, businessId)
                    .set(FileStorage::getBusinessType, null)
                    .set(FileStorage::getBusinessId, null));
        }
        if (!targetFileIds.isEmpty()) {
            fileStorageMapper.update(null, new LambdaUpdateWrapper<FileStorage>()
                    .in(FileStorage::getId, targetFileIds)
                    .eq(FileStorage::getTenantId, tenantId)
                    .eq(FileStorage::getStatus, 1)
                    .set(FileStorage::getBusinessType, businessType)
                    .set(FileStorage::getBusinessId, businessId));
        }
    }

    @Override
    public void bindFilesFromJson(String businessType, Long businessId, String imagesJson) {
        bindFiles(businessType, businessId, parseFileIds(imagesJson));
    }

    @Override
    public void syncFilesFromJson(String businessType, Long businessId, String imagesJson) {
        syncFiles(businessType, businessId, parseFileIds(imagesJson));
    }

    @Override
    public List<Long> getActiveFileIds(String businessType, Long businessId) {
        if (businessType == null || businessType.isBlank() || businessId == null) {
            return List.of();
        }
        Long tenantId = requiredTenantId();
        List<Long> boundIds = fileBusinessBindMapper.selectList(
                        new LambdaQueryWrapper<FileBusinessBind>()
                                .eq(FileBusinessBind::getBusinessType, businessType)
                                .eq(FileBusinessBind::getBusinessId, businessId)
                                .eq(FileBusinessBind::getTenantId, tenantId)
                                .eq(FileBusinessBind::getDeleted, 0)
                                .orderByAsc(FileBusinessBind::getSort)
                                .orderByAsc(FileBusinessBind::getId))
                .stream()
                .map(FileBusinessBind::getFileId)
                .distinct()
                .toList();
        if (!boundIds.isEmpty()) {
            Set<Long> activeIds = fileStorageMapper.selectList(new LambdaQueryWrapper<FileStorage>()
                            .in(FileStorage::getId, boundIds)
                            .eq(FileStorage::getTenantId, tenantId)
                            .eq(FileStorage::getStatus, 1))
                    .stream()
                    .map(FileStorage::getId)
                    .collect(Collectors.toSet());
            return boundIds.stream().filter(activeIds::contains).toList();
        }

        return fileStorageMapper.selectList(new LambdaQueryWrapper<FileStorage>()
                        .eq(FileStorage::getBusinessType, businessType)
                        .eq(FileStorage::getBusinessId, businessId)
                        .eq(FileStorage::getTenantId, tenantId)
                        .eq(FileStorage::getStatus, 1)
                        .orderByAsc(FileStorage::getId))
                .stream()
                .map(FileStorage::getId)
                .toList();
    }

    // ==================== BE-1002: 分页列表 ====================

    @Override
    public PageResult<FileVO> pageList(FilePageDTO dto) {
        Long tenantId = requiredTenantId();

        LambdaQueryWrapper<FileStorage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileStorage::getTenantId, tenantId);
        // Series E2：可见性在 count/page SQL 之前应用，禁止先分页后 Java 过滤
        wrapper.apply(fileBusinessAccessPolicy.buildVisibilityCondition());

        // 关键字搜索
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String kw = dto.getKeyword().trim();
            wrapper.and(w -> {
                w.like(FileStorage::getOriginalName, kw)
                 .or().like(FileStorage::getFileName, kw);
                // 如果关键字是数字，也按 ID 搜索
                if (kw.matches("\\d+")) {
                    w.or().eq(FileStorage::getId, Long.parseLong(kw));
                }
            });
        }

        // 筛选条件
        if (dto.getFolderId() != null) {
            wrapper.eq(FileStorage::getFolderId, dto.getFolderId());
        }
        if (dto.getFileType() != null && !dto.getFileType().isBlank()) {
            wrapper.eq(FileStorage::getFileType, dto.getFileType());
        }
        if (dto.getPurpose() != null && !dto.getPurpose().isBlank()) {
            wrapper.eq(FileStorage::getPurpose, dto.getPurpose());
        }
        if (dto.getCreateBy() != null) {
            wrapper.eq(FileStorage::getCreateBy, dto.getCreateBy());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(FileStorage::getStatus, dto.getStatus());
        }

        // 时间范围
        if (dto.getStartDate() != null && !dto.getStartDate().isBlank()) {
            LocalDateTime start = LocalDate.parse(dto.getStartDate(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            wrapper.ge(FileStorage::getCreateTime, start);
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isBlank()) {
            LocalDateTime end = LocalDate.parse(dto.getEndDate(), DateTimeFormatter.ISO_LOCAL_DATE).atTime(LocalTime.MAX);
            wrapper.le(FileStorage::getCreateTime, end);
        }

        // 业务类型和绑定状态均基于 file_business_bind，不再依赖 file_storage 的旧 business 字段。
        boolean hasBusinessType = dto.getBusinessType() != null && !dto.getBusinessType().isBlank();
        if (hasBusinessType || dto.getBound() != null) {
            List<Long> boundFileIds = findBoundFileIds(tenantId, hasBusinessType ? dto.getBusinessType().trim() : null);
            if (Boolean.TRUE.equals(dto.getBound()) || (hasBusinessType && dto.getBound() == null)) {
                if (boundFileIds.isEmpty()) {
                    return PageResult.of(List.of(), 0, dto.getSize(), dto.getCurrent());
                }
                wrapper.in(FileStorage::getId, boundFileIds);
            } else if (Boolean.FALSE.equals(dto.getBound()) && !boundFileIds.isEmpty()) {
                wrapper.notIn(FileStorage::getId, boundFileIds);
            }
        }

        // 按创建时间倒序
        wrapper.orderByDesc(FileStorage::getCreateTime);

        // 分页查询
        Page<FileStorage> page = fileStorageMapper.selectPage(
                new Page<>(dto.getCurrent(), dto.getSize()), wrapper);

        // 转 VO 并填充 bound 标志
        List<FileVO> records = page.getRecords().stream()
                .map(this::toFileVO)
                .collect(Collectors.toList());

        fillBoundFlags(records);

        return PageResult.of(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    // ==================== BE-1002: 文件详情 ====================

    @Override
    public List<FileBusinessBind> getActiveBindings(Long fileId) {
        Long tenantId = requiredTenantId();
        LambdaQueryWrapper<FileBusinessBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileBusinessBind::getFileId, fileId);
        wrapper.eq(FileBusinessBind::getTenantId, tenantId);
        wrapper.eq(FileBusinessBind::getDeleted, 0);
        return fileBusinessBindMapper.selectList(wrapper);
    }

    @Override
    public FileVO getDetail(Long id) {
        Long tenantId = requiredTenantId();

        LambdaQueryWrapper<FileStorage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileStorage::getId, id);
        wrapper.eq(FileStorage::getTenantId, tenantId);
        FileStorage entity = fileStorageMapper.selectOne(wrapper);

        if (entity == null) {
            throw new RuntimeException("文件不存在");
        }
        fileBusinessAccessPolicy.requireFileRead(entity);

        FileVO vo = toFileVO(entity);

        // 查询绑定状态
        Long bindCount = fileBusinessBindMapper.selectCount(
                new LambdaQueryWrapper<FileBusinessBind>()
                        .eq(FileBusinessBind::getFileId, id)
                        .eq(FileBusinessBind::getDeleted, 0));
        vo.setBound(bindCount > 0);

        return vo;
    }

    // ==================== 内部辅助方法 ====================

    private FileVO toFileVO(FileStorage entity) {
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setFileKey(entity.getFileKey());
        vo.setOriginalName(entity.getOriginalName());
        vo.setFileName(entity.getFileName());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setAccessUrl(entity.getAccessUrl());
        vo.setFolderId(entity.getFolderId());
        vo.setFileType(entity.getFileType());
        vo.setFileExt(entity.getFileExt());
        vo.setSource(entity.getSource());
        vo.setPurpose(entity.getPurpose());
        vo.setBusinessType(entity.getBusinessType());
        vo.setBusinessId(entity.getBusinessId());
        vo.setBindCount(entity.getBindCount());
        vo.setVisibility(entity.getVisibility());
        vo.setImageWidth(entity.getImageWidth());
        vo.setImageHeight(entity.getImageHeight());
        vo.setDurationSeconds(entity.getDurationSeconds());
        vo.setCoverFileId(entity.getCoverFileId());
        vo.setStatus(entity.getStatus());
        vo.setCreateBy(entity.getCreateBy());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setDeletedTime(entity.getDeletedTime());
        return vo;
    }

    private void fillBoundFlags(List<FileVO> records) {
        if (records.isEmpty()) return;

        Set<Long> fileIds = records.stream().map(FileVO::getId).collect(Collectors.toSet());

        // 批量查询这些文件是否有绑定记录
        List<FileBusinessBind> binds = fileBusinessBindMapper.selectList(
                new LambdaQueryWrapper<FileBusinessBind>()
                        .in(FileBusinessBind::getFileId, fileIds)
                        .eq(FileBusinessBind::getDeleted, 0));

        Set<Long> boundFileIds = binds.stream()
                .map(FileBusinessBind::getFileId)
                .collect(Collectors.toSet());

        for (FileVO vo : records) {
            vo.setBound(boundFileIds.contains(vo.getId()));
        }
    }

    private List<Long> findBoundFileIds(Long tenantId, String businessType) {
        LambdaQueryWrapper<FileBusinessBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(FileBusinessBind::getFileId);
        wrapper.eq(FileBusinessBind::getTenantId, tenantId);
        wrapper.eq(FileBusinessBind::getDeleted, 0);
        if (businessType != null && !businessType.isBlank()) {
            // "Order images" in the file center is a business category, not
            // only a formal-order lifecycle state.  Draft source photos are
            // therefore included together with confirmed-order attachments.
            if ("order".equals(businessType)) {
                wrapper.in(FileBusinessBind::getBusinessType, List.of("order", "order_draft"));
            } else {
                wrapper.eq(FileBusinessBind::getBusinessType, businessType);
            }
        }
        return fileBusinessBindMapper.selectList(wrapper).stream()
                .map(FileBusinessBind::getFileId)
                .distinct()
                .collect(Collectors.toList());
    }

    private Long requiredTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw com.blade.common.exception.BusinessException.of(403, "缺少租户上下文");
        }
        return tenantId;
    }

    private List<FileStorage> loadActiveFiles(List<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return fileStorageMapper.selectList(new LambdaQueryWrapper<FileStorage>()
                .in(FileStorage::getId, ids)
                .eq(FileStorage::getTenantId, tenantId)
                .eq(FileStorage::getStatus, 1));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        long maxBytes = properties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new RuntimeException("文件大小不能超过 " + properties.getMaxSizeMb() + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedTypes().contains(contentType)) {
            throw new RuntimeException("不支持的文件类型");
        }
    }

    private List<Long> parseFileIds(String imagesJson) {
        List<Long> ids = new ArrayList<>();
        if (imagesJson == null || imagesJson.isBlank()) {
            return ids;
        }
        try {
            List<String> values = objectMapper.readValue(imagesJson, new TypeReference<>() {});
            for (String value : values) {
                if (value != null && value.matches("\\d+")) {
                    ids.add(Long.valueOf(value));
                }
            }
        } catch (Exception ignored) {
            if (imagesJson.matches("\\d+")) {
                ids.add(Long.valueOf(imagesJson));
            }
        }
        return ids;
    }
}
