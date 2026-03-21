package com.blade.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.product.entity.ProductColorRel;
import com.blade.product.entity.ProductColor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductColorRelMapper extends BaseMapper<ProductColorRel> {

    @Select("SELECT pc.* FROM product_color pc " +
            "INNER JOIN product_color_rel pcr ON pc.id = pcr.color_id " +
            "WHERE pcr.product_id = #{productId} AND pc.deleted = 0")
    List<ProductColor> selectByProductId(@Param("productId") Long productId);

    @Delete("DELETE FROM product_color_rel WHERE product_id = #{productId}")
    void deleteByProductId(@Param("productId") Long productId);
}
