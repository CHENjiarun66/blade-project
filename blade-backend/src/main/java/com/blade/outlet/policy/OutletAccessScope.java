package com.blade.outlet.policy;

import java.util.List;

/**
 * 不可变的档口/人员范围快照。用户与 Agent 的统一数据范围事实。
 *
 * <ul>
 *   <li>{@code outletScopeType}: ALL / ASSIGNED / NONE（档口维度）</li>
 *   <li>{@code peopleAll}: true=ALL_USERS, false=SELF（人员维度，与档口维度独立）</li>
 *   <li>{@code readableOutletIds}: 可读档口（ALL=本租户全部未删档口含禁用；ASSIGNED=有效绑定∩本租户；NONE=空）</li>
 *   <li>{@code usableOutletIds}: 可用于新建/选项的启用档口</li>
 *   <li>{@code unassignedAllowed}: 是否可访问 source_outlet_id 为空的遗留数据</li>
 * </ul>
 */
public record OutletAccessScope(
        Long tenantId,
        ActorType actorType,
        Long actorId,
        String outletScopeType,
        boolean peopleAll,
        boolean unassignedAllowed,
        List<Long> readableOutletIds,
        List<Long> usableOutletIds,
        Long defaultOutletId) {

    public enum ActorType { USER, AGENT }

    /** 档口维度的 SQL 过滤形态，订单与草稿共用，保证列表与详情一致。 */
    public enum OutletFilter { NO_FILTER, NOT_NULL, IN, IN_OR_NULL, ONLY_NULL, DENY }

    public static final String ALL = "ALL";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String NONE = "NONE";

    public OutletAccessScope {
        readableOutletIds = readableOutletIds == null ? List.of() : List.copyOf(readableOutletIds);
        usableOutletIds = usableOutletIds == null ? List.of() : List.copyOf(usableOutletIds);
    }

    public boolean isAll() { return ALL.equals(outletScopeType); }

    public boolean isAssigned() { return ASSIGNED.equals(outletScopeType); }

    public boolean isNone() { return NONE.equals(outletScopeType); }

    public boolean isPeopleAll() { return peopleAll; }

    public String peopleScopeType() { return peopleAll ? "ALL_USERS" : "SELF"; }

    /** 单档口锁定时前端只读展示。 */
    public boolean locked() { return usableOutletIds.size() == 1; }

    /**
     * 档口维度过滤：与 requireAccess/requireDraftAccess 的 NULL 规则严格一致。
     * <pre>
     * ALL + unassigned      -> NO_FILTER
     * ALL + !unassigned     -> NOT_NULL
     * ASSIGNED + unassigned -> IN_OR_NULL
     * ASSIGNED + !unassigned-> IN
     * NONE + unassigned     -> ONLY_NULL
     * NONE + !unassigned    -> DENY
     * </pre>
     */
    public OutletFilter outletFilter() {
        if (isAll()) {
            return unassignedAllowed ? OutletFilter.NO_FILTER : OutletFilter.NOT_NULL;
        }
        if (isAssigned()) {
            if (readableOutletIds.isEmpty()) {
                // 无有效绑定等价 NONE，避免生成 IN ()
                return unassignedAllowed ? OutletFilter.ONLY_NULL : OutletFilter.DENY;
            }
            return unassignedAllowed ? OutletFilter.IN_OR_NULL : OutletFilter.IN;
        }
        return unassignedAllowed ? OutletFilter.ONLY_NULL : OutletFilter.DENY;
    }

    /** 只允许本租户 readable 集合内的档口；NULL 取决于 unassignedAllowed。 */
    public boolean canReadOutlet(Long outletId) {
        if (outletId == null) {
            return unassignedAllowed;
        }
        return readableOutletIds.contains(outletId);
    }

    public boolean canUseOutlet(Long outletId) {
        return outletId != null && usableOutletIds.contains(outletId);
    }
}
