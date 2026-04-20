-- V21: 订单配货计划模块
-- 1. order_delivery_plan 表（订单发货计划）
-- 2. order_adjustment_log 表（订单调整记录）
-- 3. sale_order 表新增字段
-- 4. sale_order_item 表新增字段

-- ----------------------------
-- 1. order_delivery_plan 表（订单发货计划）
-- ----------------------------
CREATE TABLE IF NOT EXISTS order_delivery_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_item_id BIGINT COMMENT '原订单明细ID（可空，用于追踪原商品）',
    sku_id BIGINT NOT NULL COMMENT 'SKU ID',
    warehouse_id BIGINT COMMENT '仓库ID（配货时填写）',
    planned_qty INT NOT NULL DEFAULT 0 COMMENT '计划数量（原订单数量）',
    allocated_qty INT NOT NULL DEFAULT 0 COMMENT '配货数量（调整后数量）',
    out_qty INT NOT NULL DEFAULT 0 COMMENT '已出库数量',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING待配/ALLOCATED已配/OUT已完成',
    remark VARCHAR(255) COMMENT '备注（如调整原因）',
    tenant_id BIGINT COMMENT '租户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_sku_warehouse (sku_id, warehouse_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单发货计划表';

-- ----------------------------
-- 2. order_adjustment_log 表（订单调整记录）
-- ----------------------------
CREATE TABLE IF NOT EXISTS order_adjustment_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人名称',
    adjustment_type VARCHAR(20) NOT NULL COMMENT '调整类型：REDUCE减数量/REPLACE替换/REFUND退款',
    original_sku_id BIGINT COMMENT '原SKU ID',
    original_quantity INT COMMENT '原数量',
    new_sku_id BIGINT COMMENT '新SKU ID（替换时使用）',
    new_quantity INT COMMENT '新数量',
    reason VARCHAR(255) COMMENT '调整原因',
    confirmed_time DATETIME COMMENT '确认时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    tenant_id BIGINT COMMENT '租户ID',
    INDEX idx_order_id (order_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单调整记录表';

-- ----------------------------
-- 3. sale_order 表新增字段
-- ----------------------------
ALTER TABLE sale_order ADD COLUMN original_amount DECIMAL(12,2) COMMENT '原始订单金额' AFTER total_amount;
ALTER TABLE sale_order ADD COLUMN refund_amount DECIMAL(12,2) DEFAULT 0 COMMENT '已退款金额' AFTER original_amount;
ALTER TABLE sale_order ADD COLUMN adjustment_status VARCHAR(20) DEFAULT 'NONE' COMMENT '调整状态：NONE无调整/PENDING待确认/APPROVED已确认/COMPLETED已完成' AFTER refund_amount;

-- ----------------------------
-- 4. sale_order_item 表新增字段
-- ----------------------------
ALTER TABLE sale_order_item ADD COLUMN planned_quantity INT DEFAULT 0 COMMENT '计划数量（原订单数量）' AFTER quantity;
ALTER TABLE sale_order_item ADD COLUMN allocated_quantity INT DEFAULT 0 COMMENT '配货数量（调整后数量）' AFTER planned_quantity;
ALTER TABLE sale_order_item ADD COLUMN out_quantity INT DEFAULT 0 COMMENT '已出库数量' AFTER allocated_quantity;
ALTER TABLE sale_order_item ADD COLUMN adjustment_remark VARCHAR(255) COMMENT '调整说明' AFTER out_quantity;

-- ----------------------------
-- 验证表结构
-- ----------------------------
SELECT 'V21 配货计划模块迁移完成' AS status;
