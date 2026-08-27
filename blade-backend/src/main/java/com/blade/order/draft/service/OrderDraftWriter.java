package com.blade.order.draft.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.entity.OrderDraftItem;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductSkuMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderDraftWriter {
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private final OrderDraftMapper draftMapper;
    private final OrderDraftItemMapper itemMapper;
    private final ProductSkuMapper skuMapper;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderDraftDTO.BatchResult create(OrderDraftDTO.SaveRequest request, Long agentKeyId) {
        OrderDraft existing = findByExternalRef(request.getExternalRefNo());
        if (existing != null) {
            return duplicate(request.getExternalRefNo(), existing.getId());
        }
        Long tenantId = requiredTenantId();
        Set<String> warnings = collectWarnings(request);
        OrderDraft draft = toDraft(request, tenantId, agentKeyId, warnings);
        try {
            draftMapper.insert(draft);
        } catch (DuplicateKeyException ex) {
            OrderDraft duplicate = findByExternalRef(request.getExternalRefNo());
            return duplicate(request.getExternalRefNo(), duplicate == null ? null : duplicate.getId());
        }
        insertItems(draft.getId(), tenantId, request.getItems(), warnings);
        if (request.getSourceFileId() != null) {
            fileService.getActiveFile(request.getSourceFileId());
            fileService.bindFiles("order_draft", draft.getId(), List.of(request.getSourceFileId()));
        }
        draft.setWarnings(writeJson(warnings));
        draftMapper.updateById(draft);

        OrderDraftDTO.BatchResult result = new OrderDraftDTO.BatchResult();
        result.setExternalRefNo(request.getExternalRefNo());
        result.setStatus(warnings.isEmpty() ? "CREATED" : "CREATED_WITH_WARNINGS");
        result.setDraftId(draft.getId());
        result.setWarnings(new ArrayList<>(warnings));
        return result;
    }

    @Transactional
    public void update(Long id, OrderDraftDTO.SaveRequest request) {
        OrderDraft draft = draftMapper.selectForUpdate(id);
        if (draft == null) throw BusinessException.of(404, "草稿不存在");
        if (!"EDITING".equals(draft.getStatus())) throw BusinessException.of(400, "只有编辑中的草稿可以修改");
        if (!draft.getExternalRefNo().equals(request.getExternalRefNo())) {
            throw BusinessException.of(400, "externalRefNo创建后不能修改");
        }
        Set<String> warnings = collectWarnings(request);
        applyHeader(draft, request, warnings);
        draftMapper.updateById(draft);
        itemMapper.delete(Wrappers.<OrderDraftItem>lambdaQuery().eq(OrderDraftItem::getDraftId, id));
        insertItems(id, requiredTenantId(), request.getItems(), warnings);
        draft.setWarnings(writeJson(warnings));
        draftMapper.updateById(draft);
        if (request.getSourceFileId() != null) {
            fileService.getActiveFile(request.getSourceFileId());
            fileService.bindFiles("order_draft", id, List.of(request.getSourceFileId()));
        }
    }

    private OrderDraft toDraft(OrderDraftDTO.SaveRequest request,
                               Long tenantId,
                               Long agentKeyId,
                               Set<String> warnings) {
        OrderDraft draft = new OrderDraft();
        draft.setTenantId(tenantId);
        draft.setExternalRefNo(request.getExternalRefNo().trim());
        draft.setStatus("EDITING");
        draft.setCreatedByAgentKeyId(agentKeyId);
        draft.setWarningAcknowledged(0);
        applyHeader(draft, request, warnings);
        return draft;
    }

    private void applyHeader(OrderDraft draft,
                             OrderDraftDTO.SaveRequest request,
                             Set<String> warnings) {
        draft.setSourceBatchNo(trim(request.getSourceBatchNo()));
        draft.setSourceOrderNo(trim(request.getSourceOrderNo()));
        draft.setSourceFileId(request.getSourceFileId());
        draft.setRawCustomerName(trim(request.getRawCustomerName()));
        draft.setRawCustomerPhone(trim(request.getRawCustomerPhone()));
        draft.setCustomerId(request.getCustomerId());
        draft.setCustomerName(trim(request.getCustomerName()) == null ? "散客" : request.getCustomerName().trim());
        draft.setCustomerPhone(trim(request.getCustomerPhone()));
        draft.setRawOrderDate(trim(request.getRawOrderDate()));
        draft.setOrderDate(request.getOrderDate());
        draft.setDeliveryDate(request.getDeliveryDate());
        draft.setRawDeposit(trim(request.getRawDeposit()));
        draft.setDeposit(request.getDeposit());
        draft.setPaperTotalAmount(request.getPaperTotalAmount());
        draft.setNote(trim(request.getNote()));
        draft.setWarnings(writeJson(warnings));
    }

    private void insertItems(Long draftId,
                             Long tenantId,
                             List<OrderDraftDTO.Item> requestItems,
                             Set<String> warnings) {
        for (int index = 0; index < requestItems.size(); index++) {
            OrderDraftDTO.Item source = requestItems.get(index);
            int row = source.getSourceRowNo() == null ? index + 1 : source.getSourceRowNo();
            ProductSku sku = null;
            if (source.getSkuId() != null) {
                sku = skuMapper.selectById(source.getSkuId());
                if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
                    warnings.add("ITEM_" + row + "_SKU_INVALID");
                    source.setSkuId(null);
                }
            }

            OrderDraftItem item = new OrderDraftItem();
            item.setTenantId(tenantId);
            item.setDraftId(draftId);
            item.setSourceRowNo(row);
            item.setRawProductCode(trim(source.getRawProductCode()));
            item.setRawDescription(trim(source.getRawDescription()));
            item.setRawColor(trim(source.getRawColor()));
            item.setRawQuantity(trim(source.getRawQuantity()));
            item.setRawSalePrice(trim(source.getRawSalePrice()));
            item.setRawAmount(trim(source.getRawAmount()));
            item.setProductId(sku != null ? sku.getProductId() : source.getProductId());
            item.setSkuId(source.getSkuId());
            item.setQuantity(source.getQuantity());
            item.setSalePrice(source.getSalePrice());
            item.setPaperAmount(source.getPaperAmount());
            item.setSystemReferencePrice(sku != null ? sku.getPrice() : source.getSystemReferencePrice());
            item.setMatchStatus(source.getSkuId() != null
                    ? "MATCHED"
                    : normalizeMatchStatus(source.getMatchStatus()));
            item.setMatchCandidates(writeJson(source.getMatchCandidates()));
            item.setWarnings(writeJson(source.getWarnings()));
            itemMapper.insert(item);
        }
    }

    private Set<String> collectWarnings(OrderDraftDTO.SaveRequest request) {
        Set<String> warnings = new LinkedHashSet<>();
        if (request.getWarnings() != null) warnings.addAll(request.getWarnings());
        if (request.getSourceFileId() == null) warnings.add("SOURCE_IMAGE_MISSING");
        if (request.getOrderDate() == null && trim(request.getRawOrderDate()) != null) {
            warnings.add("ORDER_DATE_UNPARSED");
        }
        if (request.getDeposit() == null && trim(request.getRawDeposit()) != null) {
            warnings.add("DEPOSIT_UNPARSED");
        }
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (int index = 0; index < request.getItems().size(); index++) {
            OrderDraftDTO.Item item = request.getItems().get(index);
            int row = item.getSourceRowNo() == null ? index + 1 : item.getSourceRowNo();
            if (item.getSkuId() == null) warnings.add("ITEM_" + row + "_SKU_UNMATCHED");
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                warnings.add("ITEM_" + row + "_QUANTITY_MISSING");
            }
            if (item.getSalePrice() == null || item.getSalePrice().compareTo(BigDecimal.ZERO) <= 0) {
                warnings.add("ITEM_" + row + "_SALE_PRICE_MISSING");
            }
            if (item.getQuantity() != null && item.getQuantity() > 0 && item.getSalePrice() != null) {
                BigDecimal calculated = item.getSalePrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                calculatedTotal = calculatedTotal.add(calculated);
                if (item.getPaperAmount() != null
                        && calculated.subtract(item.getPaperAmount()).abs().compareTo(CENT) > 0) {
                    warnings.add("ITEM_" + row + "_AMOUNT_MISMATCH");
                }
            }
        }
        if (request.getPaperTotalAmount() != null
                && calculatedTotal.subtract(request.getPaperTotalAmount()).abs().compareTo(CENT) > 0) {
            warnings.add("ORDER_TOTAL_MISMATCH");
        }
        return warnings;
    }

    private OrderDraft findByExternalRef(String externalRefNo) {
        if (trim(externalRefNo) == null) return null;
        return draftMapper.selectOne(new LambdaQueryWrapper<OrderDraft>()
                .eq(OrderDraft::getExternalRefNo, externalRefNo.trim())
                .last("LIMIT 1"));
    }

    private OrderDraftDTO.BatchResult duplicate(String externalRefNo, Long draftId) {
        OrderDraftDTO.BatchResult result = new OrderDraftDTO.BatchResult();
        result.setExternalRefNo(externalRefNo);
        result.setStatus("DUPLICATE");
        result.setDraftId(draftId);
        result.setWarnings(List.of());
        result.setMessage("草稿已存在，未覆盖人工修改");
        return result;
    }

    private String normalizeMatchStatus(String status) {
        if ("AMBIGUOUS".equals(status)) return "AMBIGUOUS";
        return "UNMATCHED";
    }

    private Long requiredTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw BusinessException.of(401, "缺少租户上下文");
        return tenantId;
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("草稿JSON字段格式错误", ex);
        }
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
