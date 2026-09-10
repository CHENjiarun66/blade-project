package com.blade.order.draft;

import com.blade.order.draft.dto.OrderDraftDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDraftValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void batchRejectsMoreThanOneHundredOrders() {
        OrderDraftDTO.BatchRequest request = new OrderDraftDTO.BatchRequest();
        List<OrderDraftDTO.SaveRequest> orders = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            orders.add(validOrder("BATCH-" + index));
        }
        request.setOrders(orders);

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("orders");
                    assertThat(violation.getMessage()).contains("100");
                });
    }

    @Test
    void orderRejectsMoreThanTwoHundredItems() {
        OrderDraftDTO.SaveRequest order = validOrder("TOO-MANY-ITEMS");
        List<OrderDraftDTO.Item> items = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            items.add(new OrderDraftDTO.Item());
        }
        order.setItems(items);

        assertThat(validator.validate(order))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("items");
                    assertThat(violation.getMessage()).contains("200");
                });
    }

    private OrderDraftDTO.SaveRequest validOrder(String externalRefNo) {
        OrderDraftDTO.SaveRequest order = new OrderDraftDTO.SaveRequest();
        order.setExternalRefNo(externalRefNo);
        order.setItems(List.of(new OrderDraftDTO.Item()));
        return order;
    }
}
