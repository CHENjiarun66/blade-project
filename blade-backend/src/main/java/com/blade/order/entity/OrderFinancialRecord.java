package com.blade.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单财务流水（只追加）。实体与服务不提供更新、软删或物理删除能力；
 * 查询不得通过 deleted 隐藏历史流水，纠错只能追加 REVERSAL。
 */
@Data
@TableName("order_financial_record")
public class OrderFinancialRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long orderId;

    /**
     * RECEIPT / WRITE_OFF / REFUND / REVERSAL / MIGRATION_OPENING
     */
    private String recordType;

    /**
     * 本次金额，恒为正数
     */
    private BigDecimal amount;

    private String paymentMethod;

    /**
     * 业务发生时间（现金流统计口径）
     */
    private LocalDateTime occurredAt;

    private Long operatorId;

    private String operatorName;

    private String reason;

    /**
     * PC / MOBILE / AGENT / MIGRATION
     */
    private String source;

    private String idempotencyKey;

    /**
     * 仅 REVERSAL 可填写，指向被冲销流水
     */
    private Long reversedRecordId;

    /**
     * 仅满足项目字段规范；不提供软删能力，查询不按此过滤
     */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
