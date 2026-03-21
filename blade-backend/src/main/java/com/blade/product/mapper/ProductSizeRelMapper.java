package com.blade.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.product.entity.ProductSizeRel;
import com.blade.product.entity.ProductSize;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSizeRelMapper extends BaseMapper<ProductSizeRel> {

    @Select("SELECT ps.* FROM product_size ps " +
            "INNER JOIN product_size_rel psr ON ps.id = psr.size_id " +
            "WHERE psr.product_id = #{productId} AND ps.deleted = 0 " +
            "ORDER BY ps.sort")
    List<ProductSize> selectByProductId(@Param("productId") Long productId);

    @Delete("DELETE FROM product_size_rel WHERE product_id = #{productId}")
    void deleteByProductId(@Param("productId") Long productId);
}
