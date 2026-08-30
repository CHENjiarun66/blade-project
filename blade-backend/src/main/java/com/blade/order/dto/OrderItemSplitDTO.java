package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "占位明细拆分请求")
public class OrderItemSplitDTO {

    @NotEmpty(message = "拆分目标不能为空")
    @Valid
    private List<Target> targets;

    private String reason;

    @Data
    @Schema(description = "拆分目标行")
    public static class Target {
        private Long skuId;
        private Integer quantity;
    }
}
