package com.blade.outlet.migration;

import java.util.List;
import java.util.Map;

/** 历史档口回填 dry-run / apply 报告。 */
public record OutletBackfillReport(
        String mode,
        long tenantId,
        int mappingRows,
        int mapRows,
        int skipRows,
        int reviewRows,
        int ordersCandidates,
        int draftsCandidates,
        int ordersUpdated,
        int draftsUpdated,
        int ordersAlreadyApplied,
        int draftsAlreadyApplied,
        int ordersConflict,
        int draftsConflict,
        int ordersSuspect,
        int draftsSuspect,
        int confirmedDraftsSkipped,
        Map<String, Object> before,
        Map<String, Object> after,
        boolean reconciliationConsistent,
        List<String> warnings,
        List<String> errors) {

    public boolean applied() {
        return "APPLY".equals(mode);
    }
}
