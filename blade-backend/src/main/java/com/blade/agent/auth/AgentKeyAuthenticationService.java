package com.blade.agent.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentKeyAuthenticationService {

    private final AgentKeyMapper agentKeyMapper;
    private final PasswordEncoder passwordEncoder;

    public AgentPrincipal authenticate(String rawKey) {
        ParsedAgentKey parsedKey = ParsedAgentKey.from(rawKey);
        AgentKey key = agentKeyMapper.selectOne(new LambdaQueryWrapper<AgentKey>()
                .eq(AgentKey::getKeyPrefix, parsedKey.prefix())
                .eq(AgentKey::getStatus, AgentKey.STATUS_ACTIVE));
        if (key == null
                || isExpired(key)
                || !passwordEncoder.matches(parsedKey.secret(), key.getKeyHash())) {
            TenantContext.clear();
            throw new BadCredentialsException("Invalid agent key");
        }
        TenantContext.setTenantId(key.getTenantId());
        return AgentPrincipal.from(key);
    }

    private boolean isExpired(AgentKey key) {
        return key.getExpiresTime() != null && key.getExpiresTime().isBefore(LocalDateTime.now());
    }

    private record ParsedAgentKey(String prefix, String secret) {
        private static ParsedAgentKey from(String rawKey) {
            if (rawKey == null) {
                throw new BadCredentialsException("Missing agent key");
            }
            int separator = rawKey.indexOf('.');
            if (separator <= 0 || separator == rawKey.length() - 1) {
                throw new BadCredentialsException("Invalid agent key");
            }
            return new ParsedAgentKey(rawKey.substring(0, separator), rawKey.substring(separator + 1));
        }
    }
}
