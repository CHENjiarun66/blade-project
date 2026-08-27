package com.blade.agent.controller;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.common.result.R;
import com.blade.file.dto.FileUploadVO;
import com.blade.file.service.FileService;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.service.AgentOrderDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/agent/order-drafts")
@RequiredArgsConstructor
@Tag(name = "Agent订单草稿")
public class AgentOrderDraftController {
    private final AgentOrderDraftService draftService;
    private final FileService fileService;

    @PostMapping("/source-files")
    @PreAuthorize("hasAuthority('agent:orders:write')")
    @Operation(summary = "上传纸质订单原图")
    public R<FileUploadVO> uploadSource(@RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal AgentPrincipal principal) {
        return R.ok(fileService.upload(file, "order_draft", null, principal.getKeyId()));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('agent:orders:write')")
    @Operation(summary = "批量创建可编辑订单草稿")
    public R<OrderDraftDTO.BatchResponse> createBatch(
            @RequestBody @Valid OrderDraftDTO.BatchRequest request,
            @AuthenticationPrincipal AgentPrincipal principal) {
        return R.ok(draftService.createBatch(request, principal));
    }
}
