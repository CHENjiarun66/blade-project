package com.blade.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AddPaymentDTO {

    @NotNull(message = "追加金额不能为空")
    @DecimalMin(value = "0.00", message = "追加金额不能为负数")
    private BigDecimal additionalAmount;

    /**
     * 是否标记结清：true 时将当前尾款写入 write_off_amount，
     * 并将 payment_status 更新为 2（已结清）。
     * 默认 false，保持原有追加收款行为。
     */
    private Boolean markAsSettled;

    /**
     * 抹零/短款结清原因。markAsSettled=true 时必填。
     */
    private String writeOffReason;

    public BigDecimal getAdditionalAmount() {
        return additionalAmount;
    }

    public void setAdditionalAmount(BigDecimal additionalAmount) {
        this.additionalAmount = additionalAmount;
    }

    public Boolean getMarkAsSettled() {
        return markAsSettled;
    }

    public void setMarkAsSettled(Boolean markAsSettled) {
        this.markAsSettled = markAsSettled;
    }

    public String getWriteOffReason() {
        return writeOffReason;
    }

    public void setWriteOffReason(String writeOffReason) {
        this.writeOffReason = writeOffReason;
    }
}
