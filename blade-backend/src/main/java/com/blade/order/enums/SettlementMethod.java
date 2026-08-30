package com.blade.order.enums;

/**
 * 结清方式：解释订单为什么达到 SETTLED，由财务快照服务根据流水计算，前端不可直接写入。
 */
public enum SettlementMethod {
    FULL_RECEIPT("足额收款"),
    WRITE_OFF("短款结清"),
    MIGRATION_CONFIRMED("迁移确认");

    private final String label;

    SettlementMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
