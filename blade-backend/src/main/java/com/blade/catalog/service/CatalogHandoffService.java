package com.blade.catalog.service;

import com.blade.catalog.dto.CatalogHandoffDTO;
import com.blade.catalog.dto.CatalogPageDTO;
import com.blade.catalog.dto.CatalogProductVO;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.service.OrderDraftService;
import com.blade.outlet.policy.OutletAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CatalogHandoffService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private final CatalogService catalogService;
    private final OrderDraftService draftService;
    private final OutletAccessPolicy outletAccessPolicy;

    @Transactional
    public CatalogHandoffDTO.Response handoff(CatalogHandoffDTO.Request request) {
        if (TenantContext.getTenantId() == null) throw BusinessException.of(401, "缺少租户上下文");
        // Check an active, authorised outlet before product lookups and before duplicate returns.
        outletAccessPolicy.requireUsableOutlet(request.getSourceOutletId());
        List<Long> ids = request.getItems().stream().map(CatalogHandoffDTO.Item::getProductId).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw BusinessException.of(400, "同一款号请合并为一行");
        Map<Long, CatalogProductVO> products = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            CatalogPageDTO query = new CatalogPageDTO();
            query.setProductIds(ids.subList(start, Math.min(start + 100, ids.size())));
            query.setSize(100L);
            catalogService.pageList(query).getRecords().forEach(p -> products.put(p.getId(), p));
        }
        OrderDraftDTO.SaveRequest draft = new OrderDraftDTO.SaveRequest();
        String uuid = request.getRequestId().toLowerCase(Locale.ROOT);
        draft.setExternalRefNo("CATALOG-" + uuid);
        draft.setSourceBatchNo("XK-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        draft.setSourceOrderNo("XK-" + uuid.replace("-", "").substring(0, 24));
        draft.setSourceOutletId(request.getSourceOutletId());
        draft.setOrderDate(LocalDate.now());
        draft.setOrderType("PREORDER");
        draft.setPaidAmount(BigDecimal.ZERO);
        draft.setNote("客户选款清单：请店员核对各行颜色说明、整款数量和本次报价后开单。");
        draft.setWarnings(List.of("CATALOG_SELECTION_REVIEW_REQUIRED"));
        List<OrderDraftDTO.Item> rows = new ArrayList<>();
        BigDecimal quotedTotal = BigDecimal.ZERO;
        for (int index = 0; index < request.getItems().size(); index++) {
            CatalogHandoffDTO.Item source = request.getItems().get(index);
            CatalogProductVO product = products.get(source.getProductId());
            if (product == null) throw BusinessException.of(400, "第" + (index + 1) + "款已下架或不可用，请移除后重试");
            String color = source.getColorNote() == null ? null : source.getColorNote().trim();
            OrderDraftDTO.Item row = new OrderDraftDTO.Item();
            row.setSourceRowNo(index + 1);
            row.setProductId(product.getId());
            row.setRawProductCode(product.getProductCode());
            row.setRawDescription(product.getName());
            row.setRawColor(color);
            row.setQuantity(source.getQuantity());
            row.setRawQuantity(source.getQuantity() == null ? null : source.getQuantity().toString());
            BigDecimal price = source.getUnitPrice() == null ? null : source.getUnitPrice().setScale(2);
            row.setSalePrice(price);
            row.setRawSalePrice(price == null ? null : price.toPlainString());
            if (price != null && source.getQuantity() != null) {
                BigDecimal amount = price.multiply(BigDecimal.valueOf(source.getQuantity())).setScale(2);
                quotedTotal = quotedTotal.add(amount);
                if (quotedTotal.compareTo(MAX_AMOUNT) > 0) throw BusinessException.of(400, "已填金额超出当前订单范围，请调整数量或拆分清单");
                row.setPaperAmount(amount);
                row.setRawAmount(amount.toPlainString());
            }
            // A mixed-colour total is not a concrete SKU allocation. Staff selects the fulfilment SKU.
            row.setMatchStatus("UNMATCHED");
            rows.add(row);
        }
        draft.setItems(rows);
        // Existing writer enforces tenant + creator + outlet scope, and preserves staff edits on retry.
        OrderDraftDTO.BatchResult result = draftService.create(draft);
        CatalogHandoffDTO.Response response = new CatalogHandoffDTO.Response();
        response.setDraftId(result.getDraftId());
        response.setReference(draft.getSourceOrderNo());
        response.setAlreadyExists("DUPLICATE".equals(result.getStatus()));
        return response;
    }
}
