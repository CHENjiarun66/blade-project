package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    private List<PlanItemDTO> items;

    @Data
    @Schema(description = "配货明细项")
    public static class PlanItemDTO {

        @Schema(description = "订单明细ID（可选，用于追踪原商品）")
        private Long orderItemId;

        @Schema(description = "SKU ID")
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        @Schema(description = "仓库ID")
        private Long warehouseId;

        @Schema(description = "计划数量（原订单数量）")
        private Integer plannedQty;

        @Schema(description = "配货数量（调整后数量）")
        private Integer allocatedQty;

        @Schema(description = "备注")
        private String remark;
    }
}
