package com.blade.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "沉默客户DTO")
public class SilentCustomerDTO {

    @Schema(description = "客户ID")
    private Long id;

    @Schema(description = "客户名称")
    private String name;

    @Schema(description = "国家区号")
    private String countryCode;

    @Schema(description = "电话号码")
    private String phone;

    @Schema(description = "最后订单日期")
    private String lastOrderDate;

    @Schema(description = "距今天数")
    private Integer daysSinceLastOrder;
}
