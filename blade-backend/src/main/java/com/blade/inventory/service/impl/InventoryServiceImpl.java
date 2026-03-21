package com.blade.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.dto.*;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.entity.InventoryLog;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.inventory.mapper.InventoryLogMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductSkuMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryLogMapper inventoryLogMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    // 变动类型常量
    private static final String CHANGE_TYPE_PURCHASE_IN = "PURCHASE_IN";
    private static final String CHANGE_TYPE_SALE_OUT = "SALE_OUT";
    private static final String CHANGE_TYPE_SALE_CANCEL = "SALE_CANCEL";
    private static final String CHANGE_TYPE_ADJUST = "ADJUST";
    private static final String CHANGE_TYPE_OTHER_OUT = "OTHER_OUT";

    @Override
    public PageResult<InventoryVO> pageList(InventoryPageDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        String sql = """
            SELECT i.*,
                   ps.sku_code, ps.price,
                   p.name AS product_name, p.category_id,
                   pc.color_name, ps.size_name,
                   w.warehouse_name
            FROM inventory i
            INNER JOIN product_sku ps ON i.sku_id = ps.id
            INNER JOIN product p ON ps.product_id = p.id
            LEFT JOIN product_color pc ON ps.color_id = pc.id
            LEFT JOIN warehouse w ON i.warehouse_id = w.id
            WHERE i.tenant_id = ?
            """;

        List<Object> params = new ArrayList<>();
        params.add(tenantId);

        StringBuilder whereSql = new StringBuilder();

        if (dto.getWarehouseId() != null) {
            whereSql.append(" AND i.warehouse_id = ?");
            params.add(dto.getWarehouseId());
        }

        if ("below".equals(dto.getAlertStatus())) {
            whereSql.append(" AND (i.quantity - i.reserved_qty) < i.alert_threshold");
        } else if ("normal".equals(dto.getAlertStatus())) {
            whereSql.append(" AND (i.quantity - i.reserved_qty) >= i.alert_threshold");
        }

        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            whereSql.append(" AND (p.name LIKE ? OR ps.sku_code LIKE ?)");
            params.add("%" + dto.getKeyword() + "%");
            params.add("%" + dto.getKeyword() + "%");
        }

        // 使用分页查询
        IPage<Inventory> page = new Page<>(dto.getCurrent(), dto.getSize());

        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getTenantId, tenantId);

        if (dto.getWarehouseId() != null) {
            wrapper.eq(Inventory::getWarehouseId, dto.getWarehouseId());
        }

        wrapper.orderByDesc(Inventory::getId);
        IPage<Inventory> result = inventoryMapper.selectPage(page, wrapper);

        // 转换结果
        List<InventoryVO> voList = new ArrayList<>();
        for (Inventory inv : result.getRecords()) {
            InventoryVO vo = convertToVO(inv);
            // 补充 SKU 信息
            ProductSku sku = productSkuMapper.selectById(inv.getSkuId());
            if (sku != null) {
                vo.setSkuCode(sku.getSkuCode());
                vo.setPrice(sku.getPrice());
            }
            // 计算预警状态
            int available = inv.getQuantity() - inv.getReservedQty();
            vo.setAvailableQty(available);
            if (available < inv.getAlertThreshold()) {
                vo.setAlertStatus("below");
            } else {
                vo.setAlertStatus("normal");
            }
            voList.add(vo);
        }

        PageResult<InventoryVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public InventoryVO getById(Long id) {
        Inventory inv = inventoryMapper.selectById(id);
        if (inv == null) {
            throw new RuntimeException("库存记录不存在");
        }
        return convertToVO(inv);
    }

    @Override
    public List<InventoryVO> listByWarehouse(Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getWarehouseId, warehouseId);
        wrapper.eq(Inventory::getTenantId, TenantContext.getTenantId());
        List<Inventory> list = inventoryMapper.selectList(wrapper);
        return list.stream().map(this::convertToVO).toList();
    }

    @Override
    @Transactional
    public void in(InventoryInDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();
        String images = dto.getImages() != null ? String.join(",", dto.getImages()) : null;

        for (InventoryInItemDTO item : dto.getItems()) {
            // 查询或创建库存记录
            Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
            int beforeQty = 0;

            if (inv == null) {
                // 创建新库存记录
                inv = new Inventory();
                inv.setSkuId(item.getSkuId());
                inv.setWarehouseId(dto.getWarehouseId());
                inv.setQuantity(item.getQuantity());
                inv.setReservedQty(0);
                inv.setAlertThreshold(10);
                inv.setTenantId(tenantId);
                inventoryMapper.insert(inv);
                beforeQty = 0;
            } else {
                beforeQty = inv.getQuantity();
                // 更新库存
                LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(Inventory::getId, inv.getId());
                wrapper.setSql("quantity = quantity + " + item.getQuantity());
                inventoryMapper.update(null, wrapper);
                inv.setQuantity(beforeQty + item.getQuantity());
            }

            // 记录变动日志
            InventoryLog log = new InventoryLog();
            log.setSkuId(item.getSkuId());
            log.setWarehouseId(dto.getWarehouseId());
            log.setChangeType(CHANGE_TYPE_PURCHASE_IN);
            log.setChangeQty(item.getQuantity());
            log.setBeforeQty(beforeQty);
            log.setAfterQty(inv.getQuantity());
            log.setSupplierId(dto.getSupplierId());
            log.setSupplierName(dto.getSupplierName());
            log.setOperatorId(operatorId);
            log.setRemark(item.getRemark() != null ? item.getRemark() : dto.getRemark());
            log.setImages(images);
            log.setTenantId(tenantId);
            inventoryLogMapper.insert(log);
        }
    }

    @Override
    @Transactional
    public void out(InventoryOutDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryOutItemDTO item : dto.getItems()) {
            Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
            if (inv == null || inv.getQuantity() - inv.getReservedQty() < item.getQuantity()) {
                throw new RuntimeException("库存不足，无法出库");
            }

            int beforeQty = inv.getQuantity();
            int changeQty = -item.getQuantity();

            // 更新库存
            LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Inventory::getId, inv.getId());
            wrapper.setSql("quantity = quantity + " + changeQty);
            inventoryMapper.update(null, wrapper);

            // 记录变动日志
            String changeType = "ORDER".equals(dto.getSource()) ? CHANGE_TYPE_SALE_OUT : CHANGE_TYPE_OTHER_OUT;
            String reason = "ORDER".equals(dto.getSource()) ? null : item.getReason();

            InventoryLog log = new InventoryLog();
            log.setSkuId(item.getSkuId());
            log.setWarehouseId(dto.getWarehouseId());
            log.setChangeType(changeType);
            log.setChangeQty(changeQty);
            log.setBeforeQty(beforeQty);
            log.setAfterQty(beforeQty + changeQty);
            log.setOrderId(dto.getOrderId());
            log.setOperatorId(operatorId);
            log.setRemark(reason != null ? reason : dto.getRemark());
            log.setTenantId(tenantId);
            inventoryLogMapper.insert(log);
        }
    }

    @Override
    @Transactional
    public void adjust(InventoryAdjustDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryAdjustItemDTO item : dto.getItems()) {
            Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
            if (inv == null) {
                throw new RuntimeException("库存记录不存在");
            }

            int beforeQty = inv.getQuantity();
            int changeQty = item.getQuantity();

            // 更新库存
            LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Inventory::getId, inv.getId());
            wrapper.setSql("quantity = quantity + " + changeQty);
            inventoryMapper.update(null, wrapper);

            // 记录变动日志
            InventoryLog log = new InventoryLog();
            log.setSkuId(item.getSkuId());
            log.setWarehouseId(dto.getWarehouseId());
            log.setChangeType(CHANGE_TYPE_ADJUST);
            log.setChangeQty(changeQty);
            log.setBeforeQty(beforeQty);
            log.setAfterQty(beforeQty + changeQty);
            log.setOperatorId(operatorId);
            log.setRemark(dto.getReason() + (dto.getRemark() != null ? ": " + dto.getRemark() : ""));
            log.setTenantId(tenantId);
            inventoryLogMapper.insert(log);
        }
    }

    @Override
    @Transactional
    public void reserve(InventoryReserveDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryReserveDTO.ReserveItemDTO item : dto.getItems()) {
            Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
            if (inv == null) {
                throw new RuntimeException("库存记录不存在");
            }

            int available = inv.getQuantity() - inv.getReservedQty();
            if (available < item.getQuantity()) {
                throw new RuntimeException("可用库存不足，无法预留");
            }

            int beforeReserved = inv.getReservedQty();

            // 更新预留数量
            LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Inventory::getId, inv.getId());
            wrapper.setSql("reserved_qty = reserved_qty + " + item.getQuantity());
            inventoryMapper.update(null, wrapper);

            // 记录变动日志（SALE_OUT 扣可用库存，释放时用 SALE_CANCEL）
            InventoryLog log = new InventoryLog();
            log.setSkuId(item.getSkuId());
            log.setWarehouseId(dto.getWarehouseId());
            log.setChangeType(CHANGE_TYPE_SALE_OUT);
            log.setChangeQty(-item.getQuantity());
            log.setBeforeQty(inv.getQuantity() - inv.getReservedQty());
            log.setAfterQty(inv.getQuantity() - inv.getReservedQty() - item.getQuantity());
            log.setOrderId(dto.getOrderId());
            log.setOperatorId(operatorId);
            log.setRemark("预留锁定");
            log.setTenantId(tenantId);
            inventoryLogMapper.insert(log);
        }
    }

    @Override
    @Transactional
    public void release(InventoryReserveDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryReserveDTO.ReserveItemDTO item : dto.getItems()) {
            Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
            if (inv == null) {
                throw new RuntimeException("库存记录不存在");
            }

            int beforeReserved = inv.getReservedQty();

            // 释放预留数量
            LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Inventory::getId, inv.getId());
            wrapper.setSql("reserved_qty = reserved_qty - " + item.getQuantity());
            inventoryMapper.update(null, wrapper);

            // 记录变动日志
            InventoryLog log = new InventoryLog();
            log.setSkuId(item.getSkuId());
            log.setWarehouseId(dto.getWarehouseId());
            log.setChangeType(CHANGE_TYPE_SALE_CANCEL);
            log.setChangeQty(item.getQuantity());
            log.setBeforeQty(inv.getQuantity() - beforeReserved);
            log.setAfterQty(inv.getQuantity() - beforeReserved + item.getQuantity());
            log.setOrderId(dto.getOrderId());
            log.setOperatorId(operatorId);
            log.setRemark("预留释放");
            log.setTenantId(tenantId);
            inventoryLogMapper.insert(log);
        }
    }

    @Override
    public List<InventoryVO> listAlerts(Long warehouseId) {
        Long tenantId = TenantContext.getTenantId();

        String sql = """
            SELECT i.*, ps.sku_code, p.name AS product_name, w.warehouse_name
            FROM inventory i
            INNER JOIN product_sku ps ON i.sku_id = ps.id
            INNER JOIN product p ON ps.product_id = p.id
            LEFT JOIN warehouse w ON i.warehouse_id = w.id
            WHERE i.tenant_id = ? AND (i.quantity - i.reserved_qty) < i.alert_threshold
            """;

        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getTenantId, tenantId);
        wrapper.apply("(quantity - reserved_qty) < alert_threshold");

        if (warehouseId != null) {
            wrapper.eq(Inventory::getWarehouseId, warehouseId);
        }

        List<Inventory> list = inventoryMapper.selectList(wrapper);
        return list.stream().map(inv -> {
            InventoryVO vo = convertToVO(inv);
            vo.setAlertStatus("below");
            return vo;
        }).toList();
    }

    private InventoryVO convertToVO(Inventory inv) {
        InventoryVO vo = new InventoryVO();
        BeanUtils.copyProperties(inv, vo);
        vo.setAvailableQty(inv.getQuantity() - inv.getReservedQty());
        return vo;
    }
}
