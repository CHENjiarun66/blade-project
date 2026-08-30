package com.blade.order.enums;

/**
 * 履约状态（新模型）。旧 status 数字的兼容投影只由 OrderCompatAdapter 生成。
 */
public enum FulfillmentStatus {
    CONFIRMED("已确认"),
    WAITING_ALLOCATION("待配货"),
    ALLOCATING("配货中"),
    READY_TO_SHIP("待发货"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    FulfillmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
