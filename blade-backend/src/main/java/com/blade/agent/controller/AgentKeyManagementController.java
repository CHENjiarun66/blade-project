package com.blade.agent.controller;

import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.service.AgentKeyManagementService;
import com.blade.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/agent-keys")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('agent-key:manage')")
@Tag(name = "Agent Key管理")
public class AgentKeyManagementController {
    private final AgentKeyManagementService service;

    @GetMapping
    @Operation(summary = "查询当前租户的Agent Key")
    public R<List<AgentKeyManagementDTO.View>> list() {
        return R.ok(service.list());
    }

    @GetMapping("/scopes")
    @Operation(summary = "查询允许签发的Agent scope")
    public R<List<String>> scopes() {
        return R.ok(service.allowedScopes());
    }

    @PostMapping
    @Operation(summary = "签发Agent Key，明文只返回一次")
    public R<AgentKeyManagementDTO.Credential> create(
            @RequestBody @Valid AgentKeyManagementDTO.CreateRequest request) {
        return R.ok(service.create(request));
    }

    @PostMapping("/{id}/rotate")
    @Operation(summary = "轮换Agent Key并立即停用旧Key")
    public R<AgentKeyManagementDTO.Credential> rotate(
            @PathVariable Long id,
            @RequestBody @Valid AgentKeyManagementDTO.RotateRequest request) {
        return R.ok(service.rotate(id, request));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用Agent Key")
    public R<Void> disable(@PathVariable Long id) {
        service.disable(id);
        return R.ok();
    }
}
