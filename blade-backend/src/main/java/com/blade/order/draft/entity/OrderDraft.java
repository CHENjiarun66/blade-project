package com.blade.order.draft.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("order_draft")
public class OrderDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String externalRefNo;
    private String sourceBatchNo;
    private String sourceOrderNo;
    private Long sourceFileId;
    private String rawCustomerName;
    private String rawCustomerPhone;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String rawOrderDate;
    private LocalDate orderDate;
    private LocalDate deliveryDate;
    private String rawDeposit;
    private BigDecimal deposit;
    private BigDecimal paperTotalAmount;
    private String note;
    private String warnings;
    private String status;
    private Long confirmedOrderId;
    private Long createdByAgentKeyId;
    private Long confirmedBy;
    private LocalDateTime confirmedTime;
    private Integer warningAcknowledged;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
