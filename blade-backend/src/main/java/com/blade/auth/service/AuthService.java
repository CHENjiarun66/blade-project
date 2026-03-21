package com.blade.auth.service;

import com.blade.auth.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final long jwtExpiration;

    @Autowired
    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserDetailsService userDetailsService,
                       RedisTemplate<String, Object> redisTemplate,
                       @Value("${jwt.expiration}") long jwtExpiration) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.jwtExpiration = jwtExpiration;
    }

    public LoginResponse login(String username, String password) {
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
