package com.blade.order.controller;

import com.blade.common.result.R;
import com.blade.order.dto.OrderDeliveryDTO;
import com.blade.order.dto.OrderDeliveryVO;
import com.blade.order.service.OrderDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order-deliveries")
@Tag(name = "订单出库管理")
public class OrderDeliveryController {

    private final OrderDeliveryService deliveryService;

    @Autowired
    public OrderDeliveryController(OrderDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('btn:order:deliver')")
    @Operation(summary = "创建出库单")
    public R<Long> create(@RequestBody @Valid OrderDeliveryDTO dto) {
        Long id = deliveryService.create(dto);
        return R.ok(id);
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('btn:order:view')")
    @Operation(summary = "根据订单ID查询出库单列表")
    public R<List<OrderDeliveryVO>> getByOrderId(@PathVariable Long orderId) {
        List<OrderDeliveryVO> list = deliveryService.getByOrderId(orderId);
        return R.ok(list);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('btn:order:deliver')")
    @Operation(summary = "确认发货")
    public R<Void> confirmDelivery(@PathVariable Long id) {
        deliveryService.confirmDelivery(id);
        return R.ok();
    }
}
