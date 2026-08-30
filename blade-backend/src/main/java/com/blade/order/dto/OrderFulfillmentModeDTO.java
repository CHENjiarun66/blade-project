package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "履约方式选择请求")
public class OrderFulfillmentModeDTO {

    @NotBlank(message = "履约方式不能为空")
    private String mode;
}
