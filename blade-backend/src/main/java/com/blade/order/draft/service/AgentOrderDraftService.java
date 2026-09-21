package com.blade.order.draft.service;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.common.exception.BusinessException;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AgentOrderDraftService {
    private final OrderDraftWriter writer;
    private final OutletAccessPolicy outletAccessPolicy;

    /**
     * 批量创建 Agent 草稿。
     *
     * <p>契约：请求级授权（缺 scope、Key 档口范围/可用性、无默认档口）在任何写入前 fail-fast，
     * 抛 401/403 由上层映射为真实 HTTP 状态，**整批零写入**；普通单条业务错误（400/404/409）
     * 保留 per-item {@code ERROR} 并继续，维持批处理能力；未知 RuntimeException/5xx 不吞。</p>
     */
    public OrderDraftDTO.BatchResponse createBatch(OrderDraftDTO.BatchRequest request,
                                                   AgentPrincipal principal) {
        boolean writesCost = request.getOrders().stream().anyMatch(order ->
                order.getFreightCost() != null || (order.getItems() != null && order.getItems().stream()
                        .anyMatch(item -> item.getCostPrice() != null)));
        if (writesCost && !principal.getScopes().contains("orders:cost:write")) {
            throw new AccessDeniedException("缺少 orders:cost:write");
        }

        preValidateOutletScope(request);

        var results = new ArrayList<OrderDraftDTO.BatchResult>();
        for (OrderDraftDTO.SaveRequest order : request.getOrders()) {
            try {
                results.add(writer.create(order, principal.getKeyId()));
            } catch (BusinessException ex) {
                if (ex.getCode() == 401 || ex.getCode() == 403) {
                    // 预校验已覆盖显式 code/默认档口；此处兜底：请求级鉴权失败仍整批拒绝，绝不降级为 item ERROR
                    throw ex;
                }
                results.add(errorResult(order, ex.getMessage()));
            }
            // 其它 RuntimeException（真实 5xx/未知缺陷）继续向上传播，不被吞成 item ERROR
        }
        OrderDraftDTO.BatchResponse response = new OrderDraftDTO.BatchResponse();
        response.setResults(results);
        return response;
    }

    /**
     * 写入前整批预校验：任一显式 sourceOutletCode 越权/不可用，或无 code 条目缺少可用默认档口，
     * 均抛 BusinessException 401/403，保证整批零写入。显式 sourceOutletId 由 writer 以普通 400 拒绝。
     */
    private void preValidateOutletScope(OrderDraftDTO.BatchRequest request) {
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        for (OrderDraftDTO.SaveRequest order : request.getOrders()) {
            if (order.getSourceOutletId() != null) {
                continue; // Agent 使用内部 ID 属普通输入错误，由 writer 逐项 400
            }
            String code = trimToNull(order.getSourceOutletCode());
            if (code != null) {
                outletAccessPolicy.requireUsableOutletByCode(code);
                continue;
            }
            Long defaultOutletId = scope.defaultOutletId();
            if (defaultOutletId == null) {
                throw BusinessException.of(403, "Agent 无可用的默认档口，请传 sourceOutletCode");
            }
            outletAccessPolicy.requireUsableOutlet(defaultOutletId);
        }
    }

    private OrderDraftDTO.BatchResult errorResult(OrderDraftDTO.SaveRequest order, String message) {
        OrderDraftDTO.BatchResult result = new OrderDraftDTO.BatchResult();
        result.setExternalRefNo(order.getExternalRefNo());
        result.setStatus("ERROR");
        result.setMessage(message);
        result.setWarnings(new ArrayList<>());
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
