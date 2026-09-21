package com.blade.outlet.migration;

/** 一条显式历史档口映射决策。 */
public record OutletBackfillMappingRow(
        long tenantId,
        String legacySourceShop,
        String outletCode,
        String decision,
        String reason) {

    public boolean isMap() {
        return OutletBackfillMapping.DECISION_MAP.equals(decision);
    }
}
