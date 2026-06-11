package com.blade.auth.controller;

import com.blade.auth.dto.LoginRequest;
import com.blade.auth.dto.LoginResponse;
import com.blade.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证接口")
public class LoginController {

    private final AuthService authService;

    @Autowired
    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "账号密码登录")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(
                request.getTenantCode(),
                request.getUsername(),
                request.getPassword(),
                Boolean.TRUE.equals(request.getRemember())
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public void logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token")
    public LoginResponse refresh(@RequestHeader("Authorization") String token) {
        return authService.refreshToken(token);
    }
}
