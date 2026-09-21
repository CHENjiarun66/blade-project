package com.blade.order.draft;

import com.blade.common.exception.BusinessException;
import com.blade.file.service.FileService;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.OrderDraftWriter;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.product.mapper.ProductSkuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** externalRefNo 重复只允许同一创建主体幂等；不同主体 409 且不泄露 ID。 */
class OrderDraftDuplicateRefTest {

    private final OrderDraftMapper draftMapper = mock(OrderDraftMapper.class);
    private final OrderDraftItemMapper itemMapper = mock(OrderDraftItemMapper.class);
    private final ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final OutletAccessPolicy outletAccessPolicy = mock(OutletAccessPolicy.class);
    private final OrderDraftWriter writer = new OrderDraftWriter(
            draftMapper, itemMapper, skuMapper, fileService, new ObjectMapper(), outletAccessPolicy,
            mock(com.blade.customer.mapper.CustomerMapper.class));

    private OrderDraftDTO.SaveRequest request(String ref) {
        OrderDraftDTO.SaveRequest request = new OrderDraftDTO.SaveRequest();
        request.setExternalRefNo(ref);
        return request;
    }

    private OrderDraft existing(Long id, Long agentKeyId, Long userId) {
        OrderDraft draft = new OrderDraft();
        draft.setId(id);
        draft.setTenantId(1L);
        draft.setCreatedByAgentKeyId(agentKeyId);
        draft.setCreatedByUserId(userId);
        return draft;
    }

    @Test
    void manualSameActorIsIdempotentWithDraftId() {
        when(draftMapper.selectOne(any())).thenReturn(existing(88L, null, 10L));
        OrderDraftDTO.BatchResult result = writer.create(request("ref-1"), null, 10L);
        assertEquals("DUPLICATE", result.getStatus());
        assertEquals(88L, result.getDraftId());
    }

    @Test
    void manualDifferentActorGets409WithoutExistingDraftId() {
        when(draftMapper.selectOne(any())).thenReturn(existing(88L, null, 10L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> writer.create(request("ref-1"), null, 11L));
        assertEquals(409, ex.getCode());
        assertEquals("单据编号冲突", ex.getMessage());
    }

    @Test
    void manualNullCreatorCannotBeClaimedByAnotherActor() {
        when(draftMapper.selectOne(any())).thenReturn(existing(88L, null, null));
        assertThrows(BusinessException.class, () -> writer.create(request("ref-1"), null, 99L));
    }

    @Test
    void agentSameKeyIsIdempotentWithDraftId() {
        when(draftMapper.selectOne(any())).thenReturn(existing(77L, 5L, null));
        OrderDraftDTO.BatchResult result = writer.create(request("ref-2"), 5L, null);
        assertEquals("DUPLICATE", result.getStatus());
        assertEquals(77L, result.getDraftId());
    }

    @Test
    void agentDifferentKeyGets409() {
        when(draftMapper.selectOne(any())).thenReturn(existing(77L, 5L, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> writer.create(request("ref-2"), 6L, null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void agentCannotClaimManualDraft() {
        when(draftMapper.selectOne(any())).thenReturn(existing(77L, null, 10L));
        assertThrows(BusinessException.class, () -> writer.create(request("ref-3"), 5L, null));
    }
}
