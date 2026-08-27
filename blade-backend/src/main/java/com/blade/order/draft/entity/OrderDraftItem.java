package com.blade.order.draft.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_draft_item")
public class OrderDraftItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long draftId;
    private Integer sourceRowNo;
    private String rawProductCode;
    private String rawDescription;
    private String rawColor;
    private String rawQuantity;
    private String rawSalePrice;
    private String rawAmount;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private BigDecimal salePrice;
    private BigDecimal paperAmount;
    private BigDecimal systemReferencePrice;
    private String matchStatus;
    private String matchCandidates;
    private String warnings;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
