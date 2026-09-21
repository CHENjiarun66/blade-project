package com.blade.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.blade.order.entity.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变的订单统计读取范围（Series E1）：由 {@link OrderAccessPolicy} 从当前 actor 解析。
 *
 * <p>与订单列表一致使用同一档口 × 人员范围，但统计口径更严格：未归档
 * （{@code source_outlet_id IS NULL}）默认<b>不</b>纳入销售/排行/档口对比，
 * 仅在拥有 {@code data:outlet:unassigned} 且显式 {@code pendingArchive=true} 时
 * 单独提供“待归档数量/列表”，绝不混入销售统计。</p>
 *
 * <p>{@link #cacheFingerprint()} 覆盖 tenantId + outletScope + peopleScope + allowedOutletIds
 * + 显式选择 + pendingArchive；当前 Dashboard/Analytics 没有缓存，指纹用于未来若引入缓存时
 * 强制把范围纳入 key，避免跨用户/跨范围复用。</p>
 */
public record OrderReadScope(
        Long tenantId,
        Long actorId,
        String outletScopeType,
        boolean peopleAll,
        boolean unassignedAllowed,
        List<Long> readableOutletIds,
        List<Long> selectedOutletIds,
        boolean pendingArchive) {

    public static final String ALL = "ALL";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String NONE = "NONE";

    public OrderReadScope {
        readableOutletIds = readableOutletIds == null ? List.of() : List.copyOf(readableOutletIds);
        selectedOutletIds = selectedOutletIds == null || selectedOutletIds.isEmpty()
                ? null : List.copyOf(selectedOutletIds);
    }

    public boolean isAll() {
        return ALL.equals(outletScopeType);
    }

    public boolean isNone() {
        return NONE.equals(outletScopeType);
    }

    /**
     * 销售/经营统计谓词（不含未归档 NULL）：档口范围 + 显式档口 + 人员范围。
     * NONE 直接 {@code 1=0}，禁止回落全租户。
     */
    public void applySalesPredicate(LambdaQueryWrapper<Order> wrapper) {
        if (isNone()) {
            wrapper.apply("1 = 0");
        } else if (isAll()) {
            wrapper.isNotNull(Order::getSourceOutletId);
        } else if (readableOutletIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(Order::getSourceOutletId, readableOutletIds);
        }
        if (selectedOutletIds != null) {
            wrapper.in(Order::getSourceOutletId, selectedOutletIds);
        }
        applyPeoplePredicate(wrapper);
    }

    /** 待归档数量/列表谓词：仅 unassigned 权限 + 显式 pendingArchive 时使用。 */
    public void applyPendingArchivePredicate(LambdaQueryWrapper<Order> wrapper) {
        wrapper.isNull(Order::getSourceOutletId);
        applyPeoplePredicate(wrapper);
    }

    /**
     * 同 {@link #applySalesPredicate(LambdaQueryWrapper)} 的字符串列版本，供
     * {@code QueryWrapper.select(...)/groupBy(...)} 的 GROUP BY 批量统计复用，保证口径单一。
     */
    public void applySalesPredicate(QueryWrapper<Order> wrapper) {
        if (isNone()) {
            wrapper.apply("1 = 0");
        } else if (isAll()) {
            wrapper.isNotNull("source_outlet_id");
        } else if (readableOutletIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in("source_outlet_id", readableOutletIds);
        }
        if (selectedOutletIds != null) {
            wrapper.in("source_outlet_id", selectedOutletIds);
        }
        if (!peopleAll) {
            wrapper.eq("salesman_id", actorId != null ? actorId : -1L);
        }
    }

    private void applyPeoplePredicate(LambdaQueryWrapper<Order> wrapper) {
        if (!peopleAll) {
            wrapper.eq(Order::getSalesmanId, actorId != null ? actorId : -1L);
        }
    }

    /** 稳定范围指纹：权限/绑定/选择任何变化都会改变它。 */
    public String cacheFingerprint() {
        List<Long> readable = new ArrayList<>(readableOutletIds);
        Collections.sort(readable);
        List<Long> selected = selectedOutletIds == null ? new ArrayList<>() : new ArrayList<>(selectedOutletIds);
        Collections.sort(selected);
        return "t=" + tenantId
                + "|o=" + outletScopeType
                + "|p=" + (peopleAll ? "ALL_USERS" : "SELF")
                + "|a=" + actorId
                + "|r=" + readable
                + "|s=" + selected
                + "|pa=" + pendingArchive;
    }
}
