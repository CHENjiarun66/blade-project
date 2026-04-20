package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "客户分页查询DTO")
@Data
public class CustomerPageDTO {

    @Schema(description = "当前页", example = "1")
    private Integer current = 1;

    @Schema(description = "每页大小", example = "20")
    private Integer size = 20;

    @Schema(description = "关键字搜索（客户名称或电话）")
    private String keyword;
}
