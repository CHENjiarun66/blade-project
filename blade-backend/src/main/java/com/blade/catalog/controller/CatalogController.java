package com.blade.catalog.controller;

import com.blade.catalog.dto.CatalogFiltersVO;
import com.blade.catalog.dto.CatalogPageDTO;
import com.blade.catalog.dto.CatalogProductVO;
import com.blade.catalog.service.CatalogService;
import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "客户展示页 Catalog API")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/products")
    @Operation(summary = "商品展示列表（分页+筛选）")
    @PreAuthorize("hasAuthority('data:catalog:view')")
    public R<PageResult<CatalogProductVO>> listProducts(CatalogPageDTO dto) {
        return R.ok(catalogService.pageList(dto));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "商品展示详情（含SKU、图片、库存状态）")
    @PreAuthorize("hasAuthority('data:catalog:view')")
    public R<CatalogProductVO> getProduct(@PathVariable Long id) {
        CatalogProductVO vo = catalogService.getById(id);
        if (vo == null) {
            return R.fail(404, "商品不存在");
        }
        return R.ok(vo);
    }

    @GetMapping("/filters")
    @Operation(summary = "筛选项（分类/颜色/尺码/库存模式）")
    @PreAuthorize("hasAuthority('data:catalog:view')")
    public R<CatalogFiltersVO> getFilters() {
        return R.ok(catalogService.getFilters());
    }
}
