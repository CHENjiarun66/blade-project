package com.blade.order.draft.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.entity.OrderDraftItem;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.order.service.OrderService;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderDraftService {
    public static final String UNBATCHED = "__UNBATCHED__";

    private final OrderDraftMapper draftMapper;
    private final OrderDraftItemMapper itemMapper;
    private final OrderDraftWriter writer;
    private final OrderService orderService;
    private final OrderFinanceSnapshotService snapshotService;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final OutletAccessPolicy outletAccessPolicy;

    public PageResult<OrderDraftDTO.Summary> page(int current,
                                                  int size,
                                                  String status,
                                                  String keyword,
                                                  String sourceBatchNo,
                                                  String entrySource,
                                                  Boolean unresolvedOnly,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  Long sourceOutletId,
                                                  Boolean unassignedOnly) {
        Page<OrderDraft> page = new Page<>(Math.max(current, 1), Math.max(1, Math.min(size, 100)));
        LambdaQueryWrapper<OrderDraft> query = new LambdaQueryWrapper<OrderDraft>()
                .eq(status != null && !status.isBlank(), OrderDraft::getStatus, status)
                .eq(entrySource != null && !entrySource.isBlank(), OrderDraft::getEntrySource, entrySource)
                .ge(startDate != null, OrderDraft::getOrderDate, startDate)
                .le(endDate != null, OrderDraft::getOrderDate, endDate)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(OrderDraft::getExternalRefNo, keyword.trim())
                        .or().like(OrderDraft::getSourceOrderNo, keyword.trim())
                        .or().like(OrderDraft::getCustomerName, keyword.trim()))
                .orderByDesc(OrderDraft::getUpdateTime);
        if (UNBATCHED.equals(sourceBatchNo)) {
            query.and(wrapper -> wrapper.isNull(OrderDraft::getSourceBatchNo)
                    .or().eq(OrderDraft::getSourceBatchNo, ""));
        } else {
            query.eq(sourceBatchNo != null && !sourceBatchNo.isBlank(),
                    OrderDraft::getSourceBatchNo, sourceBatchNo);
        }
        if (Boolean.TRUE.equals(unresolvedOnly)) {
            query.exists("SELECT 1 FROM order_draft_item odi "
                    + "WHERE odi.draft_id = order_draft.id AND odi.deleted = 0 AND odi.sku_id IS NULL");
        }
        // 档口 × 人员维度在分页前应用
        outletAccessPolicy.applyDraftReadScope(query);
        // 显式档口筛选：仅当前可读集合；待归档仅 unassigned；越权 403
        outletAccessPolicy.applyExplicitDraftOutletFilter(query, sourceOutletId, unassignedOnly);
        Page<OrderDraft> result = draftMapper.selectPage(page, query);
        Map<Long, String> codes = outletCodes(result.getRecords().stream()
                .map(OrderDraft::getSourceOutletId).filter(Objects::nonNull).toList());
        List<OrderDraftDTO.Summary> records = result.getRecords().stream()
                .map(draft -> toSummary(draft, codes))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getSize(), result.getCurrent());
    }

    public List<OrderDraftDTO.BatchSummary> batches() {
        LambdaQueryWrapper<OrderDraft> batchQuery = new LambdaQueryWrapper<OrderDraft>()
                .eq(OrderDraft::getStatus, "EDITING")
                .orderByDesc(OrderDraft::getUpdateTime);
        outletAccessPolicy.applyDraftReadScope(batchQuery);
        List<OrderDraft> drafts = draftMapper.selectList(batchQuery);
        Map<String, OrderDraftDTO.BatchSummary> grouped = new LinkedHashMap<>();
        for (OrderDraft draft : drafts) {
            String key = draft.getSourceBatchNo() == null || draft.getSourceBatchNo().isBlank()
                    ? UNBATCHED : draft.getSourceBatchNo().trim();
            OrderDraftDTO.BatchSummary batch = grouped.computeIfAbsent(key, ignored -> {
                OrderDraftDTO.BatchSummary value = new OrderDraftDTO.BatchSummary();
                value.setSourceBatchNo(UNBATCHED.equals(key) ? null : key);
                value.setDraftCount(0);
                return value;
            });
            batch.setDraftCount(batch.getDraftCount() + 1);
            if (batch.getLatestUpdateTime() == null
                    || (draft.getUpdateTime() != null && draft.getUpdateTime().isAfter(batch.getLatestUpdateTime()))) {
                batch.setLatestUpdateTime(draft.getUpdateTime());
            }
        }
        return grouped.values().stream()
                .sorted(Comparator.comparing(OrderDraftDTO.BatchSummary::getLatestUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public OrderDraftDTO.View get(Long id) {
        OrderDraft draft = draftMapper.selectById(id);
        if (draft == null) throw BusinessException.of(404, "草稿不存在");
        outletAccessPolicy.requireDraftAccess(draft);
        return toView(draft, items(id));
    }

    public OrderDraftDTO.BatchResult create(OrderDraftDTO.SaveRequest request) {
        return writer.create(request, null, currentUserId());
    }

    public void update(Long id, OrderDraftDTO.SaveRequest request) {
        writer.update(id, request);
    }

    @Transactional
    public OrderDraftDTO.ConfirmResponse confirm(Long id,
                                                 OrderDraftDTO.ConfirmRequest request) {
        OrderDraft draft = draftMapper.selectForUpdate(id);
        if (draft == null) throw BusinessException.of(404, "草稿不存在");
        // selectForUpdate 之后复核范围，避免 TOCTOU（先租户/读取权限）
        outletAccessPolicy.requireDraftAccess(draft);
        // 已确认幂等返回优先于“需可用档口”的新写校验
        if ("CONFIRMED".equals(draft.getStatus())) {
            return confirmedResponse(draft, true);
        }
        if (draft.getSourceOutletId() == null) {
            // 待归档草稿不得确认出新的空档口正式订单
            throw BusinessException.of(400, "请先归档档口后再确认正式订单");
        }
        // 禁用/无权档口历史可读，但不可确认新写
        outletAccessPolicy.requireUseOutlet(draft.getSourceOutletId());
        if (!"EDITING".equals(draft.getStatus())) {
            throw BusinessException.of(400, "当前草稿状态不能确认");
        }
        if (draft.getSourceBatchNo() == null || draft.getSourceBatchNo().isBlank()) {
            throw BusinessException.of(400, "请填写单据批次");
        }
        if (draft.getSourceOrderNo() == null || draft.getSourceOrderNo().isBlank()) {
            throw BusinessException.of(400, "请填写单据号");
        }
        List<OrderDraftItem> items = items(id);
        if (items.isEmpty()) throw BusinessException.of(400, "草稿没有商品明细");
        for (OrderDraftItem item : items) {
            if (item.getSkuId() == null) {
                throw BusinessException.of(400, "第" + item.getSourceRowNo() + "行尚未选择SKU");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw BusinessException.of(400, "第" + item.getSourceRowNo() + "行数量必须大于0");
            }
            if (item.getSalePrice() == null || item.getSalePrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw BusinessException.of(400, "第" + item.getSourceRowNo() + "行销售单价必须大于0");
            }
        }
        List<String> warnings = readStringList(draft.getWarnings());
        if (!warnings.isEmpty() && !request.isAcknowledgeWarnings()) {
            throw BusinessException.of(400, "草稿仍有警告，请确认后再提交");
        }
        if (!isManual(draft) && draft.getPaperTotalAmount() != null && draft.getDeposit() != null
                && draft.getDeposit().compareTo(draft.getPaperTotalAmount()) > 0) {
            throw BusinessException.of(400, "定金不能大于纸单总金额");
        }

        OrderCreateDTO create = toOrderCreate(draft, items);
        create.setPaidAmount(initialPaidAmount(draft));
        Long orderId = orderService.create(create);
        applyPaperTotalOverride(orderId, draft);

        draft.setStatus("CONFIRMED");
        draft.setConfirmedOrderId(orderId);
        draft.setConfirmedBy(currentUserId());
        draft.setConfirmedTime(LocalDateTime.now());
        draft.setWarningAcknowledged(request.isAcknowledgeWarnings() ? 1 : 0);
        draftMapper.updateById(draft);
        return confirmedResponse(draft, false);
    }

    /**
     * 纸单金额为准：草稿确认后以人工识别/确认的纸单总额覆盖订单价值三字段，
     * 再由统一快照服务重算收款快照（定金已在 create 内写为首笔 RECEIPT）。
     */
    private void applyPaperTotalOverride(Long orderId, OrderDraft draft) {
        if (isManual(draft) || draft.getPaperTotalAmount() == null) {
            return;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw BusinessException.of(500, "正式订单创建后无法读取");
        order.setOriginalAmount(draft.getPaperTotalAmount());
        order.setTotalAmount(draft.getPaperTotalAmount());
        order.setGrossProfit(draft.getPaperTotalAmount().subtract(zero(order.getTotalCostAmount())));
        snapshotService.recalculateAndApply(order);
    }

    private OrderCreateDTO toOrderCreate(OrderDraft draft, List<OrderDraftItem> items) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setCustomerId(draft.getCustomerId());
        dto.setCustomerName(blankToWalkIn(draft.getCustomerName()));
        dto.setCustomerPhone(draft.getCustomerPhone());
        dto.setOrderDate(draft.getOrderDate());
        dto.setSourceDocNo(formalSourceDocNo(draft));
        dto.setSourceOutletId(draft.getSourceOutletId());
        dto.setSourceShop(draft.getSourceShop());
        dto.setOrderType(draft.getOrderType() == null ? "PREORDER" : draft.getOrderType());
        dto.setPaymentStatus(0);
        dto.setPaidAmount(BigDecimal.ZERO);
        dto.setCustomerAddress(draft.getCustomerAddress());
        dto.setFreightAmount(zero(draft.getFreightAmount()));
        dto.setFreightCost(zero(draft.getFreightCost()));
        dto.setNeedDelivery(draft.getNeedDelivery() == null ? 0 : draft.getNeedDelivery());
        dto.setDeliveryAddress(draft.getDeliveryAddress());
        dto.setRemark(draft.getNote());
        List<Long> sourceFileIds = sourceFileIds(draft);
        if (!sourceFileIds.isEmpty()) {
            dto.setImages(writeJson(sourceFileIds.stream().map(String::valueOf).toList()));
        }
        List<OrderCreateDTO.OrderItemDTO> orderItems = new ArrayList<>();
        for (OrderDraftItem source : items) {
            OrderCreateDTO.OrderItemDTO target = new OrderCreateDTO.OrderItemDTO();
            target.setSkuId(source.getSkuId());
            target.setQuantity(source.getQuantity());
            target.setPrice(source.getSalePrice());
            target.setCostPrice(source.getCostPrice());
            orderItems.add(target);
        }
        dto.setItems(orderItems);
        return dto;
    }

    private String formalSourceDocNo(OrderDraft draft) {
        String batchNo = draft.getSourceBatchNo() == null ? null : draft.getSourceBatchNo().trim();
        String orderNo = draft.getSourceOrderNo() == null ? null : draft.getSourceOrderNo().trim();
        if (batchNo != null && !batchNo.isBlank() && orderNo != null && !orderNo.isBlank()) {
            String combined = batchNo + "_" + orderNo;
            if (combined.length() > 50) {
                throw BusinessException.of(400, "单据批次与单据号组合后不能超过50位");
            }
            return combined;
        }
        if (orderNo != null && !orderNo.isBlank()) return orderNo;
        return draft.getExternalRefNo();
    }

    private Map<Long, String> outletCodes(List<Long> outletIds) {
        Map<Long, String> codes = new LinkedHashMap<>();
        for (Long id : new LinkedHashSet<>(outletIds)) {
            com.blade.outlet.entity.SalesOutlet outlet = outletAccessPolicy.findOutlet(id);
            if (outlet != null) codes.put(id, outlet.getOutletCode());
        }
        return codes;
    }

    private OrderDraftDTO.Summary toSummary(OrderDraft draft, Map<Long, String> outletCodes) {
        List<OrderDraftItem> items = items(draft.getId());
        OrderDraftDTO.Summary summary = new OrderDraftDTO.Summary();
        summary.setId(draft.getId());
        summary.setExternalRefNo(draft.getExternalRefNo());
        summary.setEntrySource(draft.getEntrySource());
        summary.setSourceBatchNo(draft.getSourceBatchNo());
        summary.setSourceOrderNo(draft.getSourceOrderNo());
        summary.setSourceOutletId(draft.getSourceOutletId());
        summary.setSourceOutletCode(outletCodes.get(draft.getSourceOutletId()));
        List<Long> sourceFileIds = sourceFileIds(draft);
        summary.setSourceFileId(sourceFileIds.isEmpty() ? null : sourceFileIds.get(0));
        summary.setSourceFileCount(sourceFileIds.size());
        summary.setCustomerName(blankToWalkIn(draft.getCustomerName()));
        summary.setOrderDate(draft.getOrderDate());
        summary.setPaperTotalAmount(displayTotalAmount(draft, items));
        summary.setStatus(draft.getStatus());
        summary.setItemCount(items.size());
        summary.setUnresolvedCount((int) items.stream().filter(item -> item.getSkuId() == null).count());
        summary.setWarningCount(readStringList(draft.getWarnings()).size());
        summary.setUpdateTime(draft.getUpdateTime());
        return summary;
    }

    private OrderDraftDTO.View toView(OrderDraft draft, List<OrderDraftItem> rows) {
        OrderDraftDTO.View view = new OrderDraftDTO.View();
        view.setId(draft.getId());
        view.setExternalRefNo(draft.getExternalRefNo());
        view.setEntrySource(draft.getEntrySource());
        view.setSourceBatchNo(draft.getSourceBatchNo());
        view.setSourceOrderNo(draft.getSourceOrderNo());
        view.setSourceOutletId(draft.getSourceOutletId());
        com.blade.outlet.entity.SalesOutlet viewOutlet = outletAccessPolicy.findOutlet(draft.getSourceOutletId());
        view.setSourceOutletCode(viewOutlet == null ? null : viewOutlet.getOutletCode());
        view.setSourceShop(draft.getSourceShop());
        view.setOrderType(draft.getOrderType());
        List<Long> sourceFileIds = sourceFileIds(draft);
        view.setSourceFileId(sourceFileIds.isEmpty() ? null : sourceFileIds.get(0));
        view.setSourceFileIds(sourceFileIds);
        view.setRawCustomerName(draft.getRawCustomerName());
        view.setRawCustomerPhone(draft.getRawCustomerPhone());
        view.setCustomerId(draft.getCustomerId());
        view.setCustomerName(blankToWalkIn(draft.getCustomerName()));
        view.setCustomerPhone(draft.getCustomerPhone());
        view.setCustomerCountryCode(draft.getCustomerCountryCode());
        view.setCustomerAddress(draft.getCustomerAddress());
        view.setRawOrderDate(draft.getRawOrderDate());
        view.setOrderDate(draft.getOrderDate());
        view.setDeliveryDate(draft.getDeliveryDate());
        view.setRawDeposit(draft.getRawDeposit());
        view.setDeposit(draft.getDeposit());
        view.setPaidAmount(draft.getPaidAmount());
        view.setPaperTotalAmount(draft.getPaperTotalAmount());
        view.setFreightAmount(draft.getFreightAmount());
        view.setFreightCost(draft.getFreightCost());
        view.setNeedDelivery(draft.getNeedDelivery());
        view.setDeliveryAddress(draft.getDeliveryAddress());
        view.setCalculatedTotalAmount(rows.stream()
                .filter(item -> item.getQuantity() != null && item.getSalePrice() != null)
                .map(item -> item.getSalePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        view.setNote(draft.getNote());
        view.setWarnings(readStringList(draft.getWarnings()));
        view.setStatus(draft.getStatus());
        view.setConfirmedOrderId(draft.getConfirmedOrderId());
        view.setCreateTime(draft.getCreateTime());
        view.setUpdateTime(draft.getUpdateTime());
        view.setItems(rows.stream().map(this::toItem).toList());
        return view;
    }

    private OrderDraftDTO.Item toItem(OrderDraftItem row) {
        OrderDraftDTO.Item item = new OrderDraftDTO.Item();
        item.setId(row.getId());
        item.setSourceRowNo(row.getSourceRowNo());
        item.setRawProductCode(row.getRawProductCode());
        item.setRawDescription(row.getRawDescription());
        item.setRawColor(row.getRawColor());
        item.setRawQuantity(row.getRawQuantity());
        item.setRawSalePrice(row.getRawSalePrice());
        item.setRawAmount(row.getRawAmount());
        item.setProductId(row.getProductId());
        item.setSkuId(row.getSkuId());
        item.setQuantity(row.getQuantity());
        item.setSalePrice(row.getSalePrice());
        item.setCostPrice(row.getCostPrice());
        item.setPaperAmount(row.getPaperAmount());
        item.setSystemReferencePrice(row.getSystemReferencePrice());
        item.setMatchStatus(row.getMatchStatus());
        item.setMatchCandidates(readCandidates(row.getMatchCandidates()));
        item.setWarnings(readStringList(row.getWarnings()));
        return item;
    }

    private List<OrderDraftItem> items(Long draftId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OrderDraftItem>()
                .eq(OrderDraftItem::getDraftId, draftId)
                .orderByAsc(OrderDraftItem::getSourceRowNo)
                .orderByAsc(OrderDraftItem::getId));
    }

    private List<Long> sourceFileIds(OrderDraft draft) {
        List<Long> boundIds = fileService.getActiveFileIds("order_draft", draft.getId());
        if (draft.getSourceFileId() == null) return boundIds;
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(draft.getSourceFileId());
        ids.addAll(boundIds);
        return new ArrayList<>(ids);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return List.of("WARNING_DATA_INVALID");
        }
    }

    private List<OrderDraftDTO.CatalogCandidate> readCandidates(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("无法序列化订单图片", ex);
        }
    }

    private OrderDraftDTO.ConfirmResponse confirmedResponse(OrderDraft draft, boolean alreadyConfirmed) {
        OrderDraftDTO.ConfirmResponse response = new OrderDraftDTO.ConfirmResponse();
        response.setDraftId(draft.getId());
        response.setOrderId(draft.getConfirmedOrderId());
        response.setAlreadyConfirmed(alreadyConfirmed);
        return response;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, authentication.getName())
                    .last("LIMIT 1"));
            if (user != null) return user.getId();
        }
        throw BusinessException.of(401, "确认草稿需要登录用户");
    }

    private String blankToWalkIn(String value) {
        return value == null || value.isBlank() ? "散客" : value.trim();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isManual(OrderDraft draft) {
        return "MANUAL".equals(draft.getEntrySource());
    }

    private BigDecimal initialPaidAmount(OrderDraft draft) {
        return zero(isManual(draft) ? draft.getPaidAmount() : draft.getDeposit());
    }

    private BigDecimal displayTotalAmount(OrderDraft draft, List<OrderDraftItem> rows) {
        if (!isManual(draft)) {
            return draft.getPaperTotalAmount();
        }
        BigDecimal itemTotal = rows.stream()
                .filter(item -> item.getQuantity() != null && item.getSalePrice() != null)
                .map(item -> item.getSalePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemTotal.add(zero(draft.getFreightAmount()));
    }
}
