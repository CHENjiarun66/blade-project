package com.blade.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.dto.WarehouseCreateDTO;
import com.blade.inventory.dto.WarehouseUpdateDTO;
import com.blade.inventory.dto.WarehouseVO;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.inventory.service.WarehouseService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    @Autowired
    private WarehouseMapper warehouseMapper;

    @Override
    public PageResult<WarehouseVO> pageList(int current, int size) {
        IPage<Warehouse> page = new Page<>(current, size);
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Warehouse::getTenantId, TenantContext.getTenantId());
        wrapper.orderByDesc(Warehouse::getId);
        IPage<Warehouse> result = warehouseMapper.selectPage(page, wrapper);

        PageResult<WarehouseVO> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords().stream().map(this::convertToVO).toList());
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public WarehouseVO getById(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new RuntimeException("仓库不存在");
        }
        return convertToVO(warehouse);
    }

    @Override
    @Transactional
    public Long create(WarehouseCreateDTO dto) {
        Warehouse warehouse = new Warehouse();
        BeanUtils.copyProperties(dto, warehouse);
        warehouse.setTenantId(TenantContext.getTenantId());
        warehouseMapper.insert(warehouse);
        return warehouse.getId();
    }

    @Override
    @Transactional
    public void update(WarehouseUpdateDTO dto) {
        Warehouse warehouse = warehouseMapper.selectById(dto.getId());
        if (warehouse == null) {
            throw new RuntimeException("仓库不存在");
        }
        if (dto.getWarehouseName() != null) {
            warehouse.setWarehouseName(dto.getWarehouseName());
        }
        if (dto.getAddress() != null) {
            warehouse.setAddress(dto.getAddress());
        }
        if (dto.getContact() != null) {
            warehouse.setContact(dto.getContact());
        }
        if (dto.getPhone() != null) {
            warehouse.setPhone(dto.getPhone());
        }
        if (dto.getStatus() != null) {
            warehouse.setStatus(dto.getStatus());
        }
        warehouseMapper.updateById(warehouse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new RuntimeException("仓库不存在");
        }
        warehouse.setDeleted(1);
        warehouseMapper.updateById(warehouse);
    }

    private WarehouseVO convertToVO(Warehouse warehouse) {
        WarehouseVO vo = new WarehouseVO();
        BeanUtils.copyProperties(warehouse, vo);
        return vo;
    }
}
