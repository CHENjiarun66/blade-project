package com.blade.order.draft;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.service.AgentCatalogService;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.dto.OrderDraftDTO.CatalogCandidate;
import com.blade.order.draft.service.AgentOrderDraftService;
import com.blade.order.draft.service.OrderDraftWriter;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrderDraftSkuAutoMatchTest {
    private final OrderDraftWriter writer = mock(OrderDraftWriter.class);
    private final OutletAccessPolicy outletAccessPolicy = mock(OutletAccessPolicy.class);
    private final AgentCatalogService catalogService = mock(AgentCatalogService.class);
    private final AgentOrderDraftService service = new AgentOrderDraftService(
            writer, outletAccessPolicy, catalogService);

    @BeforeEach
    void prepareOutlet() {
        OutletAccessScope scope = new OutletAccessScope(1L, OutletAccessScope.ActorType.AGENT, 9L,
                OutletAccessScope.ALL, true, false, List.of(1L), List.of(1L), 1L);
        when(outletAccessPolicy.resolveCurrentScope()).thenReturn(scope);
        when(outletAccessPolicy.requireUsableOutlet(1L)).thenReturn(new SalesOutlet());
        OrderDraftDTO.BatchResult result = new OrderDraftDTO.BatchResult();
        result.setStatus("CREATED");
        when(writer.create(any(), eq(9L))).thenReturn(result);
    }

    @Test
    void exactSpuWithoutVariantAutomaticallyUsesPlaceholderSku() {
        CatalogCandidate placeholder = candidate(81L, 18L, "618-16#", "PLACEHOLDER", true, "1.00");
        when(catalogService.search(null, "618-16", null, null, 20))
                .thenReturn(List.of(placeholder));

        service.createBatch(request("618-16", null), principal());

        OrderDraftDTO.Item item = capturedItem();
        assertEquals(81L, item.getSkuId());
        assertEquals(18L, item.getProductId());
        assertEquals("MATCHED", item.getMatchStatus());
        assertEquals(new BigDecimal("43.00"), item.getSystemReferencePrice());
    }

    @Test
    void noSpecificationProductAutomaticallyUsesDefaultSku() {
        CatalogCandidate defaultSku = candidate(91L, 19L, "9000", "DEFAULT", false, "0.98");
        when(catalogService.search(null, "9000", null, null, 20))
                .thenReturn(List.of(defaultSku));

        service.createBatch(request("9000", "未指定颜色"), principal());

        assertEquals(91L, capturedItem().getSkuId());
        assertEquals("MATCHED", capturedItem().getMatchStatus());
    }

    @Test
    void multipleConcreteVariantsRemainAmbiguousInsteadOfGuessing() {
        CatalogCandidate small = candidate(101L, 20L, "7000", "NORMAL", false, "0.99");
        CatalogCandidate medium = candidate(102L, 20L, "7000", "NORMAL", false, "0.99");
        when(catalogService.search(null, "7000", "黑色", null, 20))
                .thenReturn(List.of(small, medium));

        service.createBatch(request("7000", "黑色"), principal());

        OrderDraftDTO.Item item = capturedItem();
        assertNull(item.getSkuId());
        assertEquals("AMBIGUOUS", item.getMatchStatus());
        assertEquals(2, item.getMatchCandidates().size());
    }

    private OrderDraftDTO.Item capturedItem() {
        ArgumentCaptor<OrderDraftDTO.SaveRequest> captor = ArgumentCaptor.forClass(OrderDraftDTO.SaveRequest.class);
        verify(writer).create(captor.capture(), eq(9L));
        return captor.getValue().getItems().get(0);
    }

    private OrderDraftDTO.BatchRequest request(String productCode, String rawColor) {
        OrderDraftDTO.Item item = new OrderDraftDTO.Item();
        item.setRawProductCode(productCode);
        item.setRawColor(rawColor);
        item.setQuantity(10);
        item.setSalePrice(new BigDecimal("42.50"));

        OrderDraftDTO.SaveRequest order = new OrderDraftDTO.SaveRequest();
        order.setExternalRefNo("auto-match-" + productCode);
        order.setSourceBatchNo("33");
        order.setSourceOrderNo("0001");
        order.setItems(List.of(item));

        OrderDraftDTO.BatchRequest request = new OrderDraftDTO.BatchRequest();
        request.setOrders(List.of(order));
        return request;
    }

    private CatalogCandidate candidate(Long skuId, Long productId, String productCode,
                                       String skuType, boolean placeholder, String score) {
        CatalogCandidate candidate = new CatalogCandidate();
        candidate.setSkuId(skuId);
        candidate.setProductId(productId);
        candidate.setProductCode(productCode);
        candidate.setSkuType(skuType);
        candidate.setPlaceholder(placeholder);
        candidate.setMatchScore(new BigDecimal(score));
        candidate.setSystemReferencePrice(new BigDecimal("43.00"));
        return candidate;
    }

    private AgentPrincipal principal() {
        AgentKey key = new AgentKey();
        key.setId(9L);
        key.setTenantId(1L);
        key.setKeyPrefix("agk_test");
        key.setName("test");
        key.setScopes("orders:write");
        return AgentPrincipal.from(key);
    }
}
