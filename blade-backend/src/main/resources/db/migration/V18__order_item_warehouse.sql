-- 订单明细表添加仓库ID字段，支持多仓库分配
ALTER TABLE sale_order_item ADD COLUMN warehouse_id BIGINT COMMENT '仓库ID（支持多仓库分配）' AFTER sku_id;
