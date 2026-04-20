package com.blade.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AddPaymentDTO {

    @NotNull(message = "追加金额不能为空")
    @DecimalMin(value = "0.01", message = "追加金额必须大于0")
    private BigDecimal additionalAmount;

    public BigDecimal getAdditionalAmount() {
        return additionalAmount;
    }

    public void setAdditionalAmount(BigDecimal additionalAmount) {
        this.additionalAmount = additionalAmount;
    }
}
