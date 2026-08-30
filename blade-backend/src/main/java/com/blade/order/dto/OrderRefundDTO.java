package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "现金退款请求")
public class OrderRefundDTO {

    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.01", message = "退款金额必须大于0")
    private BigDecimal amount;

    @NotBlank(message = "退款必须填写原因")
    private String reason;

    /** 外部请求幂等键（可选） */
    private String idempotencyKey;
}
