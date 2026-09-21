package com.blade.outlet.migration;

import java.util.List;
import java.util.Map;

/** 历史档口回填 dry-run / apply 报告。 */
public record OutletBackfillReport(
        String mode,
        long tenantId,
        String operator,
        String startedAt,
        String finishedAt,
        String expectedDatabaseName,
        String actualDatabaseName,
        String mappingFileSha256,
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
        int ordersConcurrentSkipped,
        int draftsConcurrentSkipped,
        Map<String, Object> before,
        Map<String, Object> after,
        boolean reconciliationConsistent,
        List<MappingDecision> mappingDecisions,
        List<ValueGroup> groups,
        List<String> warnings,
        List<String> errors,
        String reportJsonPath,
        String reportMarkdownPath) {

    /** 结构化分类/样例；样例严格限定本租户，sampleRefs 为订单号或草稿 externalRefNo。 */
    public record ValueGroup(String table, String bucket, String value, long count, List<String> sampleRefs) {
    }

    /**
     * 逐条 mapping 审计轨迹：决策原文 + 该 legacy 值候选/实际更新数量，可据此重建每个映射的结果。
     */
    public record MappingDecision(String legacySourceShop, String outletCode, String decision, String reason,
                                  int ordersCandidates, int draftsCandidates,
                                  int ordersUpdated, int draftsUpdated) {
    }

    public boolean applied() {
        return "APPLY".equals(mode);
    }
}
