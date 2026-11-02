package com.blade.catalog;

import com.blade.catalog.dto.*;
import com.blade.catalog.service.*;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.outlet.policy.OutletAccessPolicy;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogHandoffServiceTest {
    CatalogService catalog = mock(CatalogService.class);
    OrderDraftService drafts = mock(OrderDraftService.class);
    OutletAccessPolicy outlets = mock(OutletAccessPolicy.class);
    CatalogHandoffService service = new CatalogHandoffService(catalog, drafts, outlets);

    @BeforeEach void tenant() { TenantContext.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContext.clear(); }
    CatalogHandoffDTO.Request request() {
        var value = new CatalogHandoffDTO.Request();
        value.setRequestId("448e8765-d1f0-4c80-a3d0-513e844ad254"); value.setSourceOutletId(7L);
        var item = new CatalogHandoffDTO.Item(); item.setProductId(11L); item.setColorNote("混色（3色）");
        item.setQuantity(120); item.setUnitPrice(new BigDecimal("28.00")); value.setItems(List.of(item));
        return value;
    }
    void available() {
        var product = new CatalogProductVO(); product.setId(11L); product.setProductCode("JJ-26018"); product.setName("连衣裙");
        when(catalog.pageList(any())).thenReturn(PageResult.of(List.of(product), 1, 100, 1));
        var result = new OrderDraftDTO.BatchResult(); result.setDraftId(85L); result.setStatus("CREATED_WITH_WARNINGS");
        when(drafts.create(any())).thenReturn(result);
    }
    OrderDraftDTO.SaveRequest saved() {
        var captor = ArgumentCaptor.forClass(OrderDraftDTO.SaveRequest.class); verify(drafts).create(captor.capture()); return captor.getValue();
    }
    @Test void styleTotalAndCustomerPriceBecomeUnallocatedDraft() {
        available(); var response = service.handoff(request()); var draft = saved(); var row = draft.getItems().get(0);
        assertEquals(120, row.getQuantity()); assertEquals("混色（3色）", row.getRawColor());
        assertEquals(new BigDecimal("3360.00"), row.getPaperAmount()); assertEquals(new BigDecimal("28.00"), row.getSalePrice());
        assertEquals("JJ-26018", row.getRawProductCode()); assertNull(row.getSkuId()); assertNull(row.getCostPrice());
        assertNull(draft.getPaperTotalAmount()); assertEquals(BigDecimal.ZERO, draft.getPaidAmount());
        assertEquals(7L, draft.getSourceOutletId()); assertEquals(85L, response.getDraftId());
        assertTrue(draft.getWarnings().contains("CATALOG_SELECTION_REVIEW_REQUIRED"));
        assertTrue((draft.getSourceBatchNo()+"_"+draft.getSourceOrderNo()).length() <= 50);
    }
    @Test void changedUnitPriceRecalculatesAndDecimalArithmeticIsExact() {
        available(); var req = request(); req.getItems().get(0).setQuantity(3); req.getItems().get(0).setUnitPrice(new BigDecimal("0.10"));
        service.handoff(req); assertEquals(new BigDecimal("0.30"), saved().getItems().get(0).getPaperAmount());
    }
    @Test void missingPriceIsNotTurnedIntoZeroOrMasterPrice() {
        available(); var req = request(); req.getItems().get(0).setUnitPrice(null); service.handoff(req);
        var row = saved().getItems().get(0); assertNull(row.getSalePrice()); assertNull(row.getPaperAmount()); assertNull(row.getSystemReferencePrice());
    }
    @Test void missingQuantityCanBeReviewedButNotInvented() {
        available(); var req = request(); req.getItems().get(0).setQuantity(null); service.handoff(req);
        assertNull(saved().getItems().get(0).getPaperAmount());
    }
    @Test void tenantIsRequiredBeforeReadingOrWriting() {
        TenantContext.clear(); assertThrows(BusinessException.class, () -> service.handoff(request())); verifyNoInteractions(catalog, drafts, outlets);
    }
    @Test void deniedOutletHasNoProductOrDraftSideEffects() {
        doThrow(BusinessException.of(403,"denied")).when(outlets).requireUsableOutlet(7L);
        assertThrows(BusinessException.class, () -> service.handoff(request())); verifyNoInteractions(catalog, drafts);
    }
    @Test void unavailableOrOtherTenantProductIsRejectedWithoutDraft() {
        when(catalog.pageList(any())).thenReturn(PageResult.of(List.of(), 0, 100, 1));
        assertThrows(BusinessException.class, () -> service.handoff(request())); verifyNoInteractions(drafts);
    }
    @Test void duplicateStylesAreRejectedWithoutDraft() {
        var req = request(); req.setItems(List.of(req.getItems().get(0),req.getItems().get(0)));
        assertThrows(BusinessException.class, () -> service.handoff(req)); verifyNoInteractions(catalog,drafts);
    }
    @Test void amountBeyondExistingDecimalSchemaIsRejectedBeforeAnyDraftWrite() {
        var product = new CatalogProductVO(); product.setId(11L); product.setProductCode("JJ-26018"); product.setName("Test");
        when(catalog.pageList(any())).thenReturn(PageResult.of(List.of(product), 1, 100, 1));
        var req=request(); req.getItems().get(0).setQuantity(999999); req.getItems().get(0).setUnitPrice(new BigDecimal("999999.99"));
        assertThrows(BusinessException.class, () -> service.handoff(req)); verifyNoInteractions(drafts);
    }
    @Test void retryReturnsExistingDraftRatherThanCreatingAnOrder() {
        available(); var result = new OrderDraftDTO.BatchResult(); result.setDraftId(85L); result.setStatus("DUPLICATE");
        when(drafts.create(any())).thenReturn(result); var response = service.handoff(request()); assertTrue(response.isAlreadyExists()); assertEquals(85L,response.getDraftId());
    }
}
