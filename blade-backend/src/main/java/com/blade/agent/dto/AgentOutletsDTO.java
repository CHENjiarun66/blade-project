package com.blade.agent.dto;

import java.util.List;

public final class AgentOutletsDTO {
    private AgentOutletsDTO() {
    }

    /** 仅暴露稳定 code/name 与默认标记；不暴露内部 outlet id。 */
    public record OutletItem(String code, String name, boolean defaultOutlet) {
    }

    public record View(String outletScopeType, String defaultOutletCode, List<OutletItem> items) {
    }
}
