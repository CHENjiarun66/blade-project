package com.blade.order.enums;

/**
 * 财务流水类型。RECEIPT 增加累计实收；WRITE_OFF 增加短款核销；
 * REFUND 仅表示现金退款（销售退货是独立语义，不在本枚举内）；
 * REVERSAL 冲销指定流水；MIGRATION_OPENING 迁移期初。
 */
public enum FinancialRecordType {
    RECEIPT,
    WRITE_OFF,
    REFUND,
    REVERSAL,
    MIGRATION_OPENING
}
