package com.blade.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单调整记录实体
 */
@Data
@TableName("order_adjustment_log")
public class OrderAdjustmentLog {

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
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;

    /**
     * 调整类型：REDUCE减数量/REPLACE替换/REFUND退款
     */
    private String adjustmentType;

    /**
     * 原SKU ID
     */
    private Long originalSkuId;

    /**
     * 原数量
     */
    private Integer originalQuantity;

    /**
     * 新SKU ID（替换时使用）
     */
    private Long newSkuId;

    /**
     * 新数量
     */
    private Integer newQuantity;

    /**
     * 调整原因
     */
    private String reason;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 调整类型枚举
     */
    public static class Type {
        public static final String REDUCE = "REDUCE";    // 减数量
        public static final String REPLACE = "REPLACE";  // 替换
        public static final String REFUND = "REFUND";    // 退款
    }
}
