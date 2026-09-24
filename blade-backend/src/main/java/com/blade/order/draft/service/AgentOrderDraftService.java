package com.blade.order.draft.service;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.service.AgentCatalogService;
import com.blade.common.exception.BusinessException;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.dto.OrderDraftDTO.CatalogCandidate;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentOrderDraftService {
    private final OrderDraftWriter writer;
    private final OutletAccessPolicy outletAccessPolicy;
    private final AgentCatalogService catalogService;

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
                resolveMissingSkus(order);
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
     * Agent 应先通过 catalog 接口选择 SKU，但创建草稿时仍由服务端做一次保守兜底。
     * 只自动接受“精确款号 + 唯一、安全的 SKU”：</n+     * <ul>
     *   <li>纸单没有明确规格：优先 PLACEHOLDER（整款录入），无规格商品才使用 DEFAULT。</li>
     *   <li>纸单明确给出颜色：只有候选唯一时才选择具体 NORMAL SKU。</li>
     *   <li>模糊款号、多个具体规格或其它歧义继续保留待匹配，绝不猜测。</li>
     * </ul>
     */
    private void resolveMissingSkus(OrderDraftDTO.SaveRequest order) {
        if (order.getItems() == null) return;
        for (OrderDraftDTO.Item item : order.getItems()) {
            if (item.getSkuId() != null) continue;
            String productCode = trimToNull(item.getRawProductCode());
            if (productCode == null) continue;

            String colorName = specifiedColor(item.getRawColor());
            List<CatalogCandidate> candidates = catalogService.search(
                    null, productCode, colorName, null, 20);
            item.setMatchCandidates(candidates);

            CatalogCandidate resolved = selectAutomaticCandidate(productCode, colorName, candidates);
            if (resolved == null) {
                item.setMatchStatus(candidates.size() > 1 ? "AMBIGUOUS" : "UNMATCHED");
                continue;
            }
            item.setProductId(resolved.getProductId());
            item.setSkuId(resolved.getSkuId());
            item.setSystemReferencePrice(resolved.getSystemReferencePrice());
            item.setMatchStatus("MATCHED");
        }
    }

    private CatalogCandidate selectAutomaticCandidate(String productCode,
                                                       String colorName,
                                                       List<CatalogCandidate> candidates) {
        String normalizedCode = normalizeCode(productCode);
        List<CatalogCandidate> exact = candidates.stream()
                .filter(candidate -> normalizedCode.equals(normalizeCode(candidate.getProductCode())))
                .toList();
        if (exact.isEmpty()) return null;

        if (colorName == null) {
            List<CatalogCandidate> placeholders = exact.stream()
                    .filter(CatalogCandidate::isPlaceholder)
                    .filter(candidate -> scoreAtLeast(candidate, "1.00"))
                    .toList();
            if (placeholders.size() == 1) return placeholders.get(0);

            List<CatalogCandidate> defaults = exact.stream()
                    .filter(candidate -> "DEFAULT".equalsIgnoreCase(candidate.getSkuType()))
                    .filter(candidate -> scoreAtLeast(candidate, "0.95"))
                    .toList();
            return defaults.size() == 1 ? defaults.get(0) : null;
        }

        List<CatalogCandidate> variants = exact.stream()
                .filter(candidate -> !candidate.isPlaceholder())
                .filter(candidate -> "NORMAL".equalsIgnoreCase(candidate.getSkuType()))
                .filter(candidate -> scoreAtLeast(candidate, "0.99"))
                .toList();
        return variants.size() == 1 ? variants.get(0) : null;
    }

    private boolean scoreAtLeast(CatalogCandidate candidate, String threshold) {
        return candidate.getMatchScore() != null
                && candidate.getMatchScore().compareTo(new java.math.BigDecimal(threshold)) >= 0;
    }

    private String specifiedColor(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("[\\s_/\\-]", "");
        if (normalized.isEmpty()
                || normalized.equals("na")
                || normalized.equals("n/a")
                || normalized.equals("无")
                || normalized.contains("无品名")
                || normalized.contains("无颜色")
                || normalized.contains("未指定")
                || normalized.contains("混色")
                || normalized.contains("unspecified")) {
            return null;
        }
        return trimmed;
    }

    private String normalizeCode(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s#＃_\\-./\\\\]", "")
                .trim();
    }

    /**
     * 写入前整批预校验：任一显式 sourceOutletCode 越权/不可用，或无 code 条目缺少可用默认档口，
     * 均抛 BusinessException 401/403，保证整批零写入。显式 sourceOutletId 由 writer 以普通 400 拒绝。
     */
    private void preValidateOutletScope(OrderDraftDTO.BatchRequest request) {
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        Set<String> codes = new LinkedHashSet<>();
        boolean needsDefault = false;
        for (OrderDraftDTO.SaveRequest order : request.getOrders()) {
            if (order.getSourceOutletId() != null) {
                continue; // Agent 使用内部 ID 属普通输入错误，由 writer 逐项 400
            }
            String code = trimToNull(order.getSourceOutletCode());
            if (code != null) {
                codes.add(code);
            } else {
                needsDefault = true;
            }
        }
        for (String code : codes) {
            outletAccessPolicy.requireUsableOutletByCode(code);
        }
        if (needsDefault) {
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
