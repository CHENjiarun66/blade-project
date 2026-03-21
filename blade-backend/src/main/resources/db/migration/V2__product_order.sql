-- V2: 订单模块表结构

-- ----------------------------
-- 订单表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(30) NOT NULL COMMENT '订单号',
  `customer_name` varchar(50) NOT NULL COMMENT '客户名称',
  `customer_phone` varchar(11) DEFAULT NULL COMMENT '客户电话',
  `customer_address` varchar(255) DEFAULT NULL COMMENT '客户地址',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单总金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态: 0待处理/1已确认/2进行中/3已完成/4已取消',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_customer_phone` (`customer_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------
-- 订单明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `product_name` varchar(100) NOT NULL COMMENT '商品名称',
  `sku` varchar(50) DEFAULT NULL COMMENT 'SKU编码',
  `price` decimal(12,2) NOT NULL COMMENT '单价',
  `quantity` int NOT NULL COMMENT '数量',
  `subtotal` decimal(12,2) NOT NULL COMMENT '小计金额',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------
-- 插入测试订单数据
-- ----------------------------
INSERT INTO `product_order` (`order_no`, `customer_name`, `customer_phone`, `customer_address`, `total_amount`, `status`, `remark`, `tenant_id`)
VALUES
  ('ORD202603210001', '张三', '13800138000', '北京市朝阳区xxx', 1000.00, 0, '尽快发货', 1),
  ('ORD202603210002', '李四', '13900139000', '上海市浦东新区xxx', 2500.00, 1, '', 1),
  ('ORD202603210003', '王五', '13700137000', '广州市天河区xxx', 800.00, 2, '加急', 1);

-- ----------------------------
-- 插入测试订单明细
-- ----------------------------
INSERT INTO `order_item` (`order_id`, `product_id`, `sku_id`, `product_name`, `sku`, `price`, `quantity`, `subtotal`, `tenant_id`)
VALUES
  (1, 1, 101, '商品A', 'SKU001', 100.00, 10, 1000.00, 1),
  (2, 1, 101, '商品A', 'SKU001', 100.00, 15, 1500.00, 1),
  (2, 2, 102, '商品B', 'SKU002', 250.00, 4, 1000.00, 1),
  (3, 3, 103, '商品C', 'SKU003', 80.00, 10, 800.00, 1);
