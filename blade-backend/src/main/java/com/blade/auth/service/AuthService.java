package com.blade.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.auth.dto.LoginResponse;
import com.blade.common.tenant.TenantContext;
import com.blade.system.tenant.entity.Tenant;
import com.blade.system.tenant.mapper.TenantMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final long jwtExpiration;

    @Autowired
    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserDetailsService userDetailsService,
                       RedisTemplate<String, Object> redisTemplate,
                       UserMapper userMapper,
                       TenantMapper tenantMapper,
                       @Value("${jwt.expiration}") long jwtExpiration) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.jwtExpiration = jwtExpiration;
    }

    public LoginResponse login(String tenantCode, String username, String password) {
        // 1. 根据租户编码查询租户
        Tenant tenant = tenantMapper.selectOne(
            new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenantCode)
        );

        if (tenant == null) {
            throw new RuntimeException("租户不存在: " + tenantCode);
        }

        if (tenant.getStatus() != 1) {
            throw new RuntimeException("租户已被禁用");
        }

        // 2. 设置租户上下文
        TenantContext.setTenantId(tenant.getId());

        // 3. 根据租户ID和用户名查询用户
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenant.getId())
                .eq(User::getUsername, username)
        );

        if (user == null) {
            TenantContext.clear();
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if (user.getStatus() != 1) {
            TenantContext.clear();
            throw new RuntimeException("用户已被禁用");
        }

        // 4. 使用 Spring Security 验证密码
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, password)
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        redisTemplate.opsForValue().set(
            "token:" + token,
            userDetails.getUsername(),
            jwtExpiration,
            TimeUnit.MILLISECONDS
        );

        // 保存租户信息到 Redis
        redisTemplate.opsForValue().set(
            "token:tenant:" + token,
            tenant.getId(),
            jwtExpiration,
            TimeUnit.MILLISECONDS
        );

        return new LoginResponse(token, refreshToken, jwtExpiration / 1000);
    }

    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token != null) {
            redisTemplate.delete("token:" + token);
        }
    }

    public LoginResponse refreshToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (!jwtTokenProvider.validateToken(token) || jwtTokenProvider.isTokenExpired(token)) {
            throw new RuntimeException("Refresh token 无效或已过期");
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        String newToken = jwtTokenProvider.generateToken(userDetails);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        redisTemplate.opsForValue().set(
            "token:" + newToken,
            username,
            jwtExpiration,
            TimeUnit.MILLISECONDS
        );

        return new LoginResponse(newToken, newRefreshToken, jwtExpiration / 1000);
    }
}
