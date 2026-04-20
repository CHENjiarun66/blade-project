-- V9: 客户模块
-- 客户表
CREATE TABLE IF NOT EXISTS `crm_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `name` varchar(50) NOT NULL COMMENT '客户名称',
  `address` varchar(255) DEFAULT NULL COMMENT '客户地址',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- 客户电话表（一个客户可以有多个电话）
CREATE TABLE IF NOT EXISTS `crm_customer_phone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '电话ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `phone` varchar(20) NOT NULL COMMENT '电话号码',
  `is_primary` tinyint DEFAULT '0' COMMENT '是否主号: 0否 1是',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户电话表';

-- 插入一些测试客户数据
INSERT INTO `crm_customer` (`id`, `name`, `address`, `remark`, `tenant_id`) VALUES
(1, '张三', '北京市朝阳区某某路123号', 'VIP客户', 1),
(2, '李四', '上海市浦东新区某某街456号', '普通客户', 1);

INSERT INTO `crm_customer_phone` (`customer_id`, `phone`, `is_primary`, `tenant_id`) VALUES
(1, '8613800001111', 1, 1),
(1, '8613800002222', 0, 1),
(2, '8613900001111', 1, 1);
