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

    List<InventoryVO> listAlerts(Long warehouseId);
}
