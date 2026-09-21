package com.blade.agent.controller;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.dto.AgentCapabilitiesDTO;
import com.blade.common.result.R;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Agent 能力自检。
 *
 * <p>返回服务器实时事实：每次请求都通过 {@link OutletAccessPolicy#resolveCurrentScope()}
 * 重新读取 agent_key 状态与 agent_key_outlet 绑定，因此轮换/停用/档口禁用会在下一请求立即反映，
 * 不依赖 principal 构造时的旧快照。Key 原文永不返回。</p>
 */
@RestController
@RequestMapping("/api/agent/capabilities")
@RequiredArgsConstructor
@Tag(name = "Agent能力自检")
public class AgentCapabilitiesController {

    private final OutletAccessPolicy outletAccessPolicy;

    @GetMapping
    @Operation(summary = "查询当前Agent Key的真实权限和有效期")
    public R<AgentCapabilitiesDTO.View> capabilities(
            @AuthenticationPrincipal AgentPrincipal principal) {
        Instant expiresAt = principal.getExpiresTime() == null
                ? null
                : principal.getExpiresTime().atZone(ZoneId.systemDefault()).toInstant();

        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        List<SalesOutlet> readableOutlets = outletAccessPolicy.loadOutlets(scope.readableOutletIds(), false);
        List<SalesOutlet> usableOutlets = outletAccessPolicy.loadOutlets(scope.usableOutletIds(), true);
        String defaultOutletCode = scope.defaultOutletId() == null
                ? null
                : readableOutlets.stream()
                        .filter(outlet -> scope.defaultOutletId().equals(outlet.getId()))
                        .map(SalesOutlet::getOutletCode)
                        .findFirst()
                        .orElse(null);

        return R.ok(new AgentCapabilitiesDTO.View(
                principal.getKeyPrefix(),
                principal.getDisplayName(),
                principal.getScopes(),
                expiresAt,
                Instant.now(),
                scope.outletScopeType(),
                defaultOutletCode,
                readableOutlets.stream().map(this::toBrief).toList(),
                usableOutlets.stream().map(this::toBrief).toList()));
    }

    private AgentCapabilitiesDTO.OutletBrief toBrief(SalesOutlet outlet) {
        return new AgentCapabilitiesDTO.OutletBrief(
                outlet.getOutletCode(), outlet.getOutletName(), outlet.getStatus());
    }
}
