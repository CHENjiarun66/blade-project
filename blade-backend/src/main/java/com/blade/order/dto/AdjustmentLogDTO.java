package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单调整记录请求DTO
 */
@Data
@Schema(description = "订单调整记录请求DTO")
public class AdjustmentLogDTO {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "调整类型：REDUCE减数量/REPLACE替换/REFUND退款")
    @NotBlank(message = "调整类型不能为空")
    private String adjustmentType;

    @Schema(description = "原SKU ID（替换时使用）")
    private Long originalSkuId;

    @Schema(description = "原数量")
    private Integer originalQuantity;

    @Schema(description = "新SKU ID（替换时使用）")
    private Long newSkuId;

    @Schema(description = "新数量")
    private Integer newQuantity;

    @Schema(description = "调整原因")
    private String reason;
}
