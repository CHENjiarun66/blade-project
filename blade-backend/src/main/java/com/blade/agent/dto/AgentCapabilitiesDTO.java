package com.blade.agent.dto;

import java.time.Instant;
import java.util.List;

public final class AgentCapabilitiesDTO {
    private AgentCapabilitiesDTO() {
    }

    /** 档口摘要：只暴露稳定 code/name/status，不暴露内部 ID 等无必要字段。 */
    public record OutletBrief(String code, String name, Integer status) {
    }

    public record View(
            String keyPrefix,
            String name,
            List<String> scopes,
            Instant expiresAt,
            Instant serverTime,
            String outletScopeType,
            String defaultOutletCode,
            List<OutletBrief> readableOutlets,
            List<OutletBrief> usableOutlets) {
    }
}
