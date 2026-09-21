package com.blade.agent;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.controller.AgentAnalyticsController;
import com.blade.agent.controller.AgentCustomerController;
import com.blade.agent.controller.AgentOrderQueryController;
import com.blade.agent.controller.AgentOutletsController;
import com.blade.agent.controller.AgentProductController;
import com.blade.agent.dto.AgentCapabilitiesDTO;
import com.blade.agent.dto.AgentCustomerDTO;
import com.blade.agent.dto.AgentOrderDTO;
import com.blade.agent.dto.AgentOutletsDTO;
import com.blade.agent.dto.AgentProductDTO;
import com.blade.dashboard.dto.DashboardQueryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDataAccessContractTest {

    @Test
    void dataEndpointsUseIndependentScopes() throws Exception {
        assertEquals("hasAuthority('agent:products:read')",
                annotation(AgentProductController.class, "list", com.blade.product.dto.ProductPageDTO.class).value());
        assertEquals("hasAuthority('agent:products:create')",
                annotation(AgentProductController.class, "create",
                        AgentProductDTO.CreateRequest.class, AgentPrincipal.class).value());
        assertEquals("hasAuthority('agent:orders:read')",
                annotation(AgentOrderQueryController.class, "list",
                        com.blade.order.dto.OrderPageDTO.class, String.class).value());
        assertEquals("hasAuthority('agent:customers:read')",
                annotation(AgentCustomerController.class, "list", AgentCustomerDTO.PageRequest.class).value());
        assertEquals("hasAuthority('agent:customers:create')",
                annotation(AgentCustomerController.class, "create",
                        AgentCustomerDTO.CreateRequest.class, AgentPrincipal.class).value());
        assertEquals("hasAuthority('agent:outlets:read')",
                annotation(AgentOutletsController.class, "outlets").value());
    }

    @Test
    void agentOutletViewsExposeStableCodesWithoutInternalIds() {
        Set<String> capabilities = componentNames(AgentCapabilitiesDTO.View.class);
        assertTrue(capabilities.containsAll(Set.of(
                "outletScopeType", "defaultOutletCode", "readableOutlets", "usableOutlets")));
        assertEquals(Set.of("code", "name", "status"), componentNames(AgentCapabilitiesDTO.OutletBrief.class));

        Set<String> outlets = componentNames(AgentOutletsDTO.View.class);
        assertTrue(outlets.containsAll(Set.of("outletScopeType", "defaultOutletCode", "items")));
        assertEquals(Set.of("code", "name", "defaultOutlet"), componentNames(AgentOutletsDTO.OutletItem.class));
        assertFalse(componentNames(AgentOutletsDTO.OutletItem.class).contains("id"));
        assertFalse(componentNames(AgentCapabilitiesDTO.OutletBrief.class).contains("id"));
    }

    @Test
    void agentOrderViewExposesOutletCodeNotInternalId() {
        Set<String> fields = componentNames(AgentOrderDTO.OrderView.class);
        assertTrue(fields.contains("sourceOutletCode"));
        assertTrue(fields.contains("sourceShop"));
        assertFalse(fields.contains("sourceOutletId"));
        assertFalse(fields.contains("tenantId"));
    }

    @Test
    void agentAnalyticsEndpointsAcceptOutletCodeListParameter() throws Exception {
        assertEquals("hasAuthority('agent:analytics:read')",
                AgentAnalyticsController.class.getMethod(
                        "getStyleTrends", DashboardQueryDTO.class, List.class, Integer.class, Integer.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('agent:analytics:read')",
                AgentAnalyticsController.class.getMethod(
                        "getSkuMix", DashboardQueryDTO.class, List.class, String.class, Integer.class)
                        .getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void agentViewsCannotSerializeCostProfitOrCustomerPii() {
        Set<String> productFields = componentNames(AgentProductDTO.ProductView.class);
        Set<String> productSkuFields = componentNames(AgentProductDTO.SkuView.class);
        Set<String> orderFields = componentNames(AgentOrderDTO.OrderView.class);
        Set<String> orderItemFields = componentNames(AgentOrderDTO.OrderItemView.class);

        assertFalse(productFields.contains("costPrice"));
        assertFalse(productSkuFields.contains("costPrice"));
        assertTrue(componentNames(AgentProductDTO.CreateRequest.class).contains("costPrice"));
        assertTrue(componentNames(AgentProductDTO.CreateResult.class).contains("appliedCostPrice"));
        assertFalse(orderFields.contains("customerPhone"));
        assertFalse(orderFields.contains("customerAddress"));
        assertFalse(orderFields.contains("remark"));
        assertFalse(orderFields.contains("grossProfit"));
        assertFalse(orderFields.contains("totalCostAmount"));
        assertFalse(orderItemFields.contains("costPrice"));
        assertFalse(orderItemFields.contains("costAmount"));
        assertFalse(orderItemFields.contains("grossProfit"));
    }

    @Test
    void customerViewMakesSensitiveFieldsExplicitWithoutLeakingInternalAttribution() {
        Set<String> fields = componentNames(AgentCustomerDTO.CustomerView.class);

        assertTrue(fields.containsAll(Set.of("phones", "address", "remark")));
        assertFalse(fields.contains("tenantId"));
        assertFalse(fields.contains("createBy"));
        assertFalse(fields.contains("createdByAgentKeyId"));
    }

    private PreAuthorize annotation(Class<?> controller, String method, Class<?>... parameters) throws Exception {
        return controller.getMethod(method, parameters).getAnnotation(PreAuthorize.class);
    }

    private Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }
}
