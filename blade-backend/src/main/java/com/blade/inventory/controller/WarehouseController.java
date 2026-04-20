package com.blade.inventory.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.inventory.dto.WarehouseCreateDTO;
import com.blade.inventory.dto.WarehouseUpdateDTO;
import com.blade.inventory.dto.WarehouseVO;
import com.blade.inventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse")
@Tag(name = "仓库管理接口")
public class WarehouseController {

    @Autowired
    private WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "仓库列表（分页）")
    public R<PageResult<WarehouseVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(warehouseService.pageList(current, size));
    }

    @GetMapping("/all")
    @Operation(summary = "所有仓库列表")
    public R<List<WarehouseVO>> listAll() {
        return R.ok(warehouseService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "仓库详情")
    public R<WarehouseVO> getById(@PathVariable Long id) {
        return R.ok(warehouseService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建仓库")
    public R<Long> create(@RequestBody @Valid WarehouseCreateDTO dto) {
        return R.ok(warehouseService.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新仓库")
    public R<Void> update(@RequestBody @Valid WarehouseUpdateDTO dto) {
        warehouseService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除仓库")
    public R<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return R.ok();
    }
}
