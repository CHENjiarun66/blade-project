package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "财务流水冲销请求")
public class OrderReverseDTO {

    @NotNull(message = "被冲销流水ID不能为空")
    private Long recordId;

    @NotBlank(message = "冲销必须填写原因")
    private String reason;

    /** 外部请求幂等键（可选） */
    private String idempotencyKey;
}
