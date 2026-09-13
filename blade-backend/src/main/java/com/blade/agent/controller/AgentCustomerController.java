package com.blade.agent.controller;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.dto.AgentCustomerDTO;
import com.blade.agent.service.AgentCustomerService;
import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/customers")
@RequiredArgsConstructor
@Tag(name = "Agent客户资料")
public class AgentCustomerController {
    private final AgentCustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('agent:customers:read')")
    @Operation(summary = "分页读取客户资料；包含电话、地址和备注")
    public R<PageResult<AgentCustomerDTO.CustomerView>> list(
            @Valid @ModelAttribute AgentCustomerDTO.PageRequest request) {
        return R.ok(customerService.page(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:customers:read')")
    @Operation(summary = "读取客户详情；包含电话、地址和备注")
    public R<AgentCustomerDTO.CustomerView> detail(@PathVariable Long id) {
        return R.ok(customerService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:customers:create')")
    @Operation(summary = "新增客户；重复电话只返回已有客户，不修改或删除")
    public R<AgentCustomerDTO.CreateResult> create(
            @Valid @RequestBody AgentCustomerDTO.CreateRequest request,
            @AuthenticationPrincipal AgentPrincipal principal) {
        return R.ok(customerService.create(request, principal));
    }
}
