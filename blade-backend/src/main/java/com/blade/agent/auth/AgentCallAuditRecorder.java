package com.blade.agent.auth;

public interface AgentCallAuditRecorder {
    void record(AgentPrincipal principal, AgentCallAuditEvent event);
}
