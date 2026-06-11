package com.blade.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.dto.FileFolderCreateDTO;
import com.blade.file.dto.FileFolderUpdateDTO;
import com.blade.file.dto.FileFolderVO;
import com.blade.file.entity.FileFolder;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileFolderMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileFolderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FileFolderServiceImpl implements FileFolderService {

    private final FileFolderMapper fileFolderMapper;
    private final FileStorageMapper fileStorageMapper;

    public FileFolderServiceImpl(FileFolderMapper fileFolderMapper,
                                 FileStorageMapper fileStorageMapper) {
        this.fileFolderMapper = fileFolderMapper;
        this.fileStorageMapper = fileStorageMapper;
    }

    @Override
    public List<FileFolderVO> getTree() {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        LambdaQueryWrapper<FileFolder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileFolder::getTenantId, tenantId);
        wrapper.eq(FileFolder::getDeleted, 0);
        wrapper.orderByAsc(FileFolder::getSort);
        wrapper.orderByAsc(FileFolder::getId);

        List<FileFolder> allFolders = fileFolderMapper.selectList(wrapper);

        // 按 parentId 分组
        Map<Long, List<FileFolderVO>> childrenMap = allFolders.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getParentId() != null ? f.getParentId() : 0L,
                        Collectors.mapping(this::toVO, Collectors.toList())));

        // 为每个节点填充 children
        for (List<FileFolderVO> list : childrenMap.values()) {
            for (FileFolderVO vo : list) {
                List<FileFolderVO> children = childrenMap.get(vo.getId());
                vo.setChildren(children != null ? children : new ArrayList<>());
            }
        }

        // 根节点（parentId = null）
        List<FileFolderVO> roots = childrenMap.getOrDefault(0L, new ArrayList<>());

        // 为根节点也填充 children
        for (FileFolderVO vo : roots) {
            List<FileFolderVO> children = childrenMap.get(vo.getId());
            vo.setChildren(children != null ? children : new ArrayList<>());
        }

        return roots;
    }

    @Override
    @Transactional
    public Long create(FileFolderCreateDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        FileFolder entity = new FileFolder();
        entity.setParentId(dto.getParentId());
        entity.setFolderName(dto.getFolderName());
        entity.setSort(dto.getSort() != null ? dto.getSort() : 0);
        entity.setTenantId(tenantId);
        entity.setCreateBy(operatorId);
        entity.setDeleted(0);

        fileFolderMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, FileFolderUpdateDTO dto) {
        FileFolder folder = getByIdAndTenant(id);
        Long tenantId = folder.getTenantId();

        LambdaUpdateWrapper<FileFolder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FileFolder::getId, id);
        wrapper.eq(FileFolder::getTenantId, tenantId);
        wrapper.eq(FileFolder::getDeleted, 0);

        boolean updated = false;
        if (dto.getFolderName() != null && !dto.getFolderName().isBlank()) {
            wrapper.set(FileFolder::getFolderName, dto.getFolderName());
            updated = true;
        }
        if (dto.getParentId() != null) {
            wrapper.set(FileFolder::getParentId, dto.getParentId());
            updated = true;
        }
        if (dto.getSort() != null) {
            wrapper.set(FileFolder::getSort, dto.getSort());
            updated = true;
        }

        if (updated) {
            fileFolderMapper.update(null, wrapper);
        }
    }

    @Override
    @Transactional
    public void delete(Long id, boolean moveFilesToUnfiled) {
        // 1. 验证文件夹存在且属于当前租户
        FileFolder folder = getByIdAndTenant(id);
        Long tenantId = folder.getTenantId();

        // 2. 检查子文件夹
        Long childCount = fileFolderMapper.selectCount(
                new LambdaQueryWrapper<FileFolder>()
                        .eq(FileFolder::getParentId, id)
                        .eq(FileFolder::getTenantId, tenantId)
                        .eq(FileFolder::getDeleted, 0));
        if (childCount > 0) {
            throw new RuntimeException("请先删除子文件夹");
        }

        // 3. 检查文件夹下是否有文件
        Long fileCount = fileStorageMapper.selectCount(
                new LambdaQueryWrapper<FileStorage>()
                        .eq(FileStorage::getFolderId, id)
                        .eq(FileStorage::getTenantId, tenantId)
                        .eq(FileStorage::getStatus, 1));
        if (fileCount > 0) {
            if (!moveFilesToUnfiled) {
                throw new RuntimeException("文件夹下存在文件");
            }
            // 将文件移出文件夹（folder_id 置 null）
            LambdaUpdateWrapper<FileStorage> fileWrapper = new LambdaUpdateWrapper<>();
            fileWrapper.eq(FileStorage::getFolderId, id);
            fileWrapper.eq(FileStorage::getTenantId, tenantId);
            fileWrapper.eq(FileStorage::getStatus, 1);
            fileWrapper.set(FileStorage::getFolderId, null);
            fileStorageMapper.update(null, fileWrapper);
        }

        // 4. 软删除文件夹
        LambdaUpdateWrapper<FileFolder> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(FileFolder::getId, id);
        deleteWrapper.eq(FileFolder::getTenantId, tenantId);
        deleteWrapper.eq(FileFolder::getDeleted, 0);
        deleteWrapper.set(FileFolder::getDeleted, 1);
        fileFolderMapper.update(null, deleteWrapper);
    }

    private FileFolder getByIdAndTenant(Long id) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        LambdaQueryWrapper<FileFolder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileFolder::getId, id);
        wrapper.eq(FileFolder::getTenantId, tenantId);
        wrapper.eq(FileFolder::getDeleted, 0);
        FileFolder folder = fileFolderMapper.selectOne(wrapper);
        if (folder == null) {
            throw new RuntimeException("文件夹不存在");
        }
        return folder;
    }

    private FileFolderVO toVO(FileFolder entity) {
        FileFolderVO vo = new FileFolderVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setFolderName(entity.getFolderName());
        vo.setSort(entity.getSort());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
