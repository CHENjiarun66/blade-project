package com.blade.inventory.service;

import com.blade.common.result.PageResult;
import com.blade.inventory.dto.WarehouseCreateDTO;
import com.blade.inventory.dto.WarehouseUpdateDTO;
import com.blade.inventory.dto.WarehouseVO;

import java.util.List;

public interface WarehouseService {

    PageResult<WarehouseVO> pageList(int current, int size);

    List<WarehouseVO> listAll();

    WarehouseVO getById(Long id);

    Long create(WarehouseCreateDTO dto);

    void update(WarehouseUpdateDTO dto);

    void delete(Long id);
}
