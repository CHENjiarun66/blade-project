package com.blade.config;

import com.blade.auth.service.JwtTokenProvider;
import com.blade.agent.auth.AgentAuthenticationFilter;
import com.blade.common.tenant.TenantContext;
import com.blade.whatsapp.auth.CollectorAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          AgentAuthenticationFilter agentAuthFilter,
                                          CollectorAuthenticationFilter collectorAuthFilter,
                                          JwtAuthenticationFilter jwtAuthFilter,
                                          AuthenticationProvider authenticationProvider) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/logout",
                    "/api/auth/refresh",
                    "/api/auth/register",
                    "/api/user/info",
                    "/api/auth/codes",
                    "/api/files/*/preview",
                    "/api/files/*/variant",
                    "/oauth2/**",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(collectorAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(agentAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Configuration
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtTokenProvider jwtTokenProvider;
        private final UserDetailsService userDetailsService;
        private final RedisTemplate<String, Object> redisTemplate;

        @Autowired
        public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                       UserDetailsService userDetailsService,
                                       RedisTemplate<String, Object> redisTemplate) {
            this.jwtTokenProvider = jwtTokenProvider;
            this.userDetailsService = userDetailsService;
            this.redisTemplate = redisTemplate;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            return uri != null && (uri.startsWith("/api/agent/") || uri.startsWith("/api/internal/whatsapp/"));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            try {
                String token = extractToken(request);
                if (token != null && jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    Long tenantId = resolveActiveTenant(token, username);
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        }

        private Long resolveActiveTenant(String token, String username) {
            Object activeUsername = redisTemplate.opsForValue().get("token:" + token);
            if (activeUsername == null || !username.equals(String.valueOf(activeUsername))) {
                return null;
            }
            Long claimTenant = jwtTokenProvider.getTenantIdFromToken(token);
            Long redisTenant = toLong(redisTemplate.opsForValue().get("token:tenant:" + token));
            if (claimTenant != null && redisTenant != null && !claimTenant.equals(redisTenant)) {
                return null;
            }
            return claimTenant != null ? claimTenant : redisTenant;
        }

        private Long toLong(Object value) {
            if (value instanceof Number number) return number.longValue();
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.valueOf(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        private String extractToken(HttpServletRequest request) {
            String bearerToken = request.getHeader("Authorization");
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
            if (isFilePreviewRequest(request)) {
                String previewToken = request.getParameter("previewToken");
                if (previewToken != null && !previewToken.isBlank()) {
                    return previewToken;
                }
            }
            return null;
        }

        private boolean isFilePreviewRequest(HttpServletRequest request) {
            String uri = request.getRequestURI();
            return uri != null && (
                uri.matches("^/api/files/\\d+/preview$") ||
                uri.matches("^/api/files/\\d+/variant$")
            );
        }
    }
}
