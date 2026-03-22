package com.blade.auth.controller;

import com.blade.auth.dto.UserInfoVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.entity.User;
import com.blade.system.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * VbenAdmin 适配接口
 * 提供 vben-admin 前端需要的标准接口
 */
@RestController
@Tag(name = "VbenAdmin适配接口")
public class VbenAdminController {

    @Autowired
    private UserService userService;

    @GetMapping("/api/user/info")
    @Operation(summary = "获取当前用户信息")
    public Object getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 如果未认证或匿名用户，返回空对象让前端处理
        if (authentication == null || !authentication.isAuthenticated() ||
            "anonymousUser".equals(authentication.getPrincipal())) {
            return Collections.singletonMap("needsLogin", true);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();

        User user = userService.getByUsername(username);
        if (user == null) {
            return Collections.singletonMap("needsLogin", true);
        }

        List<Role> roles = userService.getRolesByUserId(user.getId());

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getNickname() != null ? user.getNickname() : user.getUsername());
        vo.setAvatar(user.getAvatar() != null ? user.getAvatar() : "");
        vo.setDesc("管理员");
        vo.setHomePath("/dashboard/analytics");
        vo.setRoles(roles != null ? roles.stream().map(Role::getRoleCode).collect(Collectors.toList()) : Collections.emptyList());

        return vo;
    }

    @GetMapping("/api/auth/codes")
    @Operation(summary = "获取权限码列表")
    public Object getAuthCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 如果未认证，返回空列表
        if (authentication == null || !authentication.isAuthenticated() ||
            "anonymousUser".equals(authentication.getPrincipal())) {
            return Collections.emptyList();
        }

        return authentication.getAuthorities().stream()
            .map(auth -> auth.getAuthority().replace("ROLE_", ""))
            .collect(Collectors.toList());
    }
}