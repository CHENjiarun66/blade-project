package com.blade.agent.controller;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.dto.AgentCapabilitiesDTO;
import com.blade.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/agent/capabilities")
@Tag(name = "Agent能力自检")
public class AgentCapabilitiesController {

    @GetMapping
    @Operation(summary = "查询当前Agent Key的真实权限和有效期")
    public R<AgentCapabilitiesDTO.View> capabilities(
            @AuthenticationPrincipal AgentPrincipal principal) {
        Instant expiresAt = principal.getExpiresTime() == null
                ? null
                : principal.getExpiresTime().atZone(ZoneId.systemDefault()).toInstant();
        return R.ok(new AgentCapabilitiesDTO.View(
                principal.getKeyPrefix(),
                principal.getDisplayName(),
                principal.getScopes(),
                expiresAt,
                Instant.now()));
    }
}
