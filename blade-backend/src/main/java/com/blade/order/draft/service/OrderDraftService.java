package com.blade.order.draft.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.dto.OrderDraftDTO;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.entity.OrderDraftItem;
import com.blade.order.draft.mapper.OrderDraftItemMapper;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderDraftService {
    private final OrderDraftMapper draftMapper;
    private final OrderDraftItemMapper itemMapper;
    private final OrderDraftWriter writer;
    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public PageResult<OrderDraftDTO.Summary> page(int current,
                                                  int size,
                                                  String status,
                                                  String keyword) {
        Page<OrderDraft> page = new Page<>(Math.max(current, 1), Math.max(1, Math.min(size, 100)));
        LambdaQueryWrapper<OrderDraft> query = new LambdaQueryWrapper<OrderDraft>()
                .eq(status != null && !status.isBlank(), OrderDraft::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(OrderDraft::getExternalRefNo, keyword.trim())
                        .or().like(OrderDraft::getSourceOrderNo, keyword.trim())
                        .or().like(OrderDraft::getCustomerName, keyword.trim()))
                .orderByDesc(OrderDraft::getUpdateTime);
        Page<OrderDraft> result = draftMapper.selectPage(page, query);
        List<OrderDraftDTO.Summary> records = result.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return PageResult.of(records, result.getTotal(), result.getSize(), result.getCurrent());
    }

    public OrderDraftDTO.View get(Long id) {
        OrderDraft draft = draftMapper.selectById(id);
        if (draft == null) throw BusinessException.of(404, "草稿不存在");
        return toView(draft, items(id));
    }

    public void update(Long id, OrderDraftDTO.SaveRequest request) {
        writer.update(id, request);
    }

    @Transactional
    public OrderDraftDTO.ConfirmResponse confirm(Long id,
                                                 OrderDraftDTO.ConfirmRequest request) {
        OrderDraft draft = draftMapper.selectForUpdate(id);
        if (draft == null) throw BusinessException.of(404, "草稿不存在");
        if ("CONFIRMED".equals(draft.getStatus())) {
            return confirmedResponse(draft, true);
        }
        if (!"EDITING".equals(draft.getStatus())) {
            throw BusinessException.of(400, "当前草稿状态不能确认");
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
        if (draft.getPaperTotalAmount() != null && draft.getDeposit() != null
                && draft.getDeposit().compareTo(draft.getPaperTotalAmount()) > 0) {
            throw BusinessException.of(400, "定金不能大于纸单总金额");
        }

        OrderCreateDTO create = toOrderCreate(draft, items);
        Long orderId = orderService.create(create);
        applyPaperFinancialSnapshot(orderId, draft);

        draft.setStatus("CONFIRMED");
        draft.setConfirmedOrderId(orderId);
        draft.setConfirmedBy(currentUserId());
        draft.setConfirmedTime(LocalDateTime.now());
        draft.setWarningAcknowledged(request.isAcknowledgeWarnings() ? 1 : 0);
        draftMapper.updateById(draft);
        return confirmedResponse(draft, false);
    }

    private OrderCreateDTO toOrderCreate(OrderDraft draft, List<OrderDraftItem> items) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setCustomerId(draft.getCustomerId());
        dto.setCustomerName(blankToWalkIn(draft.getCustomerName()));
        dto.setCustomerPhone(draft.getCustomerPhone());
        dto.setOrderDate(draft.getOrderDate());
        dto.setSourceDocNo(draft.getSourceOrderNo() == null
                ? draft.getExternalRefNo()
                : draft.getSourceOrderNo());
        dto.setSourceShop(draft.getSourceBatchNo());
        dto.setOrderType("PREORDER");
        dto.setPaymentStatus(0);
        dto.setPaidAmount(BigDecimal.ZERO);
        dto.setNeedDelivery(0);
        dto.setRemark(draft.getNote());
        if (draft.getSourceFileId() != null) {
            dto.setImages(writeJson(List.of(String.valueOf(draft.getSourceFileId()))));
        }
        List<OrderCreateDTO.OrderItemDTO> orderItems = new ArrayList<>();
        for (OrderDraftItem source : items) {
            OrderCreateDTO.OrderItemDTO target = new OrderCreateDTO.OrderItemDTO();
            target.setSkuId(source.getSkuId());
            target.setQuantity(source.getQuantity());
            target.setPrice(source.getSalePrice());
            orderItems.add(target);
        }
        dto.setItems(orderItems);
        return dto;
    }

    private void applyPaperFinancialSnapshot(Long orderId, OrderDraft draft) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw BusinessException.of(500, "正式订单创建后无法读取");
        BigDecimal paperTotal = draft.getPaperTotalAmount();
        if (paperTotal != null) {
            order.setOriginalAmount(paperTotal);
            order.setTotalAmount(paperTotal);
            order.setGrossProfit(paperTotal.subtract(zero(order.getTotalCostAmount())));
        }
        BigDecimal receivable = zero(order.getTotalAmount());
        BigDecimal paid = zero(draft.getDeposit());
        order.setPaidAmount(paid);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            order.setPaymentStatus(0);
            order.setDepositAmount(BigDecimal.ZERO);
        } else if (paid.compareTo(receivable) >= 0 && receivable.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(2);
            order.setDepositAmount(BigDecimal.ZERO);
        } else {
            order.setPaymentStatus(1);
            order.setDepositAmount(paid);
        }
        orderMapper.updateById(order);
    }

    private OrderDraftDTO.Summary toSummary(OrderDraft draft) {
        List<OrderDraftItem> items = items(draft.getId());
        OrderDraftDTO.Summary summary = new OrderDraftDTO.Summary();
        summary.setId(draft.getId());
        summary.setExternalRefNo(draft.getExternalRefNo());
        summary.setSourceOrderNo(draft.getSourceOrderNo());
        summary.setSourceFileId(draft.getSourceFileId());
        summary.setCustomerName(blankToWalkIn(draft.getCustomerName()));
        summary.setOrderDate(draft.getOrderDate());
        summary.setPaperTotalAmount(draft.getPaperTotalAmount());
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
        view.setSourceBatchNo(draft.getSourceBatchNo());
        view.setSourceOrderNo(draft.getSourceOrderNo());
        view.setSourceFileId(draft.getSourceFileId());
        view.setRawCustomerName(draft.getRawCustomerName());
        view.setRawCustomerPhone(draft.getRawCustomerPhone());
        view.setCustomerId(draft.getCustomerId());
        view.setCustomerName(blankToWalkIn(draft.getCustomerName()));
        view.setCustomerPhone(draft.getCustomerPhone());
        view.setRawOrderDate(draft.getRawOrderDate());
        view.setOrderDate(draft.getOrderDate());
        view.setDeliveryDate(draft.getDeliveryDate());
        view.setRawDeposit(draft.getRawDeposit());
        view.setDeposit(draft.getDeposit());
        view.setPaperTotalAmount(draft.getPaperTotalAmount());
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
}
