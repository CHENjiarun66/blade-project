package com.blade.order.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.order.dto.*;
import com.blade.order.service.impl.OrderServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单管理接口")
public class OrderController {

    @Autowired
    private OrderServiceImpl orderService;

    @GetMapping
    @Operation(summary = "订单列表（分页）")
    public R<PageResult<OrderVO>> list(@Valid OrderPageDTO dto) {
        return R.ok(orderService.pageList(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情")
    public R<OrderVO> getById(@PathVariable Long id) {
        return R.ok(orderService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建订单")
    public R<Long> create(@RequestBody @Valid OrderCreateDTO dto) {
        return R.ok(orderService.create(dto));
    }

    @PostMapping("/confirm-payment")
    @Operation(summary = "付款确认（锁定库存）")
    public R<Void> confirmPayment(@RequestBody @Valid PaymentConfirmDTO dto) {
        orderService.confirmPayment(dto.getOrderId(), dto.getPaidAmount());
        return R.ok();
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "订单发货（预留转出库）")
    public R<Void> deliver(@PathVariable Long id) {
        orderService.deliverOrder(id);
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "订单完成")
    public R<Void> complete(@PathVariable Long id) {
        orderService.completeOrder(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单（释放预留库存）")
    public R<Void> cancel(@PathVariable Long id, @RequestBody @Valid CancelOrderDTO dto) {
        orderService.cancelOrder(id, dto.getReason());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订单")
    public R<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return R.ok();
    }
}
