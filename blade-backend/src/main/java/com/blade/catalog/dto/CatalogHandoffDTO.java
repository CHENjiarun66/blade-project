package com.blade.catalog.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** Customer selection is a draft for staff review, never a confirmed order. */
public final class CatalogHandoffDTO {
    private CatalogHandoffDTO() {}

    @Data
    public static class Request {
        @NotBlank
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private String requestId;
        @NotNull(message = "请选择开单档口") @Positive
        private Long sourceOutletId;
        @NotEmpty(message = "请先勾选需要移交的款式") @Size(max = 200)
        private List<@NotNull @Valid Item> items;
    }

    @Data
    public static class Item {
        @NotNull @Positive
        private Long productId;
        @Size(max = 100, message = "颜色说明最多100字")
        private String colorNote;
        @Min(value = 1, message = "数量须为正整数") @Max(999999)
        @JsonDeserialize(using = WholeQuantity.class)
        private Integer quantity;
        @DecimalMin(value = "0.00", message = "单价不能为负数")
        @Digits(integer = 6, fraction = 2, message = "单价最多6位整数和2位小数")
        private BigDecimal unitPrice;
    }

    public static class WholeQuantity extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedNumberIntToken()) return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            return parser.getIntValue();
        }
    }

    @Data
    public static class Response {
        private Long draftId;
        private String reference;
        private boolean alreadyExists;
    }
}
