package com.blade.file.service;

import com.blade.file.dto.FileFolderCreateDTO;
import com.blade.file.dto.FileFolderUpdateDTO;
import com.blade.file.dto.FileFolderVO;

import java.util.List;

public interface FileFolderService {

    /**
     * 获取当前租户的文件夹树
     */
    List<FileFolderVO> getTree();

    /**
     * 创建文件夹
     */
    Long create(FileFolderCreateDTO dto, Long operatorId);

    /**
     * 更新文件夹（名称、parentId、sort）
     */
    void update(Long id, FileFolderUpdateDTO dto);

    /**
     * 删除文件夹
     * @param moveFilesToUnfiled true=将文件夹下文件移出再删除, false=有文件时报错
     */
    void delete(Long id, boolean moveFilesToUnfiled);
}
