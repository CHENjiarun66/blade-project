package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "客户偏好查询DTO")
public class CustomerPreferenceQueryDTO {

    @Schema(description = "开始日期（格式：yyyy-MM-dd），默认365天前")
    private String startDate;

    @Schema(description = "结束日期（格式：yyyy-MM-dd），默认今天")
    private String endDate;
}
