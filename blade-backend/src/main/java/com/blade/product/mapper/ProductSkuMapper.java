package com.blade.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.product.dto.SkuVO;
import com.blade.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Select("""
        SELECT ps.id, ps.sku_code AS skuCode, p.name AS productName, p.id AS productId,
               pc.color_name AS colorName, ps.color_id AS colorId,
               psz.size_code AS sizeName, ps.size_id AS sizeId,
               ps.price, COALESCE(i.quantity, 0) AS stock
        FROM product_sku ps
        INNER JOIN product p ON ps.product_id = p.id
        LEFT JOIN product_color pc ON ps.color_id = pc.id
        LEFT JOIN product_size psz ON ps.size_id = psz.id
        LEFT JOIN inventory i ON ps.id = i.sku_id AND i.warehouse_id = 1
        WHERE ps.status = 1 AND p.status = 1
        ORDER BY ps.id DESC
        """)
    List<SkuVO> selectAllSkuList();
}
