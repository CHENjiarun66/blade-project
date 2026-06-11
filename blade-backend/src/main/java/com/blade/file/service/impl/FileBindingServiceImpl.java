package com.blade.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.dto.FileBatchDeleteDTO;
import com.blade.file.dto.FileBatchMoveDTO;
import com.blade.file.dto.FileBindingCreateDTO;
import com.blade.file.dto.FileBindingVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileFolder;
import com.blade.file.entity.FileOperationLog;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileFolderMapper;
import com.blade.file.mapper.FileOperationLogMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileBindingService;
import com.blade.system.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileBindingServiceImpl implements FileBindingService {

    private final FileStorageMapper fileStorageMapper;
    private final FileBusinessBindMapper fileBusinessBindMapper;
    private final FileOperationLogMapper fileOperationLogMapper;
    private final FileFolderMapper fileFolderMapper;

    public FileBindingServiceImpl(FileStorageMapper fileStorageMapper,
                                  FileBusinessBindMapper fileBusinessBindMapper,
                                  FileOperationLogMapper fileOperationLogMapper,
                                  FileFolderMapper fileFolderMapper) {
        this.fileStorageMapper = fileStorageMapper;
        this.fileBusinessBindMapper = fileBusinessBindMapper;
        this.fileOperationLogMapper = fileOperationLogMapper;
        this.fileFolderMapper = fileFolderMapper;
    }

    // ==================== GET /api/files/{id}/bindings ====================

    @Override
    public List<FileBindingVO> getBindings(Long fileId) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        LambdaQueryWrapper<FileBusinessBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileBusinessBind::getFileId, fileId);
        wrapper.eq(FileBusinessBind::getTenantId, tenantId);
        wrapper.eq(FileBusinessBind::getDeleted, 0);
        wrapper.orderByAsc(FileBusinessBind::getSort);

        return fileBusinessBindMapper.selectList(wrapper).stream()
                .map(this::toBindingVO)
                .collect(Collectors.toList());
    }

    // ==================== POST /api/files/bindings ====================

    @Override
    @Transactional
    public void createBindings(FileBindingCreateDTO dto) {
        if (dto.getFileIds() == null || dto.getFileIds().isEmpty()) return;
        if (dto.getBusinessType() == null || dto.getBusinessType().isBlank()
                || dto.getBusinessId() == null) {
            throw new RuntimeException("businessType和businessId不能为空");
        }

        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        Long operatorId = getCurrentUserId();

        // 验证所有文件存在且属于当前租户且 status=1
        Long fileCount = fileStorageMapper.selectCount(
                new LambdaQueryWrapper<FileStorage>()
                        .in(FileStorage::getId, dto.getFileIds())
                        .eq(FileStorage::getTenantId, tenantId)
                        .eq(FileStorage::getStatus, 1));
        if (fileCount != dto.getFileIds().size()) {
            throw new RuntimeException("文件不存在");
        }

        // 批量插入绑定
        int sort = 0;
        for (Long fileId : dto.getFileIds()) {
            FileBusinessBind bind = new FileBusinessBind();
            bind.setFileId(fileId);
            bind.setBusinessType(dto.getBusinessType());
            bind.setBusinessId(dto.getBusinessId());
            bind.setBindRole(dto.getBindRole());
            bind.setSort(sort);
            bind.setIsPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : 0);
            bind.setTenantId(tenantId);
            bind.setCreateBy(operatorId);
            bind.setDeleted(0);
            fileBusinessBindMapper.insert(bind);
            sort++;
        }

        // 写入操作日志
        writeLog(dto.getFileIds().get(0), "bind",
                "批量绑定 " + dto.getFileIds().size() + " 个文件到 "
                        + dto.getBusinessType() + ":" + dto.getBusinessId(), tenantId);
    }

    // ==================== DELETE /api/files/bindings/{id} ====================

    @Override
    @Transactional
    public void deleteBinding(Long id) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 验证绑定存在且属于当前租户且未删除
        LambdaQueryWrapper<FileBusinessBind> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileBusinessBind::getId, id);
        queryWrapper.eq(FileBusinessBind::getTenantId, tenantId);
        queryWrapper.eq(FileBusinessBind::getDeleted, 0);
        FileBusinessBind bind = fileBusinessBindMapper.selectOne(queryWrapper);
        if (bind == null) {
            throw new RuntimeException("绑定不存在");
        }

        // 软删除
        LambdaUpdateWrapper<FileBusinessBind> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FileBusinessBind::getId, id);
        updateWrapper.eq(FileBusinessBind::getTenantId, tenantId);
        updateWrapper.eq(FileBusinessBind::getDeleted, 0);
        updateWrapper.set(FileBusinessBind::getDeleted, 1);
        fileBusinessBindMapper.update(null, updateWrapper);

        // 写入操作日志
        writeLog(bind.getFileId(), "unbind",
                "解除绑定 " + bind.getBusinessType() + ":" + bind.getBusinessId(), tenantId);
    }

    // ==================== POST /api/files/batch-delete ====================

    @Override
    @Transactional
    public void batchDelete(FileBatchDeleteDTO dto) {
        if (dto.getFileIds() == null || dto.getFileIds().isEmpty()) return;

        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        List<Long> boundFileIds = fileBusinessBindMapper.selectList(
                        new LambdaQueryWrapper<FileBusinessBind>()
                                .select(FileBusinessBind::getFileId)
                                .in(FileBusinessBind::getFileId, dto.getFileIds())
                                .eq(FileBusinessBind::getTenantId, tenantId)
                                .eq(FileBusinessBind::getDeleted, 0))
                .stream()
                .map(FileBusinessBind::getFileId)
                .distinct()
                .toList();
        if (!boundFileIds.isEmpty()) {
            throw new RuntimeException("文件存在有效绑定，不能删除");
        }

        // 批量软删除: status=0，仅操作当前租户且 status=1 的文件
        LambdaUpdateWrapper<FileStorage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(FileStorage::getId, dto.getFileIds());
        wrapper.eq(FileStorage::getTenantId, tenantId);
        wrapper.eq(FileStorage::getStatus, 1);
        wrapper.set(FileStorage::getStatus, 0);
        fileStorageMapper.update(null, wrapper);

        // 写入操作日志
        writeLog(dto.getFileIds().get(0), "batch_delete",
                "批量删除 " + dto.getFileIds().size() + " 个文件", tenantId);
    }

    // ==================== POST /api/files/batch-move ====================

    @Override
    @Transactional
    public void batchMove(FileBatchMoveDTO dto) {
        if (dto.getFileIds() == null || dto.getFileIds().isEmpty()) return;

        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 如果指定了文件夹，验证文件夹存在且属于当前租户且未删除
        if (dto.getFolderId() != null) {
            LambdaQueryWrapper<FileFolder> folderQuery = new LambdaQueryWrapper<>();
            folderQuery.eq(FileFolder::getId, dto.getFolderId());
            folderQuery.eq(FileFolder::getTenantId, tenantId);
            folderQuery.eq(FileFolder::getDeleted, 0);
            if (fileFolderMapper.selectOne(folderQuery) == null) {
                throw new RuntimeException("文件夹不存在");
            }
        }

        // 批量移动 folder_id，仅操作当前租户且 status=1 的文件
        LambdaUpdateWrapper<FileStorage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(FileStorage::getId, dto.getFileIds());
        wrapper.eq(FileStorage::getTenantId, tenantId);
        wrapper.eq(FileStorage::getStatus, 1);
        wrapper.set(FileStorage::getFolderId, dto.getFolderId());
        fileStorageMapper.update(null, wrapper);

        // 写入操作日志
        String detail = "批量移动 " + dto.getFileIds().size() + " 个文件"
                + (dto.getFolderId() != null ? " 到文件夹 " + dto.getFolderId() : " 到未归档");
        writeLog(dto.getFileIds().get(0), "batch_move", detail, tenantId);
    }

    // ==================== 内部辅助 ====================

    private FileBindingVO toBindingVO(FileBusinessBind entity) {
        FileBindingVO vo = new FileBindingVO();
        vo.setId(entity.getId());
        vo.setFileId(entity.getFileId());
        vo.setBusinessType(entity.getBusinessType());
        vo.setBusinessId(entity.getBusinessId());
        vo.setBindRole(entity.getBindRole());
        vo.setSort(entity.getSort());
        vo.setIsPrimary(entity.getIsPrimary());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private void writeLog(Long fileId, String operationType, String detail, Long tenantId) {
        FileOperationLog log = new FileOperationLog();
        log.setFileId(fileId);
        log.setOperationType(operationType);
        log.setDetail(detail);
        log.setOperatorId(getCurrentUserId());
        log.setTenantId(tenantId);
        fileOperationLogMapper.insert(log);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L;
    }
}
