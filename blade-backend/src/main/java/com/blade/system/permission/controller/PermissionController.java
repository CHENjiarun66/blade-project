package com.blade.system.permission.controller;

import com.blade.common.result.R;
import com.blade.system.permission.dto.PermissionCreateDTO;
import com.blade.system.permission.dto.PermissionUpdateDTO;
import com.blade.system.permission.dto.PermissionVO;
import com.blade.system.permission.dto.RolePermissionDTO;
import com.blade.system.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "权限管理")
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @Autowired
    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Operation(summary = "权限分页列表")
    @GetMapping
    public R<com.blade.common.result.PageResult<PermissionVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String module) {
        return R.ok(permissionService.pageList(current, size, type, module));
    }

    @Operation(summary = "获取权限详情")
    @GetMapping("/{id}")
    public R<PermissionVO> getById(@PathVariable Long id) {
        return R.ok(permissionService.getById(id));
    }

    @Operation(summary = "获取所有权限（树形结构）")
    @GetMapping("/tree")
    public R<List<PermissionVO>> getTree() {
        return R.ok(permissionService.getAllTree());
    }

    @Operation(summary = "创建权限")
    @PostMapping
    public R<Long> create(@RequestBody @Valid PermissionCreateDTO dto) {
        return R.ok(permissionService.create(dto));
    }

    @Operation(summary = "更新权限")
    @PutMapping
    public R<Void> update(@RequestBody @Valid PermissionUpdateDTO dto) {
        permissionService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return R.ok();
    }

    @Operation(summary = "获取角色已分配的权限ID列表")
    @GetMapping("/role/{roleId}")
    public R<List<Long>> getPermissionIdsByRoleId(@PathVariable Long roleId) {
        return R.ok(permissionService.getPermissionIdsByRoleId(roleId));
    }

    @Operation(summary = "分配角色权限")
    @PostMapping("/role")
    public R<Void> assignPermissions(@RequestBody @Valid RolePermissionDTO dto) {
        permissionService.assignPermissions(dto);
        return R.ok();
    }
}
