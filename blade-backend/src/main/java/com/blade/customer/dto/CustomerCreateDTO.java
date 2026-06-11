package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "客户创建DTO")
@Data
public class CustomerCreateDTO {

    @Schema(description = "客户名称", required = true)
    private String name;

    @Schema(description = "电话列表", required = true)
    private List<String> phones;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "国家区号，如+86")
    private String countryCode;
}
