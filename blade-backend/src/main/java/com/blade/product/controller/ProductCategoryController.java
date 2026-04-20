package com.blade.product.controller;

import com.blade.common.result.R;
import com.blade.product.dto.CategoryCreateDTO;
import com.blade.product.dto.CategoryUpdateDTO;
import com.blade.product.dto.ProductCategoryVO;
import com.blade.product.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/product-categories")
@RequiredArgsConstructor
@Tag(name = "商品分类管理")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @GetMapping
    @Operation(summary = "获取所有分类")
    public R<List<ProductCategoryVO>> listAll() {
        return R.ok(categoryService.listAll());
    }

    @PostMapping
    @Operation(summary = "创建分类")
    public R<Long> create(@RequestBody @Valid CategoryCreateDTO dto) {
        return R.ok(categoryService.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新分类")
    public R<Void> update(@RequestBody @Valid CategoryUpdateDTO dto) {
        categoryService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
