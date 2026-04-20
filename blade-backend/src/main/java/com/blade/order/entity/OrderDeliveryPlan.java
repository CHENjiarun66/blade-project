package com.blade.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单发货计划实体
 */
@Data
@TableName("order_delivery_plan")
public class OrderDeliveryPlan {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 原订单明细ID（可空，用于追踪原商品）
     */
    private Long orderItemId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 仓库ID（配货时填写）
     */
    private Long warehouseId;

    /**
     * 计划数量（原订单数量）
     */
    private Integer plannedQty;

    /**
     * 配货数量（调整后数量）
     */
    private Integer allocatedQty;

    /**
     * 已出库数量
     */
    private Integer outQty;

    /**
     * 状态：PENDING待配/ALLOCATED已配/OUT已完成
     */
    private String status;

    /**
     * 备注（如调整原因）
     */
    private String remark;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 配货计划状态枚举
     */
    public static class Status {
        public static final String PENDING = "PENDING";      // 待配
        public static final String ALLOCATED = "ALLOCATED";  // 已配
        public static final String OUT = "OUT";              // 已出库
    }
}
