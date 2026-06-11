package com.blade.dashboard.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 库存统计数据（周转分析）
 */
@Data
public class InventoryStatsVO {
    /** 库存周转率（销售数量/平均库存），保留2位小数 */
    private BigDecimal turnoverRate;
    /** 当前库存总量（所有SKU * 所有仓库） */
    private Long totalQuantity;
    /** 当前库存SKU种数 */
    private Long totalSkuCount;
    /** 低库存预警数（库存 < 10） */
    private Long lowStockCount;
    /** 高库存积压数（库存 > 100，可能有滞销） */
    private Long overstockCount;
}
