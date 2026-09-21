package com.blade.agent.controller;

import com.blade.agent.dto.AgentOrderDTO;
import com.blade.agent.service.AgentOrderQueryService;
import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.order.dto.OrderPageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/orders")
@RequiredArgsConstructor
@Tag(name = "Agent订单查询")
public class AgentOrderQueryController {
    private final AgentOrderQueryService orderService;

    @GetMapping
    @PreAuthorize("hasAuthority('agent:orders:read')")
    @Operation(summary = "分页读取脱敏正式订单；档口筛选使用稳定 sourceOutletCode")
    public R<PageResult<AgentOrderDTO.OrderView>> list(
            @Valid @ModelAttribute OrderPageDTO query,
            @RequestParam(value = "sourceOutletCode", required = false) String sourceOutletCode) {
        return R.ok(orderService.page(query, sourceOutletCode));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:orders:read')")
    @Operation(summary = "读取脱敏正式订单详情")
    public R<AgentOrderDTO.OrderView> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(id));
    }
}
