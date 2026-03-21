-- V4: 库存模块

-- ----------------------------
-- 仓库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  `warehouse_name` varchar(50) NOT NULL COMMENT '仓库名称',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `contact` varchar(30) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '电话',
  `status` tinyint DEFAULT '1' COMMENT '状态: 1启用 0禁用',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库表';

-- ----------------------------
-- 库存表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `quantity` int NOT NULL DEFAULT '0' COMMENT '当前库存数量',
  `reserved_qty` int NOT NULL DEFAULT '0' COMMENT '预留数量（订单占用）',
  `available_qty` int GENERATED ALWAYS AS (`quantity` - `reserved_qty`) STORED COMMENT '可用数量',
  `alert_threshold` int DEFAULT '10' COMMENT '预警阈值',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_warehouse` (`sku_id`, `warehouse_id`, `tenant_id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- ----------------------------
-- 库存变动记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `change_type` varchar(20) NOT NULL COMMENT '变动类型',
  `change_qty` int NOT NULL COMMENT '变动数量（正数=入库，负数=出库）',
  `before_qty` int NOT NULL COMMENT '变动前库存',
  `after_qty` int NOT NULL COMMENT '变动后库存',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID',
  `reference_no` varchar(50) DEFAULT NULL COMMENT '关联单据号',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID（预留）',
  `supplier_name` varchar(100) DEFAULT NULL COMMENT '供应商名称（冗余）',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `images` varchar(1000) DEFAULT NULL COMMENT '图片URLs，逗号分隔',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_change_type` (`change_type`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变动记录表';

-- ----------------------------
-- 插入默认仓库
-- ----------------------------
INSERT INTO `warehouse` (`id`, `warehouse_name`, `address`, `contact`, `phone`, `status`, `tenant_id`)
VALUES (1, '默认仓库', '地址待填写', '联系人', '13800138000', 1, 1)
ON DUPLICATE KEY UPDATE `warehouse_name` = VALUES(`warehouse_name`);

-- ----------------------------
-- 初始化库存数据（将现有 SKU 初始化到默认仓库）
-- ----------------------------
INSERT INTO `inventory` (`sku_id`, `warehouse_id`, `quantity`, `reserved_qty`, `tenant_id`)
SELECT ps.id, 1, 100, 0, ps.tenant_id
FROM product_sku ps
WHERE NOT EXISTS (
    SELECT 1 FROM inventory inv WHERE inv.sku_id = ps.id AND inv.warehouse_id = 1
)
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);
