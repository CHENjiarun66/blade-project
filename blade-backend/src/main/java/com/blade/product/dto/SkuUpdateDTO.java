package com.blade.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 单个 SKU 更新 DTO — PUT /api/products/skus
 * 用于 SKU 明细精细维护：单独编辑售价、成本价、条码、状态
 */
@Schema(description = "单个SKU更新DTO")
public class SkuUpdateDTO {

    @NotNull(message = "SKU ID不能为空")
    @Schema(description = "SKU ID")
    private Long id;

    @Schema(description = "售价")
    private BigDecimal price;

    @Schema(description = "成本价")
    private BigDecimal costPrice;

    @Schema(description = "条形码")
    private String barCode;

    @Schema(description = "状态: 1启用 0禁用")
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public String getBarCode() { return barCode; }
    public void setBarCode(String barCode) { this.barCode = barCode; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
