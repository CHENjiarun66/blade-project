package com.blade.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "沉默客户响应DTO")
public class SilentCustomerResultDTO {

    @Schema(description = "沉默客户总数")
    private Integer total;

    @Schema(description = "沉默客户列表")
    private List<SilentCustomerDTO> customers;
}
