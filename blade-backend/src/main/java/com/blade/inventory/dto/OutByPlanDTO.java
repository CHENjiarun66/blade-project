package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 按配货计划出库DTO
 */
@Data
@Schema(description = "按配货计划出库请求DTO")
public class OutByPlanDTO {

    @Schema(description = "配货计划ID")
    @NotNull(message = "配货计划ID不能为空")
    private Long planId;

    @Schema(description = "出库数量")
    @NotNull(message = "出库数量不能为空")
    private Integer quantity;
}
