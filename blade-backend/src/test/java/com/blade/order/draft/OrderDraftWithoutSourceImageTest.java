package com.blade.order.draft;

import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.file.entity.FileStorage;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.OrderDraftWriter;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductSkuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class OrderDraftWithoutSourceImageTest {
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void structuredBatchWithoutSourceImageCreatesCleanDraft() {
        OrderDraftMapper draftMapper = mock(OrderDraftMapper.class);
        OrderDraftItemMapper itemMapper = mock(OrderDraftItemMapper.class);
        ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
        FileService fileService = mock(FileService.class);
        OrderDraftWriter writer = new OrderDraftWriter(
                draftMapper, itemMapper, skuMapper, fileService, new ObjectMapper());

        doAnswer(invocation -> {
            OrderDraft draft = invocation.getArgument(0);
            draft.setId(88L);
            return 1;
        }).when(draftMapper).insert(any(OrderDraft.class));

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(20L);
        sku.setStatus(1);
        sku.setPrice(new BigDecimal("25.00"));
        when(skuMapper.selectById(10L)).thenReturn(sku);
        TenantContext.setTenantId(7L);

        OrderDraftDTO.Item item = new OrderDraftDTO.Item();
        item.setSkuId(10L);
        item.setQuantity(2);
        item.setSalePrice(new BigDecimal("25.00"));
        item.setPaperAmount(new BigDecimal("50.00"));

        OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
        request.setExternalRefNo("batch-001-order-001");
        request.setOrderDate(LocalDate.of(2026, 9, 5));
        request.setPaperTotalAmount(new BigDecimal("50.00"));
        request.setItems(List.of(item));

        OrderDraftDTO.BatchResult result = writer.create(request, 9L);

        assertEquals("CREATED", result.getStatus());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(88L, result.getDraftId());
    }

    @Test
    void structuredBatchBindsMultipleSourceImagesAndKeepsFirstAsLegacyPrimary() {
        OrderDraftMapper draftMapper = mock(OrderDraftMapper.class);
        OrderDraftItemMapper itemMapper = mock(OrderDraftItemMapper.class);
        ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
        FileService fileService = mock(FileService.class);
        OrderDraftWriter writer = new OrderDraftWriter(
                draftMapper, itemMapper, skuMapper, fileService, new ObjectMapper());

        doAnswer(invocation -> {
            OrderDraft draft = invocation.getArgument(0);
            draft.setId(91L);
            return 1;
        }).when(draftMapper).insert(any(OrderDraft.class));
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(20L);
        sku.setStatus(1);
        sku.setPrice(new BigDecimal("25.00"));
        when(skuMapper.selectById(10L)).thenReturn(sku);
        FileStorage image = new FileStorage();
        image.setFileType("IMAGE");
        when(fileService.getActiveFile(101L)).thenReturn(image);
        when(fileService.getActiveFile(102L)).thenReturn(image);
        TenantContext.setTenantId(7L);

        OrderDraftDTO.Item item = new OrderDraftDTO.Item();
        item.setSkuId(10L);
        item.setQuantity(2);
        item.setSalePrice(new BigDecimal("25.00"));
        item.setPaperAmount(new BigDecimal("50.00"));
        OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
        request.setExternalRefNo("batch-001-order-with-images");
        request.setOrderDate(LocalDate.of(2026, 9, 11));
        request.setPaperTotalAmount(new BigDecimal("50.00"));
        request.setSourceFileIds(List.of(101L, 102L));
        request.setItems(List.of(item));

        writer.create(request, 9L);

        ArgumentCaptor<OrderDraft> draftCaptor = ArgumentCaptor.forClass(OrderDraft.class);
        verify(draftMapper).insert(draftCaptor.capture());
        assertEquals(101L, draftCaptor.getValue().getSourceFileId());
        verify(fileService).syncFiles(eq("order_draft"), eq(91L), eq(List.of(101L, 102L)));
    }
}
