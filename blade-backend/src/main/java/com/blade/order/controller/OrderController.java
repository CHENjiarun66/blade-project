package com.blade.order.controller;

import com.alibaba.excel.EasyExcel;
import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.order.dto.*;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderDeliveryPlanService;
import com.blade.order.service.impl.OrderServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单管理接口")
public class OrderController {

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private OrderDeliveryPlanService deliveryPlanService;

    @Autowired
    private OrderActionService actionService;

    @Autowired
    private com.blade.order.service.OrderPlaceholderSplitService placeholderSplitService;

    @GetMapping
    @Operation(summary = "订单列表（分页）")
    public R<PageResult<OrderVO>> list(@Valid OrderPageDTO dto) {
        return R.ok(orderService.pageList(dto));
    }

    @PostMapping("/{id}/items/{itemId}/split")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "占位明细拆分到真实SKU（数量/销售额/成本守恒）")
    public R<List<com.blade.order.entity.OrderItem>> splitPlaceholderItem(
            @PathVariable Long id, @PathVariable Long itemId, @RequestBody @Valid OrderItemSplitDTO dto) {
        List<com.blade.order.service.OrderPlaceholderSplitService.SplitTarget> targets =
                dto.getTargets().stream().map(t -> {
                    com.blade.order.service.OrderPlaceholderSplitService.SplitTarget target =
                            new com.blade.order.service.OrderPlaceholderSplitService.SplitTarget();
                    target.setSkuId(t.getSkuId());
                    target.setQuantity(t.getQuantity());
                    return target;
                }).toList();
        return R.ok(placeholderSplitService.splitPlaceholderItem(id, itemId, targets, dto.getReason()));
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

    @PutMapping("/{id}")
    @Operation(summary = "更新订单基础信息")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid OrderUpdateDTO dto) {
        dto.setId(id);
        orderService.update(dto);
        return R.ok();
    }

    @PostMapping("/confirm-payment")
    @PreAuthorize("hasAuthority('btn:order:recordPayment')")
    @Operation(summary = "付款确认")
    public R<Void> confirmPayment(@RequestBody @Valid PaymentConfirmDTO dto) {
        orderService.confirmPayment(dto.getOrderId(), dto.getPaidAmount());
        return R.ok();
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('btn:order:deliver')")
    @Operation(summary = "订单发货")
    public R<Void> deliver(@PathVariable Long id) {
        orderService.deliverOrder(id);
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('btn:order:deliver')")
    @Operation(summary = "订单完成")
    public R<Void> complete(@PathVariable Long id) {
        orderService.completeOrder(id);
        return R.ok();
    }

    @PostMapping("/{id}/add-payment")
    @PreAuthorize("hasAnyAuthority('btn:order:recordPayment', 'btn:order:writeOff')")
    @Operation(summary = "追加收款 / 标记结清")
    public R<Void> addPayment(@PathVariable Long id, @RequestBody @Valid AddPaymentDTO dto) {
        orderService.addPayment(id, dto);
        return R.ok();
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('btn:order:refund')")
    @Operation(summary = "现金退款（与销售退货无关）")
    public R<Void> refund(@PathVariable Long id, @RequestBody @Valid OrderRefundDTO dto) {
        actionService.refundPayment(id, dto.getAmount(), dto.getReason(), dto.getIdempotencyKey(), "PC");
        return R.ok();
    }

    @PostMapping("/{id}/reverse-record")
    @PreAuthorize("hasAuthority('btn:order:reverse')")
    @Operation(summary = "冲销财务流水（只追加 REVERSAL，不改历史）")
    public R<Void> reverseRecord(@PathVariable Long id, @RequestBody @Valid OrderReverseDTO dto) {
        actionService.reverseFinancialRecord(dto.getRecordId(), dto.getReason(), dto.getIdempotencyKey(), "PC");
        return R.ok();
    }

    @PostMapping("/{id}/fulfillment-mode")
    @PreAuthorize("hasAuthority('btn:order:chooseFulfillment')")
    @Operation(summary = "选择履约方式（已结清后：关联库存 / 仅记录订单）")
    public R<Void> chooseFulfillmentMode(@PathVariable Long id, @RequestBody @Valid OrderFulfillmentModeDTO dto) {
        actionService.chooseFulfillmentMode(id, FulfillmentMode.valueOf(dto.getMode()), "PC");
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('btn:order:cancel')")
    @Operation(summary = "取消订单")
    public R<Void> cancel(@PathVariable Long id, @RequestBody @Valid CancelOrderDTO dto) {
        orderService.cancelOrder(id, dto.getReason());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('btn:order:delete')")
    @Operation(summary = "删除订单（仅未产生事实的确认订单，软删除可恢复）")
    public R<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return R.ok();
    }

    // ==================== 配货计划接口 ====================

    @PostMapping("/{id}/delivery-plan")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "创建配货计划（从订单明细生成，仅关联库存订单）")
    public R<List<DeliveryPlanVO>> createDeliveryPlan(@PathVariable Long id) {
        return R.ok(deliveryPlanService.createDeliveryPlan(id));
    }

    @PutMapping("/{id}/delivery-plan")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "更新配货计划")
    public R<List<DeliveryPlanVO>> updateDeliveryPlan(@PathVariable Long id, @RequestBody @Valid DeliveryPlanDTO dto) {
        return R.ok(deliveryPlanService.updateDeliveryPlan(id, dto));
    }

    @GetMapping("/{id}/delivery-plan")
    @Operation(summary = "获取配货计划")
    public R<List<DeliveryPlanVO>> getDeliveryPlan(@PathVariable Long id) {
        return R.ok(deliveryPlanService.getDeliveryPlanByOrderId(id));
    }

    @DeleteMapping("/{id}/delivery-plan")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "删除配货计划（取消配货，回到待配货）")
    public R<Void> deleteDeliveryPlan(@PathVariable Long id) {
        deliveryPlanService.deleteDeliveryPlan(id);
        return R.ok();
    }

    @PostMapping("/{id}/adjustment")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "记录订单调整")
    public R<Void> recordAdjustment(@PathVariable Long id, @RequestBody @Valid AdjustmentLogDTO dto) {
        dto.setOrderId(id);
        deliveryPlanService.recordAdjustment(dto);
        return R.ok();
    }

    @GetMapping("/{id}/adjustment")
    @Operation(summary = "获取订单调整记录")
    public R<List<AdjustmentLogDTO>> getAdjustmentLogs(@PathVariable Long id) {
        return R.ok(deliveryPlanService.getAdjustmentLogs(id));
    }

    @PostMapping("/{id}/confirm-adjustment")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "确认调整方案")
    public R<Void> confirmAdjustment(@PathVariable Long id) {
        deliveryPlanService.confirmAdjustment(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel-adjustment")
    @PreAuthorize("hasAuthority('btn:order:allocate')")
    @Operation(summary = "取消调整")
    public R<Void> cancelAdjustment(@PathVariable Long id) {
        deliveryPlanService.cancelAdjustment(id);
        return R.ok();
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('btn:order:export')")
    @Operation(summary = "导出订单列表Excel")
    public void exportOrders(OrderPageDTO dto, HttpServletResponse response) throws IOException {
        List<OrderExportDTO> data = orderService.exportOrders(dto);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("订单导出_" + System.currentTimeMillis(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), OrderExportDTO.class)
                .sheet("订单列表")
                .doWrite(data);
    }
}
