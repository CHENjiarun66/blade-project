package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 配货计划响应VO
 */
@Data
@Schema(description = "配货计划响应VO")
public class DeliveryPlanVO {

    @Schema(description = "配货计划ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "原订单明细ID")
    private Long orderItemId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "颜色")
    private String colorName;

    @Schema(description = "尺码")
    private String sizeName;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "仓库名称")
    private String warehouseName;

    @Schema(description = "计划数量（原订单数量）")
    private Integer plannedQty;

    @Schema(description = "配货数量（调整后数量）")
    private Integer allocatedQty;

    @Schema(description = "已出库数量")
    private Integer outQty;

    @Schema(description = "状态：PENDING待配/ALLOCATED已配/OUT已完成")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "配货明细列表")
    private List<DeliveryPlanVO> items;
}
