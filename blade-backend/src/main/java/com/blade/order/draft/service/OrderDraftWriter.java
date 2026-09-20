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
import com.blade.outlet.policy.OutletAccessPolicy;
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
    private final OutletAccessPolicy outletAccessPolicy;

    @Transactional
    public OrderDraftDTO.BatchResult create(OrderDraftDTO.SaveRequest request, Long agentKeyId) {
        return create(request, agentKeyId, null);
    }

    @Transactional
    public OrderDraftDTO.BatchResult create(OrderDraftDTO.SaveRequest request, Long agentKeyId,
                                            Long createdByUserId) {
        OrderDraft existing = findByExternalRef(request.getExternalRefNo());
        if (existing != null) {
            return resolveDuplicate(existing, request.getExternalRefNo(), agentKeyId, createdByUserId);
        }
        Long tenantId = requiredTenantId();
        Set<String> warnings = collectWarnings(request);
        OrderDraft draft = toDraft(request, tenantId, agentKeyId, createdByUserId, warnings);
        try {
            draftMapper.insert(draft);
        } catch (DuplicateKeyException ex) {
            OrderDraft concurrent = findByExternalRef(request.getExternalRefNo());
            if (concurrent == null) {
                throw BusinessException.of(409, "单据编号冲突");
            }
            return resolveDuplicate(concurrent, request.getExternalRefNo(), agentKeyId, createdByUserId);
        }
        insertItems(draft.getId(), tenantId, request.getItems(), warnings);
        bindSourceFiles(draft.getId(), request);
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
        // selectForUpdate 之后复核范围，避免 TOCTOU
        outletAccessPolicy.requireDraftAccess(draft);
        if (!"EDITING".equals(draft.getStatus())) throw BusinessException.of(400, "只有编辑中的草稿可以修改");
        if (!draft.getExternalRefNo().equals(request.getExternalRefNo())) {
            throw BusinessException.of(400, "externalRefNo创建后不能修改");
        }
        Set<String> warnings = collectWarnings(request);
        applyHeader(draft, request, warnings);
        draftMapper.updateById(draft);
        // MyBatis-Plus 默认忽略 null 字段，显式同步兼容首图字段，确保清空全部图片时不会回显旧值。
        draftMapper.update(null, Wrappers.<OrderDraft>lambdaUpdate()
                .eq(OrderDraft::getId, id)
                .set(OrderDraft::getSourceFileId, draft.getSourceFileId()));
        itemMapper.delete(Wrappers.<OrderDraftItem>lambdaQuery().eq(OrderDraftItem::getDraftId, id));
        insertItems(id, requiredTenantId(), request.getItems(), warnings);
        draft.setWarnings(writeJson(warnings));
        draftMapper.updateById(draft);
        bindSourceFiles(id, request);
    }

    private OrderDraft toDraft(OrderDraftDTO.SaveRequest request,
                               Long tenantId,
                               Long agentKeyId,
                               Long createdByUserId,
                               Set<String> warnings) {
        OrderDraft draft = new OrderDraft();
        draft.setTenantId(tenantId);
        draft.setExternalRefNo(request.getExternalRefNo().trim());
        draft.setEntrySource(agentKeyId == null ? "MANUAL" : "AGENT");
        draft.setStatus("EDITING");
        draft.setCreatedByAgentKeyId(agentKeyId);
        if (agentKeyId == null) {
            // 手工草稿记录当前用户；Agent 草稿保留 key id，不回填历史
            draft.setCreatedByUserId(createdByUserId);
        }
        draft.setWarningAcknowledged(0);
        applyHeader(draft, request, warnings);
        return draft;
    }

    private void applyHeader(OrderDraft draft,
                             OrderDraftDTO.SaveRequest request,
                             Set<String> warnings) {
        draft.setSourceBatchNo(trim(request.getSourceBatchNo()));
        draft.setSourceOrderNo(trim(request.getSourceOrderNo()));
        draft.setSourceShop(trim(request.getSourceShop()));
        draft.setOrderType(trim(request.getOrderType()));
        List<Long> sourceFileIds = normalizedSourceFileIds(request);
        draft.setSourceFileId(sourceFileIds.isEmpty() ? null : sourceFileIds.get(0));
        draft.setRawCustomerName(trim(request.getRawCustomerName()));
        draft.setRawCustomerPhone(trim(request.getRawCustomerPhone()));
        draft.setCustomerId(request.getCustomerId());
        draft.setCustomerName(trim(request.getCustomerName()) == null ? "散客" : request.getCustomerName().trim());
        draft.setCustomerPhone(trim(request.getCustomerPhone()));
        draft.setCustomerCountryCode(trim(request.getCustomerCountryCode()));
        draft.setCustomerAddress(trim(request.getCustomerAddress()));
        draft.setRawOrderDate(trim(request.getRawOrderDate()));
        draft.setOrderDate(request.getOrderDate());
        draft.setDeliveryDate(request.getDeliveryDate());
        draft.setRawDeposit(trim(request.getRawDeposit()));
        draft.setDeposit(request.getDeposit());
        draft.setPaidAmount(request.getPaidAmount());
        draft.setPaperTotalAmount(request.getPaperTotalAmount());
        draft.setFreightAmount(request.getFreightAmount());
        draft.setFreightCost(request.getFreightCost());
        draft.setNeedDelivery(request.getNeedDelivery());
        draft.setDeliveryAddress(trim(request.getDeliveryAddress()));
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
            item.setCostPrice(source.getCostPrice());
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

    private void bindSourceFiles(Long draftId, OrderDraftDTO.SaveRequest request) {
        List<Long> sourceFileIds = normalizedSourceFileIds(request);
        for (Long fileId : sourceFileIds) {
            var file = fileService.getActiveFile(fileId);
            boolean image = "IMAGE".equals(file.getFileType())
                    || (file.getContentType() != null && file.getContentType().startsWith("image/"));
            if (!image) {
                throw BusinessException.of(400, "纸单原图只支持图片文件");
            }
        }
        fileService.syncFiles("order_draft", draftId, sourceFileIds);
    }

    private List<Long> normalizedSourceFileIds(OrderDraftDTO.SaveRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (request.getSourceFileId() != null) {
            ids.add(request.getSourceFileId());
        }
        if (request.getSourceFileIds() != null) {
            request.getSourceFileIds().stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        if (ids.size() > 10) {
            throw BusinessException.of(400, "每张草稿最多上传10张纸单原图");
        }
        return new ArrayList<>(ids);
    }

    private Set<String> collectWarnings(OrderDraftDTO.SaveRequest request) {
        Set<String> warnings = new LinkedHashSet<>();
        if (request.getWarnings() != null) {
            request.getWarnings().stream()
                    .filter(warning -> !isCalculatedWarning(warning))
                    .forEach(warnings::add);
        }
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

    private boolean isCalculatedWarning(String warning) {
        if (warning == null) return false;
        return "ORDER_DATE_UNPARSED".equals(warning)
                || "DEPOSIT_UNPARSED".equals(warning)
                || "ORDER_TOTAL_MISMATCH".equals(warning)
                || warning.matches("ITEM_\\d+_(SKU_UNMATCHED|SKU_INVALID|QUANTITY_MISSING|SALE_PRICE_MISSING|AMOUNT_MISMATCH)");
    }

    private OrderDraft findByExternalRef(String externalRefNo) {
        if (trim(externalRefNo) == null) return null;
        return draftMapper.selectOne(new LambdaQueryWrapper<OrderDraft>()
                .eq(OrderDraft::getExternalRefNo, externalRefNo.trim())
                .last("LIMIT 1"));
    }

    /**
     * externalRefNo 重复：仅同一创建主体可幂等返回；不同主体 409 且不暴露已有 ID/状态/内容。
     * manual(null creator) 不得被其他主体认领。
     */
    private OrderDraftDTO.BatchResult resolveDuplicate(OrderDraft existing, String externalRefNo,
                                                       Long agentKeyId, Long createdByUserId) {
        boolean sameActor;
        if (agentKeyId != null) {
            sameActor = agentKeyId.equals(existing.getCreatedByAgentKeyId());
        } else {
            sameActor = createdByUserId != null && createdByUserId.equals(existing.getCreatedByUserId());
        }
        if (!sameActor) {
            throw BusinessException.of(409, "单据编号冲突");
        }
        // 同主体仍需满足当前档口/人员读取策略
        outletAccessPolicy.requireDraftAccess(existing);
        return duplicate(externalRefNo, existing.getId());
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
