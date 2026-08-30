package com.blade.order.enums;

/**
 * 履约方式。数据库默认 UNDECIDED；仅已结清订单允许选择。
 */
public enum FulfillmentMode {
    UNDECIDED("尚未选择"),
    STOCK_LINKED("关联库存"),
    RECORD_ONLY("仅记录订单");

    private final String label;

    FulfillmentMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
