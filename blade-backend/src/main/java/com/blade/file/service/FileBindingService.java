package com.blade.file.service;

import com.blade.file.dto.FileBatchDeleteDTO;
import com.blade.file.dto.FileBatchMoveDTO;
import com.blade.file.dto.FileBindingCreateDTO;
import com.blade.file.dto.FileBindingVO;

import java.util.List;

/**
 * 文件绑定与批量操作服务
 */
public interface FileBindingService {

    /**
     * 查询文件的有效绑定关系
     */
    List<FileBindingVO> getBindings(Long fileId);

    /**
     * 批量绑定文件到业务对象
     */
    void createBindings(FileBindingCreateDTO dto);

    /**
     * 软删除绑定关系
     */
    void deleteBinding(Long id);

    /**
     * 批量软删除文件
     */
    void batchDelete(FileBatchDeleteDTO dto);

    /**
     * 批量移动文件到文件夹
     */
    void batchMove(FileBatchMoveDTO dto);
}
