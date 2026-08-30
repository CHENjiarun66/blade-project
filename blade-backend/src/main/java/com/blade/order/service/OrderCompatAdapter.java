package com.blade.order.service;

import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentStatus;
import org.springframework.stereotype.Component;

/**
 * 新旧订单状态唯一映射点。
 * <p>
 * 任何模块不得自行维护映射（执行看板审核阻断项）。写入投影：统一动作服务在
 * 同一事务内调用 {@link #projectLegacyStatus(FulfillmentStatus)} /
 * {@link #projectLegacyPaymentStatus(CollectionStatus)} 生成旧字段。
 * 读取回退：仅 VO 展示层允许调用 {@link #displayFulfillmentStatus(String)} /
 * {@link #displayCollectionStatus(String)} 对历史未迁移行做旧→新反推，且响应必须
 * 携带 legacyUnmigrated 标记；反推结果严禁进入动作判定、统计事实、写回或状态机。
 */
@Component
public class OrderCompatAdapter {

    /**
     * 新履约状态 → 旧 status 数字投影。旧 7/8（退货语义）不参与映射。
     */
    public Integer projectLegacyStatus(FulfillmentStatus status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case CONFIRMED: return 0;
            case WAITING_ALLOCATION: return 1;
            case ALLOCATING: return 2;
            case READY_TO_SHIP: return 3;
            case SHIPPED: return 4;
            case COMPLETED: return 5;
            case CANCELLED: return 6;
            default: throw new IllegalArgumentException("未知履约状态: " + status);
        }
    }

    /**
     * 新收款状态 → 旧 payment_status 数字投影。
     */
    public Integer projectLegacyPaymentStatus(CollectionStatus status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case UNPAID: return 0;
            case PARTIAL: return 1;
            case SETTLED: return 2;
            default: throw new IllegalArgumentException("未知收款状态: " + status);
        }
    }

    /**
     * 展示回退：新字段为空（历史未迁移行）时由旧 status 反推，仅供 VO 展示。
     */
    public FulfillmentStatus displayFulfillmentStatus(String newStatus, Integer legacyStatus) {
        if (newStatus != null) {
            return FulfillmentStatus.valueOf(newStatus);
        }
        if (legacyStatus == null) {
            return null;
        }
        switch (legacyStatus) {
            case 0: return FulfillmentStatus.CONFIRMED;
            case 1: return FulfillmentStatus.WAITING_ALLOCATION;
            case 2: return FulfillmentStatus.ALLOCATING;
            case 3: return FulfillmentStatus.READY_TO_SHIP;
            case 4: return FulfillmentStatus.SHIPPED;
            case 5: return FulfillmentStatus.COMPLETED;
            case 6: return FulfillmentStatus.CANCELLED;
            default: return null; // 7/8 退货语义不自动映射
        }
    }

    /**
     * 展示回退：新字段为空（历史未迁移行）时由旧 payment_status 反推，仅供 VO 展示。
     */
    public CollectionStatus displayCollectionStatus(String newStatus, Integer legacyPaymentStatus) {
        if (newStatus != null) {
            return CollectionStatus.valueOf(newStatus);
        }
        if (legacyPaymentStatus == null) {
            return null;
        }
        switch (legacyPaymentStatus) {
            case 0: return CollectionStatus.UNPAID;
            case 1: return CollectionStatus.PARTIAL;
            case 2: return CollectionStatus.SETTLED;
            default: return null;
        }
    }
}
