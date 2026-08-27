package com.blade.order.draft.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDraftDTO {
    private OrderDraftDTO() {
    }

    @Data
    public static class BatchRequest {
        @NotEmpty(message = "订单批次不能为空")
        @Valid
        private List<SaveRequest> orders;
    }

    @Data
    public static class SaveRequest {
        @NotBlank(message = "externalRefNo不能为空")
        @Size(max = 100)
        private String externalRefNo;
        private String sourceBatchNo;
        private String sourceOrderNo;
        private Long sourceFileId;
        private String rawCustomerName;
        private String rawCustomerPhone;
        private Long customerId;
        private String customerName;
        private String customerPhone;
        private String rawOrderDate;
        private LocalDate orderDate;
        private LocalDate deliveryDate;
        private String rawDeposit;
        private BigDecimal deposit;
        private BigDecimal paperTotalAmount;
        @Size(max = 1000)
        private String note;
        private List<String> warnings;
        @NotEmpty(message = "草稿明细不能为空")
        @Valid
        private List<Item> items;
    }

    @Data
    public static class Item {
        private Long id;
        private Integer sourceRowNo;
        private String rawProductCode;
        private String rawDescription;
        private String rawColor;
        private String rawQuantity;
        private String rawSalePrice;
        private String rawAmount;
        private Long productId;
        private Long skuId;
        private Integer quantity;
        private BigDecimal salePrice;
        private BigDecimal paperAmount;
        private BigDecimal systemReferencePrice;
        private String matchStatus;
        private List<CatalogCandidate> matchCandidates;
        private List<String> warnings;
    }

    @Data
    public static class BatchResponse {
        private List<BatchResult> results;
    }

    @Data
    public static class BatchResult {
        private String externalRefNo;
        private String status;
        private Long draftId;
        private List<String> warnings;
        private String message;
    }

    @Data
    public static class View {
        private Long id;
        private String externalRefNo;
        private String sourceBatchNo;
        private String sourceOrderNo;
        private Long sourceFileId;
        private String rawCustomerName;
        private String rawCustomerPhone;
        private Long customerId;
        private String customerName;
        private String customerPhone;
        private String rawOrderDate;
        private LocalDate orderDate;
        private LocalDate deliveryDate;
        private String rawDeposit;
        private BigDecimal deposit;
        private BigDecimal paperTotalAmount;
        private BigDecimal calculatedTotalAmount;
        private String note;
        private List<String> warnings;
        private String status;
        private Long confirmedOrderId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private List<Item> items;
    }

    @Data
    public static class Summary {
        private Long id;
        private String externalRefNo;
        private String sourceOrderNo;
        private Long sourceFileId;
        private String customerName;
        private LocalDate orderDate;
        private BigDecimal paperTotalAmount;
        private String status;
        private Integer itemCount;
        private Integer unresolvedCount;
        private Integer warningCount;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ConfirmRequest {
        private boolean acknowledgeWarnings;
    }

    @Data
    public static class ConfirmResponse {
        private Long draftId;
        private Long orderId;
        private boolean alreadyConfirmed;
    }

    @Data
    public static class CatalogCandidate {
        private Long skuId;
        private String skuCode;
        private String skuType;
        private boolean placeholder;
        private Long productId;
        private String productCode;
        private String productName;
        private String colorCode;
        private String colorName;
        private String sizeCode;
        private BigDecimal systemReferencePrice;
        private BigDecimal matchScore;
        private List<String> matchReasons;
    }
}
