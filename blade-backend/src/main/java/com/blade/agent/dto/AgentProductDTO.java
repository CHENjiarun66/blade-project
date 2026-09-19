package com.blade.agent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AgentProductDTO {
    private AgentProductDTO() {
    }

    public record ProductView(
            Long id,
            String productCode,
            String name,
            Long categoryId,
            String categoryName,
            Long supplierId,
            String supplierName,
            String unit,
            BigDecimal wholesalePrice,
            BigDecimal weight,
            String description,
            String imageUrl,
            String remark,
            Integer status,
            List<ColorView> colors,
            List<SizeView> sizes,
            List<SkuView> skus,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
    }

    public record ColorView(Long id, String colorCode, String colorName, Integer status) {
    }

    public record SizeView(Long id, String sizeCode, Integer sort, Integer status) {
    }

    public record CategoryView(Long id, String categoryName, Long parentId, Integer sort, Integer status) {
    }

    public record OptionsView(List<CategoryView> categories, List<ColorView> colors, List<SizeView> sizes) {
    }

    public record SkuView(
            Long id,
            String skuCode,
            String skuType,
            boolean placeholder,
            String colorName,
            String sizeName,
            BigDecimal price,
            String barCode,
            Integer status) {
    }

    public record CreateRequest(
            @NotBlank(message = "商品编码不能为空") @Size(max = 30, message = "商品编码最多30位") String productCode,
            @NotBlank(message = "商品名称不能为空") @Size(max = 100, message = "商品名称最多100位") String name,
            Long categoryId,
            @Size(max = 10, message = "单位最多10位") String unit,
            @DecimalMin(value = "0.00", message = "成本价不能小于0") BigDecimal costPrice,
            @DecimalMin(value = "0.00", message = "批发价不能小于0") BigDecimal wholesalePrice,
            @DecimalMin(value = "0.00", message = "重量不能小于0") BigDecimal weight,
            @Size(max = 1000, message = "描述最多1000位") String description,
            @Size(max = 500, message = "备注最多500位") String remark,
            @Size(max = 50, message = "颜色最多50个") List<String> colorCodes,
            @Size(max = 50, message = "尺码最多50个") List<String> sizeCodes) {
    }

    public record CreateResult(
            Long productId,
            String productCode,
            String result,
            int skuCount,
            BigDecimal appliedCostPrice) {
    }
}
