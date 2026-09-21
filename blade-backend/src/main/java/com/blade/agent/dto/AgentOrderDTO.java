package com.blade.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AgentOrderDTO {
    private AgentOrderDTO() {
    }

    public record OrderView(
            Long id,
            String orderNo,
            LocalDate orderDate,
            String sourceDocNo,
            String sourceShop,
            String sourceOutletCode,
            String orderType,
            String orderTypeName,
            Long customerId,
            String customerName,
            BigDecimal totalAmount,
            BigDecimal grossReceivedAmount,
            BigDecimal cashRefundAmount,
            BigDecimal salesReturnAmount,
            BigDecimal netReceivedAmount,
            BigDecimal writeOffAmount,
            BigDecimal balanceAmount,
            String collectionStatus,
            String fulfillmentStatus,
            String fulfillmentMode,
            String settlementMethod,
            LocalDateTime settledAt,
            BigDecimal freightAmount,
            Integer needDelivery,
            String warehouseName,
            String salesmanName,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            List<OrderItemView> items) {
    }

    public record OrderItemView(
            Long id,
            Long skuId,
            String skuCode,
            String skuType,
            Boolean variantUnresolved,
            String productName,
            String colorName,
            String sizeName,
            BigDecimal price,
            Integer quantity,
            BigDecimal subtotal) {
    }
}
