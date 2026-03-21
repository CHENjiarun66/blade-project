package com.blade.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT * FROM inventory WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId}")
    Inventory selectBySkuAndWarehouse(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId);

    @Update("UPDATE inventory SET quantity = quantity + #{changeQty}, reserved_qty = reserved_qty + #{reservedChange} WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId}")
    int updateQuantity(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId, @Param("changeQty") Integer changeQty, @Param("reservedChange") Integer reservedChange);
}
