package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        @NotNull(message = "目标SKU不能为空")
        private Long skuId;

        @NotNull(message = "拆分数量不能为空")
        @Positive(message = "拆分数量必须为正整数")
        private Integer quantity;
    }
}
