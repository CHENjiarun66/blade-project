package com.blade.agent.controller;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.dto.AgentProductDTO;
import com.blade.agent.service.AgentProductService;
import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.product.dto.ProductPageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/products")
@RequiredArgsConstructor
@Tag(name = "Agent商品主档")
public class AgentProductController {
    private final AgentProductService productService;

    @GetMapping
    @PreAuthorize("hasAuthority('agent:products:read')")
    @Operation(summary = "分页读取脱敏商品主档")
    public R<PageResult<AgentProductDTO.ProductView>> list(@Valid @ModelAttribute ProductPageDTO query) {
        return R.ok(productService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:products:read')")
    @Operation(summary = "读取脱敏商品详情")
    public R<AgentProductDTO.ProductView> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('agent:products:read')")
    @Operation(summary = "读取商品新增所需分类、颜色和尺码选项")
    public R<AgentProductDTO.OptionsView> options() {
        return R.ok(productService.options());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:products:create')")
    @Operation(summary = "新增商品；成本价需独立授权，不修改同编码商品，不产生库存")
    public R<AgentProductDTO.CreateResult> create(
            @Valid @RequestBody AgentProductDTO.CreateRequest request,
            @AuthenticationPrincipal AgentPrincipal principal) {
        if (request.costPrice() != null && !principal.getScopes().contains("products:cost:write")) {
            throw new AccessDeniedException("缺少 products:cost:write");
        }
        return R.ok(productService.create(request));
    }
}
