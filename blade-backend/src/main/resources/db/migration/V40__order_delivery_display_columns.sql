-- V40: 补齐出库单展示冗余字段
-- V17 创建出库单表时未包含实体已使用的展示字段，导致查询/插入出库单时报 unknown column。

ALTER TABLE order_delivery
    ADD COLUMN warehouse_name VARCHAR(50) DEFAULT NULL COMMENT '仓库名称（冗余）' AFTER warehouse_id;

ALTER TABLE order_delivery_item
    ADD COLUMN sku_code VARCHAR(50) DEFAULT NULL COMMENT 'SKU编码（冗余）' AFTER sku_id,
    ADD COLUMN product_name VARCHAR(100) DEFAULT NULL COMMENT '商品名称（冗余）' AFTER sku_code,
    ADD COLUMN color_name VARCHAR(50) DEFAULT NULL COMMENT '颜色名称（冗余）' AFTER product_name,
    ADD COLUMN size_name VARCHAR(50) DEFAULT NULL COMMENT '尺码名称（冗余）' AFTER color_name;
