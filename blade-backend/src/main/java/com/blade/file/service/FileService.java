package com.blade.file.service;

import com.blade.common.result.PageResult;
import com.blade.file.dto.FilePageDTO;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.dto.FileVO;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {

    FileUploadVO upload(MultipartFile file, String businessType, Long businessId, Long operatorId);

    FileStorage getActiveFile(Long id);

    Resource loadResource(Long id);

    void delete(Long id);

    void bindFiles(String businessType, Long businessId, List<Long> fileIds);

    void bindFilesFromJson(String businessType, Long businessId, String imagesJson);

    // === BE-1002: 文件中心分页/详情 ===

    PageResult<FileVO> pageList(FilePageDTO dto);

    FileVO getDetail(Long id);

    /**
     * 查询文件的所有有效业务绑定（deleted=0）
     */
    List<FileBusinessBind> getActiveBindings(Long fileId);
}
