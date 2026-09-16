package com.blade.order.draft.service;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.order.draft.dto.OrderDraftDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AgentOrderDraftService {
    private final OrderDraftWriter writer;

    public OrderDraftDTO.BatchResponse createBatch(OrderDraftDTO.BatchRequest request,
                                                   AgentPrincipal principal) {
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
