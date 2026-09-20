package com.blade.order.draft;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.file.service.FileService;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.order.draft.service.OrderDraftWriter;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.order.service.OrderService;
import com.blade.system.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDraftDeleteTest {

    @Mock private OrderDraftMapper draftMapper;
    @Mock private OrderDraftItemMapper itemMapper;
    @Mock private OrderDraftWriter writer;
    @Mock private OrderService orderService;
    @Mock private OrderFinanceSnapshotService snapshotService;
    @Mock private OrderMapper orderMapper;
    @Mock private UserMapper userMapper;
    @Mock private FileService fileService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private OrderDraftService service;

    @Test
    void deleteEditingDraftUnbindsFilesAndSoftDeletesDraftWithItems() {
        OrderDraft draft = editingDraft(11L);
        when(draftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(draft));

        service.delete(11L);

        verify(fileService).syncFiles("order_draft", 11L, List.of());
        verify(itemMapper).delete(any(LambdaQueryWrapper.class));
        verify(draftMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void batchDeleteValidatesEveryDraftBeforeChangingAnything() {
        OrderDraft editing = editingDraft(11L);
        OrderDraft confirmed = editingDraft(12L);
        confirmed.setStatus("CONFIRMED");
        when(draftMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(editing, confirmed));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteBatch(List.of(11L, 12L)));

        assertEquals(400, error.getCode());
        verify(fileService, never()).syncFiles(any(), any(), any());
        verify(itemMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(draftMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void batchDeleteRejectsMissingDraftWithoutPartialDelete() {
        when(draftMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(editingDraft(11L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteBatch(List.of(11L, 99L)));

        assertEquals(404, error.getCode());
        verify(fileService, never()).syncFiles(any(), any(), any());
        verify(itemMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(draftMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    private OrderDraft editingDraft(Long id) {
        OrderDraft draft = new OrderDraft();
        draft.setId(id);
        draft.setStatus("EDITING");
        return draft;
    }
}
