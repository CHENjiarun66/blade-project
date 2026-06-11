package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "客户商品偏好VO")
public class CustomerPreferenceVO {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "购买商品种类数")
    private Integer productTypeCount;

    @Schema(description = "偏好品类列表")
    private List<CategoryPreference> categories;

    @Schema(description = "偏好颜色列表")
    private List<ColorPreference> colors;

    @Schema(description = "偏好尺码列表")
    private List<SizePreference> sizes;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Integer getProductTypeCount() { return productTypeCount; }
    public void setProductTypeCount(Integer productTypeCount) { this.productTypeCount = productTypeCount; }
    public List<CategoryPreference> getCategories() { return categories; }
    public void setCategories(List<CategoryPreference> categories) { this.categories = categories; }
    public List<ColorPreference> getColors() { return colors; }
    public void setColors(List<ColorPreference> colors) { this.colors = colors; }
    public List<SizePreference> getSizes() { return sizes; }
    public void setSizes(List<SizePreference> sizes) { this.sizes = sizes; }

    @Schema(description = "品类偏好项")
    public static class CategoryPreference {
        @Schema(description = "品类名称")
        private String categoryName;
        @Schema(description = "购买次数")
        private Integer count;
        @Schema(description = "占比（0-100）")
        private Double percentage;

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Double getPercentage() { return percentage; }
        public void setPercentage(Double percentage) { this.percentage = percentage; }
    }

    @Schema(description = "颜色偏好项")
    public static class ColorPreference {
        @Schema(description = "颜色名称")
        private String colorName;
        @Schema(description = "购买次数")
        private Integer count;
        @Schema(description = "占比（0-100）")
        private Double percentage;

        public String getColorName() { return colorName; }
        public void setColorName(String colorName) { this.colorName = colorName; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Double getPercentage() { return percentage; }
        public void setPercentage(Double percentage) { this.percentage = percentage; }
    }

    @Schema(description = "尺码偏好项")
    public static class SizePreference {
        @Schema(description = "尺码名称")
        private String sizeName;
        @Schema(description = "购买次数")
        private Integer count;
        @Schema(description = "占比（0-100）")
        private Double percentage;

        public String getSizeName() { return sizeName; }
        public void setSizeName(String sizeName) { this.sizeName = sizeName; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Double getPercentage() { return percentage; }
        public void setPercentage(Double percentage) { this.percentage = percentage; }
    }
}
