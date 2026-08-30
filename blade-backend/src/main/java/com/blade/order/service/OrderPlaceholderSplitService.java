package com.blade.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderAdjustmentLog;
import com.blade.order.entity.OrderItem;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSku;
import com.blade.product.entity.ProductSize;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 占位 SKU 拆分服务（BE-610～612）：
 * 把一条 PLACEHOLDER 订单明细原子转移到多个真实 SKU 明细，
 * 保持总数量、销售额（subtotal 合计）、成本快照（costAmount 合计）与毛利合计守恒，
 * 并写入拆分来源审计（OrderAdjustmentLog，adjustmentType=PLACEHOLDER_SPLIT）。
 */
@Service
public class OrderPlaceholderSplitService {

    public static final String SPLIT_ADJUSTMENT_TYPE = "PLACEHOLDER_SPLIT";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderAdjustmentLogMapper adjustmentLogMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final ProductColorMapper productColorMapper;
    private final ProductSizeMapper productSizeMapper;

    public OrderPlaceholderSplitService(OrderMapper orderMapper,
                                        OrderItemMapper orderItemMapper,
                                        OrderAdjustmentLogMapper adjustmentLogMapper,
                                        ProductSkuMapper productSkuMapper,
                                        ProductMapper productMapper,
                                        ProductColorMapper productColorMapper,
                                        ProductSizeMapper productSizeMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.adjustmentLogMapper = adjustmentLogMapper;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.productColorMapper = productColorMapper;
        this.productSizeMapper = productSizeMapper;
    }

