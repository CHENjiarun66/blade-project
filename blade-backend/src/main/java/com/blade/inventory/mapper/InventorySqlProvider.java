package com.blade.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;

public class InventorySqlProvider {

    public String selectInventoryList(
            @Param("tenantId") Long tenantId,
            @Param("warehouseId") Long warehouseId,
            @Param("alertStatus") String alertStatus,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") long size) {

        SQL sql = new SQL();
        sql.SELECT("i.id, i.sku_id, i.warehouse_id, i.quantity, i.reserved_qty, i.alert_threshold, i.version, i.tenant_id, i.update_time, ps.sku_code, ps.price, p.name AS product_name, p.category_id, pc.color_name, ps.size_name, w.warehouse_name");
        sql.FROM("inventory i");
        sql.INNER_JOIN("product_sku ps ON i.sku_id = ps.id");
        sql.INNER_JOIN("product p ON ps.product_id = p.id");
        sql.LEFT_OUTER_JOIN("product_color pc ON ps.color_id = pc.id");
        sql.LEFT_OUTER_JOIN("warehouse w ON i.warehouse_id = w.id");
        sql.WHERE("i.tenant_id = #{tenantId}");

        if (warehouseId != null) {
            sql.WHERE("i.warehouse_id = #{warehouseId}");
        }

        if ("below".equals(alertStatus)) {
            sql.WHERE("(i.quantity - i.reserved_qty) < i.alert_threshold");
        } else if ("normal".equals(alertStatus)) {
            sql.WHERE("(i.quantity - i.reserved_qty) >= i.alert_threshold");
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.WHERE("(p.name LIKE #{keywordPattern} OR ps.sku_code LIKE #{keywordPattern})");
        }

        sql.ORDER_BY("i.id DESC");
        sql.LIMIT("#{offset}, #{size}");

        String result = sql.toString();
        // 替换占位符
        if (keyword != null && !keyword.isBlank()) {
            result = result.replace("#{keywordPattern}", "'%" + keyword + "%'");
        }
        return result;
    }

    public String countInventoryList(
            @Param("tenantId") Long tenantId,
            @Param("warehouseId") Long warehouseId,
            @Param("alertStatus") String alertStatus,
            @Param("keyword") String keyword) {

        SQL sql = new SQL();
        sql.SELECT("COUNT(*)");
        sql.FROM("inventory i");
        sql.INNER_JOIN("product_sku ps ON i.sku_id = ps.id");
        sql.INNER_JOIN("product p ON ps.product_id = p.id");
        sql.WHERE("i.tenant_id = #{tenantId}");

        if (warehouseId != null) {
            sql.WHERE("i.warehouse_id = #{warehouseId}");
        }

        if ("below".equals(alertStatus)) {
            sql.WHERE("(i.quantity - i.reserved_qty) < i.alert_threshold");
        } else if ("normal".equals(alertStatus)) {
            sql.WHERE("(i.quantity - i.reserved_qty) >= i.alert_threshold");
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.WHERE("(p.name LIKE #{keywordPattern} OR ps.sku_code LIKE #{keywordPattern})");
        }

        String result = sql.toString();
        if (keyword != null && !keyword.isBlank()) {
            result = result.replace("#{keywordPattern}", "'%" + keyword + "%'");
        }
        return result;
    }
}
