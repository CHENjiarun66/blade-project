package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 配货计划请求DTO
 */
@Data
@Schema(description = "配货计划请求DTO")
public class DeliveryPlanDTO {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "配货明细")
    @NotEmpty(message = "配货明细不能为空")
    @Valid
    private List<PlanItemDTO> items;

    @Data
    @Schema(description = "配货明细项")
    public static class PlanItemDTO {

        @Schema(description = "订单明细ID")
        @NotNull(message = "订单明细ID不能为空")
        private Long orderItemId;

        @Schema(description = "SKU ID")
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        @Schema(description = "仓库ID")
        private Long warehouseId;

        @Schema(description = "计划数量（原订单数量）")
        @NotNull(message = "计划数量不能为空")
        @Positive(message = "计划数量必须为正数")
        private Integer plannedQty;

        @Schema(description = "配货数量（调整后数量）")
        @NotNull(message = "配货数量不能为空")
        @Positive(message = "配货数量必须为正数")
        private Integer allocatedQty;

        @Schema(description = "备注")
        private String remark;
    }
}
