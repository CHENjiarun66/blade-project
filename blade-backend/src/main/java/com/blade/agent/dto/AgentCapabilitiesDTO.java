package com.blade.agent.dto;

import java.time.Instant;
import java.util.List;

public final class AgentCapabilitiesDTO {
    private AgentCapabilitiesDTO() {
    }

    public record View(
            String keyPrefix,
            String name,
            List<String> scopes,
            Instant expiresAt,
            Instant serverTime) {
    }
}
