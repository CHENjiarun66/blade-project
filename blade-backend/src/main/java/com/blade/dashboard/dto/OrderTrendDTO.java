package com.blade.dashboard.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单趋势数据
 */
@Data
public class OrderTrendDTO {
    private List<String> dates;
    private List<Long> orderCounts;
    private List<BigDecimal> salesAmounts;
}
