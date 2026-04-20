package com.blade.inventory.service;

import com.blade.common.result.PageResult;
import com.blade.inventory.dto.*;

import java.util.List;

public interface InventoryService {

    PageResult<InventoryVO> pageList(InventoryPageDTO dto);

    InventoryVO getById(Long id);

    List<InventoryVO> listByWarehouse(Long warehouseId);

    void in(InventoryInDTO dto, Long operatorId);

    void out(InventoryOutDTO dto, Long operatorId);

    void adjust(InventoryAdjustDTO dto, Long operatorId);

    void reserve(InventoryReserveDTO dto, Long operatorId);

    void release(InventoryReserveDTO dto, Long operatorId);

    /**
     * 跨仓总量预留（付款确认时调用）
     * 校验跨仓总量是否充足，充足则锁定
     */
    void globalReserve(InventoryReserveDTO dto, Long operatorId);

    /**
     * 跨仓总量释放（取消订单时调用）
     */
    void globalRelease(InventoryReserveDTO dto, Long operatorId);

    /**
     * 跨仓部分释放预留（确认调整方案减配时调用）
     * 不依赖 inventory_global_reserve 记录，直接按 SKU 在各仓库减少 global_reserved_qty
     */
    void globalReleasePartial(Long skuId, Integer quantity, Long orderId, Long operatorId);

    /**
     * 查询SKU跨仓可用总量
     * 可用量 = Σ(inventory.quantity - inventory.reserved_qty - inventory.global_reserved_qty)
     */
    Integer getGlobalAvailableQty(Long skuId);

    /**
     * 按配货计划出库
     * 同时扣减 quantity、reserved_qty、global_reserved_qty
     */
    void outByPlan(Long planId, Integer quantity, Long operatorId);

    List<InventoryVO> listAlerts(Long warehouseId);

    PageResult<InventoryLogVO> listLogs(InventoryLogPageDTO dto);
}
