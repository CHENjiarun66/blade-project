package com.blade.agent;

import com.blade.agent.controller.AgentOrderQueryController;
import com.blade.agent.controller.AgentProductController;
import com.blade.agent.dto.AgentOrderDTO;
import com.blade.agent.dto.AgentProductDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentDataAccessContractTest {

    @Test
    void productAndOrderEndpointsUseIndependentScopes() throws Exception {
        assertEquals("hasAuthority('agent:products:read')",
                annotation(AgentProductController.class, "list", com.blade.product.dto.ProductPageDTO.class).value());
        assertEquals("hasAuthority('agent:products:create')",
                annotation(AgentProductController.class, "create", AgentProductDTO.CreateRequest.class).value());
        assertEquals("hasAuthority('agent:orders:read')",
                annotation(AgentOrderQueryController.class, "list", com.blade.order.dto.OrderPageDTO.class).value());
    }

    @Test
    void agentViewsCannotSerializeCostProfitOrCustomerPii() {
        Set<String> productFields = componentNames(AgentProductDTO.ProductView.class);
        Set<String> productSkuFields = componentNames(AgentProductDTO.SkuView.class);
        Set<String> orderFields = componentNames(AgentOrderDTO.OrderView.class);
        Set<String> orderItemFields = componentNames(AgentOrderDTO.OrderItemView.class);

        assertFalse(productFields.contains("costPrice"));
        assertFalse(productSkuFields.contains("costPrice"));
        assertFalse(orderFields.contains("customerPhone"));
        assertFalse(orderFields.contains("customerAddress"));
        assertFalse(orderFields.contains("remark"));
        assertFalse(orderFields.contains("grossProfit"));
        assertFalse(orderFields.contains("totalCostAmount"));
        assertFalse(orderItemFields.contains("costPrice"));
        assertFalse(orderItemFields.contains("costAmount"));
        assertFalse(orderItemFields.contains("grossProfit"));
    }

    private PreAuthorize annotation(Class<?> controller, String method, Class<?> parameter) throws Exception {
        return controller.getMethod(method, parameter).getAnnotation(PreAuthorize.class);
    }

    private Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }
}
