package com.blade.order.draft;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.service.AgentOrderDraftService;
import com.blade.order.draft.service.OrderDraftWriter;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrderDraftCostPermissionTest {
    private final OrderDraftWriter writer = mock(OrderDraftWriter.class);
    private final OutletAccessPolicy outletAccessPolicy = mock(OutletAccessPolicy.class);
    private final AgentOrderDraftService service = new AgentOrderDraftService(writer, outletAccessPolicy);

    @Test
    void rejectsCostFieldsWhenKeyOnlyHasDraftWriteScope() {
        OrderDraftDTO.BatchRequest request = requestWithCosts();

        AccessDeniedException error = assertThrows(AccessDeniedException.class,
                () -> service.createBatch(request, principal("orders:write")));

        assertEquals("缺少 orders:cost:write", error.getMessage());
        verify(writer, never()).create(any(), any());
    }

    @Test
    void acceptsCostFieldsWhenKeyHasBothScopes() {
        OrderDraftDTO.BatchRequest request = requestWithCosts();
        OrderDraftDTO.BatchResult result = new OrderDraftDTO.BatchResult();
        result.setExternalRefNo("agent-cost-1");
        result.setStatus("CREATED");
        when(writer.create(any(), eq(9L))).thenReturn(result);
        OutletAccessScope scope = new OutletAccessScope(1L, OutletAccessScope.ActorType.AGENT, 9L,
                OutletAccessScope.ALL, true, false, List.of(1L), List.of(1L), 1L);
        when(outletAccessPolicy.resolveCurrentScope()).thenReturn(scope);
        when(outletAccessPolicy.requireUsableOutlet(1L)).thenReturn(new SalesOutlet());

        OrderDraftDTO.BatchResponse response = service.createBatch(
                request, principal("orders:write,orders:cost:write"));

        assertEquals(1, response.getResults().size());
        assertEquals("CREATED", response.getResults().get(0).getStatus());
        verify(writer).create(any(), eq(9L));
    }

    private OrderDraftDTO.BatchRequest requestWithCosts() {
        OrderDraftDTO.Item item = new OrderDraftDTO.Item();
        item.setQuantity(1);
        item.setSalePrice(new BigDecimal("20.00"));
        item.setCostPrice(new BigDecimal("12.00"));
        OrderDraftDTO.SaveRequest order = new OrderDraftDTO.SaveRequest();
        order.setExternalRefNo("agent-cost-1");
        order.setSourceBatchNo("42");
        order.setSourceOrderNo("0001");
        order.setFreightCost(new BigDecimal("3.00"));
        order.setItems(List.of(item));
        OrderDraftDTO.BatchRequest request = new OrderDraftDTO.BatchRequest();
        request.setOrders(List.of(order));
        return request;
    }

    private AgentPrincipal principal(String scopes) {
        AgentKey key = new AgentKey();
        key.setId(9L);
        key.setTenantId(1L);
        key.setKeyPrefix("agk_test");
        key.setName("test");
        key.setScopes(scopes);
        return AgentPrincipal.from(key);
    }
}
