package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "客户订单分页查询DTO")
public class CustomerOrderPageDTO {

    @Schema(description = "当前页", example = "1")
    private Integer current = 1;

    @Schema(description = "每页条数", example = "20")
    private Integer size = 20;

    @Schema(description = "最大每页条数", example = "100")
    private Integer maxSize = 100;
}
