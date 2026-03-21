package com.blade.inventory.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.inventory.dto.*;
import com.blade.inventory.service.InventoryService;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "库存管理接口")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    @Operation(summary = "库存列表（分页）")
    public R<PageResult<InventoryVO>> list(InventoryPageDTO dto) {
        return R.ok(inventoryService.pageList(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "库存详情")
    public R<InventoryVO> getById(@PathVariable Long id) {
        return R.ok(inventoryService.getById(id));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "按仓库查询库存")
    public R<List<InventoryVO>> listByWarehouse(@PathVariable Long warehouseId) {
        return R.ok(inventoryService.listByWarehouse(warehouseId));
    }

    @GetMapping("/alerts")
    @Operation(summary = "库存预警列表")
    public R<List<InventoryVO>> listAlerts(@RequestParam(required = false) Long warehouseId) {
        return R.ok(inventoryService.listAlerts(warehouseId));
    }

    @PostMapping("/in")
    @Operation(summary = "入库")
    public R<Void> in(@RequestBody @Valid InventoryInDTO dto) {
        Long operatorId = getCurrentUserId();
        inventoryService.in(dto, operatorId);
        return R.ok();
    }

    @PostMapping("/out")
    @Operation(summary = "出库")
    public R<Void> out(@RequestBody @Valid InventoryOutDTO dto) {
        Long operatorId = getCurrentUserId();
        inventoryService.out(dto, operatorId);
        return R.ok();
    }

    @PostMapping("/adjust")
    @Operation(summary = "直接调整")
    public R<Void> adjust(@RequestBody @Valid InventoryAdjustDTO dto) {
        Long operatorId = getCurrentUserId();
        inventoryService.adjust(dto, operatorId);
        return R.ok();
    }

    @PostMapping("/reserve")
    @Operation(summary = "预留锁定（订单付款时调用）")
    public R<Void> reserve(@RequestBody @Valid InventoryReserveDTO dto) {
        Long operatorId = getCurrentUserId();
        inventoryService.reserve(dto, operatorId);
        return R.ok();
    }

    @PostMapping("/release")
    @Operation(summary = "预留释放（订单取消时调用）")
    public R<Void> release(@RequestBody @Valid InventoryReserveDTO dto) {
        Long operatorId = getCurrentUserId();
        inventoryService.release(dto, operatorId);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L; // 默认管理员
    }
}
