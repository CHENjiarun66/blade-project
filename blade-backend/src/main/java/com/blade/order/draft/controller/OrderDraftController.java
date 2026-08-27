package com.blade.order.draft.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.service.OrderDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-drafts")
@RequiredArgsConstructor
@Tag(name = "订单草稿")
public class OrderDraftController {
    private final OrderDraftService service;

    @GetMapping
    @Operation(summary = "分页查询订单草稿")
    public R<PageResult<OrderDraftDTO.Summary>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(service.page(current, size, status, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询订单草稿详情")
    public R<OrderDraftDTO.View> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "保存订单草稿")
    public R<Void> update(@PathVariable Long id,
                          @RequestBody @Valid OrderDraftDTO.SaveRequest request) {
        service.update(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认草稿并创建正式订单")
    public R<OrderDraftDTO.ConfirmResponse> confirm(
            @PathVariable Long id,
            @RequestBody OrderDraftDTO.ConfirmRequest request) {
        return R.ok(service.confirm(id, request));
    }
}
