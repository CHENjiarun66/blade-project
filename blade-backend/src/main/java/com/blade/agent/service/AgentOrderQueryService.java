package com.blade.agent.service;

import com.blade.agent.dto.AgentOrderDTO;
import com.blade.common.result.PageResult;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentOrderQueryService {
    private final OrderServiceImpl orderService;

    public PageResult<AgentOrderDTO.OrderView> page(OrderPageDTO query) {
        PageResult<OrderVO> source = orderService.pageList(query);
        return new PageResult<>(source.getRecords().stream().map(this::toView).toList(),
                source.getTotal(), source.getSize(), source.getCurrent());
    }

    public AgentOrderDTO.OrderView detail(Long id) {
        return toView(orderService.getById(id));
    }

    private AgentOrderDTO.OrderView toView(OrderVO order) {
        List<AgentOrderDTO.OrderItemView> items = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(item -> new AgentOrderDTO.OrderItemView(item.getId(), item.getSkuId(), item.getSkuCode(),
                        item.getSkuType(), item.getVariantUnresolved(), item.getProductName(), item.getColorName(),
                        item.getSizeName(), item.getPrice(), item.getQuantity(), item.getSubtotal()))
                .toList();
        return new AgentOrderDTO.OrderView(order.getId(), order.getOrderNo(), order.getOrderDate(),
                order.getSourceDocNo(), order.getSourceShop(), order.getOrderType(), order.getOrderTypeName(),
                order.getCustomerId(), order.getCustomerName(), order.getTotalAmount(), order.getGrossReceivedAmount(),
                order.getCashRefundAmount(), order.getSalesReturnAmount(), order.getNetReceivedAmount(),
                order.getWriteOffAmount(), order.getBalanceAmount(), order.getCollectionStatus(),
                order.getFulfillmentStatus(), order.getFulfillmentMode(), order.getSettlementMethod(),
                order.getSettledAt(), order.getFreightAmount(), order.getNeedDelivery(), order.getWarehouseName(),
                order.getSalesmanName(), order.getCreateTime(), order.getUpdateTime(), items);
    }
}
