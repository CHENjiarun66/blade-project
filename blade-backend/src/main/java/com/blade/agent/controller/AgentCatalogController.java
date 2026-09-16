package com.blade.agent.controller;

import com.blade.agent.service.AgentCatalogService;
import com.blade.common.result.R;
import com.blade.order.draft.dto.OrderDraftDTO.CatalogCandidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/catalog")
@RequiredArgsConstructor
@Tag(name = "Agent商品匹配")
public class AgentCatalogController {
    private final AgentCatalogService catalogService;

    @GetMapping("/skus")
    @PreAuthorize("hasAuthority('agent:catalog:read')")
    @Operation(summary = "查询纸单货号对应的SKU候选")
    public R<List<CatalogCandidate>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String colorName,
            @RequestParam(required = false) String sizeCode,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(catalogService.search(keyword, productCode, colorName, sizeCode, limit));
    }
}
