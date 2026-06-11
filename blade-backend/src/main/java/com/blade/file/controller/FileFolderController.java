package com.blade.file.controller;

import com.blade.common.result.R;
import com.blade.file.dto.FileFolderCreateDTO;
import com.blade.file.dto.FileFolderUpdateDTO;
import com.blade.file.dto.FileFolderVO;
import com.blade.file.service.FileFolderService;
import com.blade.system.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/file-folders")
public class FileFolderController {

    private final FileFolderService fileFolderService;

    public FileFolderController(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    @GetMapping("/tree")
    public R<List<FileFolderVO>> getTree() {
        return R.ok(fileFolderService.getTree());
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody FileFolderCreateDTO dto) {
        return R.ok(fileFolderService.create(dto, getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody FileFolderUpdateDTO dto) {
        fileFolderService.update(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestParam(value = "moveFilesToUnfiled", defaultValue = "false") boolean moveFilesToUnfiled) {
        fileFolderService.delete(id, moveFilesToUnfiled);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L;
    }
}
