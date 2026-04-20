-- V23: 商品表新增字段 + 供应商表

-- ----------------------------
-- 1. 商品表新增字段
-- ----------------------------
ALTER TABLE `product` ADD COLUMN `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID' AFTER `category_id`;
ALTER TABLE `product` ADD COLUMN `cost_price` decimal(12,2) DEFAULT NULL COMMENT '进货价（成本参考）' AFTER `supplier_id`;
ALTER TABLE `product` ADD COLUMN `wholesale_price` decimal(12,2) DEFAULT NULL COMMENT '批发价' AFTER `cost_price`;
ALTER TABLE `product` ADD COLUMN `weight` decimal(10,2) DEFAULT NULL COMMENT '重量（用于物流/运费计算）' AFTER `wholesale_price`;
ALTER TABLE `product` ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注' AFTER `image_url`;

-- ----------------------------
-- 2. 供应商表（预留，暂不开发管理页面）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `supplier_code` varchar(30) NOT NULL COMMENT '供应商编码',
  `supplier_name` varchar(100) NOT NULL COMMENT '供应商名称',
  `contact` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '电话',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `status` tinyint DEFAULT '1' COMMENT '状态: 1启用 0禁用',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ----------------------------
-- 3. 添加外键约束（等供应商模块开发后启用）
-- ----------------------------
-- ALTER TABLE `product` ADD CONSTRAINT `fk_product_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`);
