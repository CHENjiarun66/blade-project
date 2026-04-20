-- V20: 库存跨仓总量预留表
-- 用于记录付款确认时的跨仓总量预留

CREATE TABLE inventory_global_reserve (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    sku_id BIGINT NOT NULL COMMENT 'SKU ID',
    reserve_qty INT NOT NULL COMMENT '预留数量',
    released_qty INT NOT NULL DEFAULT 0 COMMENT '已释放数量',
    tenant_id BIGINT COMMENT '租户ID',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    INDEX idx_order_sku (order_id, sku_id),
    INDEX idx_sku (sku_id)
) COMMENT '库存跨仓总量预留表';

-- 给 inventory 表添加 global_reserved_qty 字段
ALTER TABLE inventory ADD COLUMN global_reserved_qty INT NOT NULL DEFAULT 0 COMMENT '全局预留数量（跨仓预留总量）' AFTER reserved_qty;
