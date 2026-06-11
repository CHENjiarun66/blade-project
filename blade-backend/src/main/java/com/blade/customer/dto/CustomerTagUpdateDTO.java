package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新客户标签DTO")
public class CustomerTagUpdateDTO {

    @NotNull(message = "标签ID不能为空")
    @Schema(description = "标签ID")
    private Long id;

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 32, message = "标签名称最多32个字符")
    @Schema(description = "标签名称")
    private String name;

    @NotBlank(message = "颜色不能为空")
    @Schema(description = "颜色值，如 #FF6B6B")
    private String color;

    @Schema(description = "排序")
    private Integer sort = 0;
}
