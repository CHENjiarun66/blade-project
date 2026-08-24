package com.blade.whatsapp.controller;

import com.blade.common.result.R;
import com.blade.whatsapp.auth.CollectorPrincipal;
import com.blade.whatsapp.dto.WhatsappDtos.*;
import com.blade.whatsapp.service.WhatsappService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/internal/whatsapp")
@RequiredArgsConstructor
public class WhatsappCollectorController {
    private final WhatsappService service;

    @PostMapping("/batches") public R<BatchResult> start(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestBody BatchStartRequest body){return R.ok(service.startBatch(p,body));}
    @PostMapping("/contacts:upsert") public R<ImportCounts> contacts(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestBody ImportRequest<ContactItem> body){return R.ok(service.upsertContacts(p,body));}
    @PostMapping("/conversations:upsert") public R<ImportCounts> conversations(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestBody ImportRequest<ConversationItem> body){return R.ok(service.upsertConversations(p,body));}
    @PostMapping("/messages:upsert") public R<ImportCounts> messages(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestBody ImportRequest<MessageItem> body){return R.ok(service.upsertMessages(p,body));}
    @PostMapping("/issues:upsert") public R<ImportCounts> issues(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestBody ImportRequest<IssueItem> body){return R.ok(service.upsertIssues(p,body));}
    @PostMapping("/media:upsert") public R<ImportCounts> mediaMetadata(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestBody ImportRequest<MediaItem> body){return R.ok(service.upsertMediaMetadata(p,body));}
    @PostMapping("/batches/{batchNo}:complete") public R<BatchResult> complete(@AuthenticationPrincipal CollectorPrincipal p,
            @PathVariable String batchNo,@Valid @RequestBody BatchCompleteRequest body){return R.ok(service.completeBatch(p,batchNo,body));}
    @PostMapping(value="/media",consumes="multipart/form-data") public R<MediaResult> media(@AuthenticationPrincipal CollectorPrincipal p,
            @Valid @RequestPart("metadata") MediaMetadata metadata,@RequestPart("file") MultipartFile file){return R.ok(service.importMedia(p,metadata,file));}
    @PostMapping("/scan-jobs:claim") public R<ScanJobView> claim(@AuthenticationPrincipal CollectorPrincipal p){return R.ok(service.claimScan(p));}
    @PostMapping("/scan-jobs/{id}:complete") public R<ScanJobView> finishScan(@AuthenticationPrincipal CollectorPrincipal p,
            @PathVariable Long id,@Valid @RequestBody ScanJobCompleteRequest body){return R.ok(service.completeScan(p,id,body));}
}
