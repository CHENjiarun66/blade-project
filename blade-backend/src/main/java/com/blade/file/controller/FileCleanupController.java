package com.blade.file.controller;

import com.blade.common.result.R;
import com.blade.file.service.FileCleanupService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件清理控制器 (BE-1007/BE-1008)
 * 提供未绑定文件查询、软删除和清理标记接口。
 */
@RestController
@RequestMapping("/api/files/cleanup")
public class FileCleanupController {

    private final FileCleanupService fileCleanupService;

    public FileCleanupController(FileCleanupService fileCleanupService) {
        this.fileCleanupService = fileCleanupService;
    }

    /**
     * 查询未绑定、未归档、超过保留期的候选文件数。
     */
    @GetMapping("/unbound-candidates")
    public R<Map<String, Object>> getUnboundCandidates(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        long count = fileCleanupService.countUnboundCandidates(days);
        Map<String, Object> result = new HashMap<>();
        result.put("candidateCount", count);
        result.put("retentionDays", days);
        return R.ok(result);
    }

    /**
     * 软删除未绑定、未归档、超过保留期的文件。
     */
    @PostMapping("/soft-delete-unbound")
    public R<Map<String, Object>> softDeleteUnbound(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        long processed = fileCleanupService.softDeleteUnbound(days);
        Map<String, Object> result = new HashMap<>();
        result.put("processedCount", processed);
        result.put("retentionDays", days);
        return R.ok(result);
    }

    /**
     * 标记软删除超过保留期且符合条件的文件为已清理（仅元数据，不物理删除）。
     */
    @PostMapping("/mark-purged")
    public R<Map<String, Object>> markPurged(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        long processed = fileCleanupService.markPurged(days);
        Map<String, Object> result = new HashMap<>();
        result.put("processedCount", processed);
        result.put("retentionDays", days);
        return R.ok(result);
    }
}
