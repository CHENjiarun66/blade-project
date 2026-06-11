package com.blade.order.controller;

import com.alibaba.excel.EasyExcel;
import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.order.dto.*;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.service.OrderDeliveryPlanService;
import com.blade.order.service.impl.OrderServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PutMapping("/{id}")
    @Operation(summary = "更新订单基础信息")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid OrderUpdateDTO dto) {
        dto.setId(id);
        orderService.update(dto);
        return R.ok();
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

    @PostMapping("/{id}/add-payment")
    @Operation(summary = "追加收款（不改变订单状态）")
    public R<Void> addPayment(@PathVariable Long id, @RequestBody @Valid AddPaymentDTO dto) {
        orderService.addPayment(id, dto.getAdditionalAmount());
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

    // ==================== 配货计划接口 ====================

    @PostMapping("/{id}/delivery-plan")
    @Operation(summary = "创建配货计划（从订单明细生成）")
    public R<List<DeliveryPlanVO>> createDeliveryPlan(@PathVariable Long id) {
        return R.ok(deliveryPlanService.createDeliveryPlan(id));
    }

    @PutMapping("/{id}/delivery-plan")
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
    @Operation(summary = "删除配货计划（取消配货）")
    public R<Void> deleteDeliveryPlan(@PathVariable Long id) {
        deliveryPlanService.deleteDeliveryPlan(id);
        return R.ok();
    }

    @PostMapping("/{id}/adjustment")
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
    @Operation(summary = "确认调整方案")
    public R<Void> confirmAdjustment(@PathVariable Long id) {
        deliveryPlanService.confirmAdjustment(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel-adjustment")
    @Operation(summary = "取消调整")
    public R<Void> cancelAdjustment(@PathVariable Long id) {
        deliveryPlanService.cancelAdjustment(id);
        return R.ok();
    }

    @GetMapping("/export")
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
