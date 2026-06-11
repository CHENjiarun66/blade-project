package com.blade.agent.controller;

import com.blade.agent.dto.AgentSkuMixDTO;
import com.blade.agent.dto.AgentStyleTrendDTO;
import com.blade.agent.service.AgentSkuMixService;
import com.blade.agent.service.AgentStyleTrendService;
import com.blade.common.result.R;
import com.blade.dashboard.dto.DashboardQueryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/analytics")
@RequiredArgsConstructor
@Tag(name = "Agent数据分析")
public class AgentAnalyticsController {

    private final AgentStyleTrendService agentStyleTrendService;
    private final AgentSkuMixService agentSkuMixService;

    @GetMapping("/style-trends")
    @PreAuthorize("hasAuthority('agent:analytics:read')")
    @Operation(summary = "获取Agent款式趋势事实包")
    public R<AgentStyleTrendDTO> getStyleTrends(@ModelAttribute DashboardQueryDTO query,
                                                @RequestParam(defaultValue = "20") Integer limit,
                                                @RequestParam(defaultValue = "3") Integer comparePeriods) {
        return R.ok(agentStyleTrendService.getStyleTrends(query, limit, comparePeriods));
    }

    @GetMapping("/sku-mix")
    @PreAuthorize("hasAuthority('agent:analytics:read')")
    @Operation(summary = "获取Agent颜色尺码结构事实包")
    public R<AgentSkuMixDTO> getSkuMix(@ModelAttribute DashboardQueryDTO query,
                                       @RequestParam String productName,
                                       @RequestParam(defaultValue = "20") Integer limit) {
        return R.ok(agentSkuMixService.getSkuMix(query, productName, limit));
    }
}
