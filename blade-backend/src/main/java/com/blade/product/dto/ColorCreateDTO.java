package com.blade.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "颜色创建DTO")
public class ColorCreateDTO {

    @NotBlank(message = "颜色编码不能为空")
    private String colorCode;

    @NotBlank(message = "颜色名称不能为空")
    private String colorName;

    private Integer status = 1;

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
