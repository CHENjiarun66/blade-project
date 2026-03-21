package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "取消订单DTO")
public class CancelOrderDTO {

    @NotBlank(message = "取消原因不能为空")
    @Schema(description = "取消原因")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
