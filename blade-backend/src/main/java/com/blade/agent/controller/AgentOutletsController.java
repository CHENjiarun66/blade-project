package com.blade.agent.controller;

import com.blade.agent.dto.AgentOutletsDTO;
import com.blade.common.result.R;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 可用档口选项。
 *
 * <p>需要业务 scope {@code outlets:read}（authority {@code agent:outlets:read}）。
 * 只返回当前 Key 可用于新建的启用档口；NONE 返回空列表，绝不返回全租户。
 * 缺少该 scope 时由安全层返回 403（服务器拒绝权限），与 Key 无效的 401 区分。</p>
 */
@RestController
@RequestMapping("/api/agent/outlets")
@RequiredArgsConstructor
@Tag(name = "Agent档口选项")
public class AgentOutletsController {

    private final OutletAccessPolicy outletAccessPolicy;

    @GetMapping
    @PreAuthorize("hasAuthority('agent:outlets:read')")
    @Operation(summary = "读取当前Key可用于新建的启用档口")
    public R<AgentOutletsDTO.View> outlets() {
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        List<SalesOutlet> usableOutlets = outletAccessPolicy.loadOutlets(scope.usableOutletIds(), true);
        String defaultOutletCode = scope.defaultOutletId() == null
                ? null
                : usableOutlets.stream()
                        .filter(outlet -> scope.defaultOutletId().equals(outlet.getId()))
                        .map(SalesOutlet::getOutletCode)
                        .findFirst()
                        .orElse(null);
        List<AgentOutletsDTO.OutletItem> items = usableOutlets.stream()
                .map(outlet -> new AgentOutletsDTO.OutletItem(
                        outlet.getOutletCode(), outlet.getOutletName(),
                        outlet.getId().equals(scope.defaultOutletId())))
                .toList();
        return R.ok(new AgentOutletsDTO.View(scope.outletScopeType(), defaultOutletCode, items));
    }
}
