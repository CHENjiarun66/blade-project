package com.blade.product.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.product.dto.*;
import com.blade.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "商品管理接口")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "商品列表（分页）")
    public R<PageResult<ProductVO>> list(ProductPageDTO dto) {
        return R.ok(productService.pageList(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情")
    public R<ProductVO> getById(@PathVariable Long id) {
        return R.ok(productService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建商品")
    public R<Long> create(@RequestBody @Valid ProductCreateDTO dto) {
        return R.ok(productService.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新商品")
    public R<Void> update(@RequestBody @Valid ProductUpdateDTO dto) {
        productService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    @GetMapping("/colors")
    @Operation(summary = "获取所有颜色")
    public R<List<ProductVO.ColorVO>> listColors() {
        return R.ok(productService.listAllColors());
    }

    @PostMapping("/colors")
    @Operation(summary = "创建颜色")
    public R<Long> createColor(@RequestBody @Valid ColorCreateDTO dto) {
        return R.ok(productService.createColor(dto));
    }

    @PutMapping("/colors")
    @Operation(summary = "更新颜色")
    public R<Void> updateColor(@RequestBody @Valid ColorUpdateDTO dto) {
        productService.updateColor(dto);
        return R.ok();
    }

    @DeleteMapping("/colors/{id}")
    @Operation(summary = "删除颜色")
    public R<Void> deleteColor(@PathVariable Long id) {
        productService.deleteColor(id);
        return R.ok();
    }

    @GetMapping("/sizes")
    @Operation(summary = "获取所有尺码")
    public R<List<ProductVO.SizeVO>> listSizes() {
        return R.ok(productService.listAllSizes());
    }

    @PostMapping("/sizes")
    @Operation(summary = "创建尺码")
    public R<Long> createSize(@RequestBody @Valid SizeCreateDTO dto) {
        return R.ok(productService.createSize(dto));
    }

    @PutMapping("/sizes")
    @Operation(summary = "更新尺码")
    public R<Void> updateSize(@RequestBody @Valid SizeUpdateDTO dto) {
        productService.updateSize(dto);
        return R.ok();
    }

    @DeleteMapping("/sizes/{id}")
    @Operation(summary = "删除尺码")
    public R<Void> deleteSize(@PathVariable Long id) {
        productService.deleteSize(id);
        return R.ok();
    }

    @GetMapping("/skus")
    @Operation(summary = "获取所有SKU（下拉选择用）")
    public R<List<SkuVO>> listSkus() {
        return R.ok(productService.listAllSkus());
    }
}
