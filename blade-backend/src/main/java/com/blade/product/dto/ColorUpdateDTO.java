package com.blade.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "颜色更新DTO")
public class ColorUpdateDTO {

    @NotNull(message = "颜色ID不能为空")
    private Long id;

    @NotBlank(message = "颜色编码不能为空")
    private String colorCode;

    @NotBlank(message = "颜色名称不能为空")
    private String colorName;

    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
