package com.blade.order.draft.service;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.order.draft.dto.OrderDraftDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AgentOrderDraftService {
    private final OrderDraftWriter writer;

    public OrderDraftDTO.BatchResponse createBatch(OrderDraftDTO.BatchRequest request,
                                                   AgentPrincipal principal) {
        boolean writesCost = request.getOrders().stream().anyMatch(order ->
                order.getFreightCost() != null || (order.getItems() != null && order.getItems().stream()
                        .anyMatch(item -> item.getCostPrice() != null)));
        if (writesCost && !principal.getScopes().contains("orders:cost:write")) {
            throw new AccessDeniedException("缺少 orders:cost:write");
        }
        var results = new ArrayList<OrderDraftDTO.BatchResult>();
        for (OrderDraftDTO.SaveRequest order : request.getOrders()) {
            try {
                results.add(writer.create(order, principal.getKeyId()));
            } catch (RuntimeException ex) {
                OrderDraftDTO.BatchResult result = new OrderDraftDTO.BatchResult();
                result.setExternalRefNo(order.getExternalRefNo());
                result.setStatus("ERROR");
                result.setMessage(ex.getMessage());
                result.setWarnings(new ArrayList<>());
                results.add(result);
            }
        }
        OrderDraftDTO.BatchResponse response = new OrderDraftDTO.BatchResponse();
        response.setResults(results);
        return response;
    }
}
