package com.blade.file.controller;

import com.blade.common.result.R;
import com.blade.file.dto.FileBatchDeleteDTO;
import com.blade.file.dto.FileBatchMoveDTO;
import com.blade.file.dto.FileBindingCreateDTO;
import com.blade.file.dto.FileBindingVO;
import com.blade.file.service.FileBindingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件绑定和批量操作控制器
 * 已映射部分与 FileController 不冲突的 /api/files 子路径
 */
@RestController
@RequestMapping("/api/files")
public class FileBindingController {

    private final FileBindingService fileBindingService;

    public FileBindingController(FileBindingService fileBindingService) {
        this.fileBindingService = fileBindingService;
    }

    // ==================== BE-1004: 绑定操作 ====================

    @GetMapping("/{id}/bindings")
    public R<List<FileBindingVO>> getBindings(@PathVariable Long id) {
        return R.ok(fileBindingService.getBindings(id));
    }

    @PostMapping("/bindings")
    public R<Void> createBindings(@Valid @RequestBody FileBindingCreateDTO dto) {
        fileBindingService.createBindings(dto);
        return R.ok();
    }

    @DeleteMapping("/bindings/{id}")
    public R<Void> deleteBinding(@PathVariable Long id) {
        fileBindingService.deleteBinding(id);
        return R.ok();
    }

    // ==================== BE-1006: 批量操作 ====================

    @PostMapping("/batch-delete")
    public R<Void> batchDelete(@Valid @RequestBody FileBatchDeleteDTO dto) {
        fileBindingService.batchDelete(dto);
        return R.ok();
    }

    @PostMapping("/batch-move")
    public R<Void> batchMove(@Valid @RequestBody FileBatchMoveDTO dto) {
        fileBindingService.batchMove(dto);
        return R.ok();
    }
}
