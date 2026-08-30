package com.blade.order.enums;

/**
 * 收款状态（新模型）。判定只依据金额快照：UNPAID=未收款，PARTIAL=部分收款，SETTLED=已结清。
 */
public enum CollectionStatus {
    UNPAID("未收款"),
    PARTIAL("部分收款"),
    SETTLED("已结清");

    private final String label;

    CollectionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
