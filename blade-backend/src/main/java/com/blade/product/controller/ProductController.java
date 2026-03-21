package com.blade.product.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.product.dto.ProductCreateDTO;
import com.blade.product.dto.ProductPageDTO;
import com.blade.product.dto.ProductUpdateDTO;
import com.blade.product.dto.ProductVO;
import com.blade.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "商品管理接口")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

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

    @GetMapping("/sizes")
    @Operation(summary = "获取所有尺码")
    public R<List<ProductVO.SizeVO>> listSizes() {
        return R.ok(productService.listAllSizes());
    }
}