    /**
     * 把一条占位明细拆分到多个真实 SKU。同事务原子完成：校验守恒 → 写审计 → 删占位行 → 插入新行。
     */
    @Transactional
    public List<OrderItem> splitPlaceholderItem(Long orderId, Long itemId, List<SplitTarget> targets, String reason) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }
        if (targets == null || targets.isEmpty()) {
            throw BusinessException.of(400, "拆分目标不能为空");
        }

        Order order = orderMapper.selectByIdForUpdate(orderId, tenantId);
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        requireSplittable(order);

        OrderItem placeholder = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getId, itemId)
                .eq(OrderItem::getOrderId, orderId)
                .eq(OrderItem::getTenantId, tenantId));
        if (placeholder == null) {
            throw BusinessException.of(404, "订单明细不存在");
        }
        ProductSku sourceSku = placeholder.getSkuId() != null
                ? productSkuMapper.selectById(placeholder.getSkuId()) : null;
        if (sourceSku == null || !"PLACEHOLDER".equals(sourceSku.getSkuType())) {
            throw BusinessException.of(400, "只有未指定颜色/尺码的占位明细可以拆分");
        }

        // 数量守恒校验
        int targetTotal = targets.stream()
                .mapToInt(t -> t.getQuantity() == null ? 0 : t.getQuantity())
                .sum();
        if (placeholder.getQuantity() == null || targetTotal != placeholder.getQuantity()) {
            throw BusinessException.of(400, String.format(
                    "拆分数量合计必须等于占位数量 %d", placeholder.getQuantity()));
        }

        // 目标 SKU 校验：必须是真实规格 SKU
        Map<Long, ProductSku> targetSkuMap = productSkuMapper.selectBatchIds(
                        targets.stream().map(SplitTarget::getSkuId).toList()).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));
        for (SplitTarget target : targets) {
            ProductSku sku = targetSkuMap.get(target.getSkuId());
            if (sku == null || !"NORMAL".equals(sku.getSkuType()) && !"DEFAULT".equals(sku.getSkuType())) {
                throw BusinessException.of(400, "拆分目标必须是真实规格 SKU：" + target.getSkuId());
            }
        }

        BigDecimal subtotalBefore = placeholder.getSubtotal() == null ? BigDecimal.ZERO : placeholder.getSubtotal();
        BigDecimal costBefore = placeholder.getCostAmount() == null ? BigDecimal.ZERO : placeholder.getCostAmount();

        // 删除占位行
        orderItemMapper.deleteById(itemId);

        // 插入真实 SKU 行（单价/成本沿用占位行，保证销售额与成本快照守恒）
        List<OrderItem> created = new ArrayList<>();
        int rowNo = 0;
        for (SplitTarget target : targets) {
            rowNo++;
            ProductSku sku = targetSkuMap.get(target.getSkuId());
            BigDecimal quantity = BigDecimal.valueOf(target.getQuantity());
            OrderItem item = new OrderItem();
            item.setOrderId(orderId);
            item.setSkuId(sku.getId());
            item.setWarehouseId(placeholder.getWarehouseId());
            item.setSkuCode(sku.getSkuCode() != null ? sku.getSkuCode() : "");
            item.setProductName(resolveProductName(sku));
            item.setColorName(resolveColorName(sku));
            item.setSizeName(resolveSizeName(sku));
            item.setPrice(placeholder.getPrice());
            item.setCostPrice(placeholder.getCostPrice());
            item.setQuantity(target.getQuantity());
            item.setSubtotal(placeholder.getPrice() == null
                    ? null : placeholder.getPrice().multiply(quantity));
            item.setCostAmount(placeholder.getCostPrice() == null
                    ? null : placeholder.getCostPrice().multiply(quantity));
            if (item.getSubtotal() != null && item.getCostAmount() != null) {
                item.setGrossProfit(item.getSubtotal().subtract(item.getCostAmount()));
            }
            item.setTenantId(tenantId);
            orderItemMapper.insert(item);
            created.add(item);

            // 拆分来源审计
            OrderAdjustmentLog log = new OrderAdjustmentLog();
            log.setOrderId(orderId);
            log.setAdjustmentType(SPLIT_ADJUSTMENT_TYPE + (targets.size() > 1 ? "#" + rowNo : ""));
            log.setOriginalSkuId(placeholder.getSkuId());
            log.setOriginalQuantity(placeholder.getQuantity());
            log.setNewSkuId(sku.getId());
            log.setNewQuantity(target.getQuantity());
            log.setReason(reason != null ? reason : "占位明细拆分");
            log.setOperatorId(currentUserId());
            log.setOperatorName(currentUserName());
            log.setTenantId(tenantId);
            adjustmentLogMapper.insert(log);
        }

        // 守恒断言（防御式，失败即回滚）
        BigDecimal subtotalAfter = created.stream()
                .map(i -> i.getSubtotal() == null ? BigDecimal.ZERO : i.getSubtotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costAfter = created.stream()
                .map(i -> i.getCostAmount() == null ? BigDecimal.ZERO : i.getCostAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotalAfter.compareTo(subtotalBefore) != 0 || costAfter.compareTo(costBefore) != 0) {
            throw BusinessException.of(500, "拆分守恒校验失败，已回滚");
        }
        return created;
    }

    /**
     * 订单中是否仍含有占位明细（配货/出库阻断判断）。
     */
    public boolean hasPlaceholderItems(Long orderId, Long tenantId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .eq(OrderItem::getTenantId, tenantId));
        if (items.isEmpty()) {
            return false;
        }
        List<Long> skuIds = items.stream().map(OrderItem::getSkuId).filter(java.util.Objects::nonNull).toList();
        if (skuIds.isEmpty()) {
            return false;
        }
        Map<Long, ProductSku> skuMap = productSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));
        return items.stream().anyMatch(item -> {
            ProductSku sku = item.getSkuId() == null ? null : skuMap.get(item.getSkuId());
            return sku != null && "PLACEHOLDER".equals(sku.getSkuType());
        });
    }

    private void requireSplittable(Order order) {
        if (order.getFulfillmentStatus() == null) {
            throw BusinessException.of(400, "历史订单尚未迁移，请先完成历史迁移");
        }
        String status = order.getFulfillmentStatus();
        boolean splittable = FulfillmentStatus.CONFIRMED.name().equals(status)
                || FulfillmentStatus.WAITING_ALLOCATION.name().equals(status)
                || FulfillmentStatus.ALLOCATING.name().equals(status);
        if (!splittable) {
            throw BusinessException.of(400, "订单已进入发货后阶段，不能再拆分明细");
        }
    }

    private String resolveProductName(ProductSku sku) {
        if (sku.getProductId() == null) return "";
        Product product = productMapper.selectById(sku.getProductId());
        return product != null ? product.getName() : "";
    }

    private String resolveColorName(ProductSku sku) {
        if (sku.getColorId() == null) return "";
        ProductColor color = productColorMapper.selectById(sku.getColorId());
        return color != null ? color.getColorName() : "";
    }

    private String resolveSizeName(ProductSku sku) {
        if (sku.getSizeId() == null) return "";
        ProductSize size = productSizeMapper.selectById(sku.getSizeId());
        return size != null ? size.getSizeCode() : "";
    }

    private Long currentUserId() {
        User user = currentUser();
        return user != null ? user.getId() : null;
    }

    private String currentUserName() {
        User user = currentUser();
        return user != null ? user.getNickname() : null;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof User user ? user : null;
    }

    /** 拆分目标 */
    public static class SplitTarget {
        private Long skuId;
        private Integer quantity;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
