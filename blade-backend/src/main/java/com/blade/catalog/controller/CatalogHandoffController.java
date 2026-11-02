package com.blade.catalog.controller;

import com.blade.catalog.dto.CatalogHandoffDTO;
import com.blade.catalog.service.CatalogHandoffService;
import com.blade.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/handoffs")
@RequiredArgsConstructor
public class CatalogHandoffController {
    private final CatalogHandoffService service;

    @PostMapping
    @Operation(summary = "移交批发选款到订单草稿，等待店员复核")
    @PreAuthorize("hasAuthority('data:catalog:view') and hasAuthority('btn:order:create')")
    public R<CatalogHandoffDTO.Response> handoff(@RequestBody @Valid CatalogHandoffDTO.Request request) {
        return R.ok(service.handoff(request));
    }
}
