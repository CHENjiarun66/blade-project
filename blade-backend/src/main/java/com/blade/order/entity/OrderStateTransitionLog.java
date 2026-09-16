package com.blade.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志（只追加）。
 */
@Data
@TableName("order_state_transition_log")
public class OrderStateTransitionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long orderId;

    /**
     * confirmDraft / recordPayment / settleWithWriteOff / refundPayment / reverseFinancialRecord /
     * chooseFulfillmentMode / startAllocation / confirmAllocation / shipOrder / completeOrder /
     * cancelOrder / migrate
     */
    private String action;

    private String fromFulfillmentStatus;

    private String toFulfillmentStatus;

    private String fromCollectionStatus;

    private String toCollectionStatus;

    private String fromFulfillmentMode;

    private String toFulfillmentMode;

    private Long operatorId;

    private String operatorName;

    private String source;

    private String reason;

    private String idempotencyKey;

    private LocalDateTime occurredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
