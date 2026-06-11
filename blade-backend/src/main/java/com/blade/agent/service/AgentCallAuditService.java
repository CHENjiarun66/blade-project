package com.blade.agent.service;

import com.blade.agent.auth.AgentCallAuditEvent;
import com.blade.agent.auth.AgentCallAuditRecorder;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentCallLog;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentCallLogMapper;
import com.blade.agent.mapper.AgentKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentCallAuditService implements AgentCallAuditRecorder {

    private final AgentCallLogMapper callLogMapper;
    private final AgentKeyMapper agentKeyMapper;

    @Override
    public void record(AgentPrincipal principal, AgentCallAuditEvent event) {
        AgentCallLog log = new AgentCallLog();
        log.setTenantId(principal.getTenantId());
        log.setAgentKeyId(principal.getKeyId());
        log.setKeyPrefix(principal.getKeyPrefix());
        log.setMethod(event.getMethod());
        log.setPath(event.getPath());
        log.setQueryString(event.getQueryString());
        log.setStatus(event.getStatus());
        log.setDurationMs(event.getDurationMs());
        log.setIp(event.getIp());
        log.setUserAgent(event.getUserAgent());
        callLogMapper.insert(log);

        AgentKey key = new AgentKey();
        key.setId(principal.getKeyId());
        key.setLastUsedTime(LocalDateTime.now());
        key.setLastUsedIp(event.getIp());
        agentKeyMapper.updateById(key);
    }
}
