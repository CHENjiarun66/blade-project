package com.blade.whatsapp.controller;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.common.result.R;
import com.blade.whatsapp.dto.WhatsappAnalysisDtos.*;
import com.blade.whatsapp.service.WhatsappAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/whatsapp")
@RequiredArgsConstructor
public class WhatsappAgentController {
    private final WhatsappAnalysisService service;

    @PostMapping("/analysis-jobs:claim")
    @PreAuthorize("hasAuthority('agent:whatsapp:analyze')")
    public R<AnalysisJobContext> claim(@AuthenticationPrincipal AgentPrincipal principal) {
        return R.ok(service.claim(principal));
    }

    @PostMapping("/analysis-jobs/{id}:complete")
    @PreAuthorize("hasAuthority('agent:whatsapp:analyze')")
    public R<AnalysisResult> complete(@AuthenticationPrincipal AgentPrincipal principal,
                                     @PathVariable Long id,
                                     @Valid @RequestBody AnalysisCompleteRequest request) {
        return R.ok(service.complete(principal,id,request));
    }

    @PostMapping("/analysis-jobs/{id}:fail")
    @PreAuthorize("hasAuthority('agent:whatsapp:analyze')")
    public R<Void> fail(@AuthenticationPrincipal AgentPrincipal principal,
                        @PathVariable Long id,
                        @Valid @RequestBody AnalysisFailRequest request) {
        service.fail(principal,id,request); return R.ok();
    }
}
