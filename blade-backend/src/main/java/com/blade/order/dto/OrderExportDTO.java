package com.blade.order.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单导出Excel模型
 */
@Data
public class OrderExportDTO {

    @ExcelProperty("订单号")
    @ColumnWidth(20)
    private String orderNo;

    @ExcelProperty("纸质单号")
    @ColumnWidth(15)
    private String sourceDocNo;

    @ExcelProperty("来源档口")
    @ColumnWidth(15)
    private String sourceShop;

    @ExcelProperty("订单日期")
    @ColumnWidth(15)
    private String orderDate;

    @ExcelProperty("订单类型")
    @ColumnWidth(12)
    private String orderTypeName;

    @ExcelProperty("订单状态")
    @ColumnWidth(12)
    private String statusName;

    @ExcelProperty("支付状态")
    @ColumnWidth(12)
    private String paymentStatusName;

    @ExcelProperty("客户名称")
    @ColumnWidth(20)
    private String customerName;

    @ExcelProperty("客户电话")
    @ColumnWidth(15)
    private String customerPhone;

    @ExcelProperty("商品名称")
    @ColumnWidth(25)
    private String productName;

    @ExcelProperty("SKU编码")
    @ColumnWidth(15)
    private String skuCode;

    @ExcelProperty("颜色")
    @ColumnWidth(10)
    private String colorName;

    @ExcelProperty("尺码")
    @ColumnWidth(8)
    private String sizeName;

    @ExcelProperty("数量")
    @ColumnWidth(8)
    private Integer quantity;

    @ExcelProperty("单价")
    @ColumnWidth(12)
    private BigDecimal price;

    @ExcelProperty("成本价")
    @ColumnWidth(12)
    private BigDecimal costPrice;

    @ExcelProperty("小计")
    @ColumnWidth(12)
    private BigDecimal subtotal;

    @ExcelProperty("成本金额")
    @ColumnWidth(12)
    private BigDecimal costAmount;

    @ExcelProperty("明细毛利")
    @ColumnWidth(12)
    private BigDecimal itemGrossProfit;

    @ExcelProperty("订单总额")
    @ColumnWidth(12)
    private BigDecimal totalAmount;

    @ExcelProperty("已付金额")
    @ColumnWidth(12)
    private BigDecimal paidAmount;

    @ExcelProperty("抹零金额")
    @ColumnWidth(12)
    private BigDecimal writeOffAmount;

    @ExcelProperty("抹零原因")
    @ColumnWidth(20)
    private String writeOffReason;

    @ExcelProperty("尾款")
    @ColumnWidth(12)
    private BigDecimal balanceAmount;

    @ExcelProperty("运费收入")
    @ColumnWidth(12)
    private BigDecimal freightAmount;

    @ExcelProperty("运费成本")
    @ColumnWidth(12)
    private BigDecimal freightCost;

    @ExcelProperty("总成本")
    @ColumnWidth(12)
    private BigDecimal totalCostAmount;

    @ExcelProperty("订单毛利")
    @ColumnWidth(12)
    private BigDecimal grossProfit;

    @ExcelProperty("开单人员")
    @ColumnWidth(12)
    private String salesmanName;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;

    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;
}
