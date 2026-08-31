package com.blade.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 按最终累计实收确认整单结清。 */
public class ConfirmSettlementDTO {

    @NotNull(message = "最终累计实收不能为空")
    @DecimalMin(value = "0.00", message = "最终累计实收不能为负数")
    private BigDecimal finalReceivedAmount;

    private String writeOffReason;

    private String idempotencyKey;

    public BigDecimal getFinalReceivedAmount() {
        return finalReceivedAmount;
    }

    public void setFinalReceivedAmount(BigDecimal finalReceivedAmount) {
        this.finalReceivedAmount = finalReceivedAmount;
    }

    public String getWriteOffReason() {
        return writeOffReason;
    }

    public void setWriteOffReason(String writeOffReason) {
        this.writeOffReason = writeOffReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
