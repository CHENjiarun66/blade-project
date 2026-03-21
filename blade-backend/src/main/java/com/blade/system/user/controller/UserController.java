package com.blade.system.user.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.dto.UserPageDTO;
import com.blade.system.user.dto.UserUpdateDTO;
import com.blade.system.user.dto.UserVO;
import com.blade.system.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@Tag(name = "用户管理接口")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "用户列表（分页）")
    public R<PageResult<UserVO>> list(@Valid UserPageDTO dto) {
        return R.ok(userService.pageList(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public R<UserVO> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    @PreAuthorize("hasAuthority('user:create')")
    public R<Long> create(@RequestBody @Valid UserCreateDTO dto) {
        return R.ok(userService.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新用户")
    @PreAuthorize("hasAuthority('user:update')")
    public R<Void> update(@RequestBody @Valid UserUpdateDTO dto) {
        userService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('user:delete')")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "重置密码")
    @PreAuthorize("hasAuthority('user:password:reset')")
    public R<Void> resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        userService.resetPassword(id, newPassword);
        return R.ok();
    }
}
