-- V17: 订单出库单表（支持多仓库出库）
-- 一个订单可以创建多个出库单，分别从不同仓库出库

-- 出库单表
CREATE TABLE IF NOT EXISTS order_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    delivery_no VARCHAR(30) NOT NULL COMMENT '出库单号',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待出库 1部分出库 2已出库 3已取消',
    total_quantity INT NOT NULL DEFAULT 0 COMMENT '出库总数量',
    deliverer VARCHAR(50) COMMENT '发货人',
    deliver_time DATETIME COMMENT '发货时间',
    remark VARCHAR(255) COMMENT '备注',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_no (delivery_no),
    KEY idx_order_id (order_id),
    KEY idx_warehouse_id (warehouse_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单表';

-- 出库明细表
CREATE TABLE IF NOT EXISTS order_delivery_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    delivery_id BIGINT NOT NULL COMMENT '出库单ID',
    order_item_id BIGINT NOT NULL COMMENT '订单明细ID',
    sku_id BIGINT NOT NULL COMMENT 'SKU ID',
    quantity INT NOT NULL COMMENT '出库数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_delivery_id (delivery_id),
    KEY idx_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库明细表';
