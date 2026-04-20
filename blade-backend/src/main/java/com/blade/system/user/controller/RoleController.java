package com.blade.system.user.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.system.user.dto.RoleCreateDTO;
import com.blade.system.user.dto.RoleUpdateDTO;
import com.blade.system.user.dto.RoleVO;
import com.blade.system.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@Tag(name = "角色管理")
public class RoleController {

    private final RoleService roleService;

    @Autowired
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "角色列表（分页）")
    public R<PageResult<RoleVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(roleService.pageList(current, size, keyword));
    }

    @GetMapping("/all")
    @Operation(summary = "所有角色（下拉框用）")
    public R<List<RoleVO>> getAll() {
        return R.ok(roleService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "角色详情")
    public R<RoleVO> getById(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建角色")
    public R<Long> create(@RequestBody @Valid RoleCreateDTO dto) {
        return R.ok(roleService.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新角色")
    public R<Void> update(@RequestBody @Valid RoleUpdateDTO dto) {
        roleService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "获取角色权限ID列表")
    public R<List<Long>> getPermissionIds(@PathVariable Long id) {
        return R.ok(roleService.getPermissionIdsByRoleId(id));
    }
}
