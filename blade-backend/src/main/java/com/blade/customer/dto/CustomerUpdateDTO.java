package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "客户更新DTO")
@Data
public class CustomerUpdateDTO {

    @Schema(description = "客户ID", required = true)
    private Long id;

    @Schema(description = "客户名称", required = true)
    private String name;

    @Schema(description = "电话列表")
    private List<String> phones;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "国家区号，如+86")
    private String countryCode;
}
