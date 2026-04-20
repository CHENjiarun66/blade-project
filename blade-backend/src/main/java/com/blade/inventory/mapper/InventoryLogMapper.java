package com.blade.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.inventory.entity.InventoryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {

    List<InventoryLog> selectInventoryLogList(
            @Param("tenantId") Long tenantId,
            @Param("skuId") Long skuId,
            @Param("warehouseId") Long warehouseId,
            @Param("changeType") String changeType,
            @Param("offset") long offset,
            @Param("size") long size
    );

    long countInventoryLogList(
            @Param("tenantId") Long tenantId,
            @Param("skuId") Long skuId,
            @Param("warehouseId") Long warehouseId,
            @Param("changeType") String changeType
    );
}
