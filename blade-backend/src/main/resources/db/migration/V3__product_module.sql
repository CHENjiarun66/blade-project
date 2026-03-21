-- V3: 商品模块表结构

-- ----------------------------
-- 商品分类表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` varchar(50) NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态: 1启用 0禁用',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ----------------------------
-- 商品表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `product_code` varchar(30) NOT NULL COMMENT '商品编码',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `unit` varchar(10) DEFAULT '件' COMMENT '单位',
  `description` text COMMENT '描述',
  `image_url` varchar(255) DEFAULT NULL COMMENT '商品图片',
  `status` tinyint DEFAULT '1' COMMENT '状态: 1启用 0禁用',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ----------------------------
-- 颜色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_color` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '颜色ID',
  `color_code` varchar(20) NOT NULL COMMENT '颜色编码',
  `color_name` varchar(50) NOT NULL COMMENT '颜色名称',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_color_code` (`color_code`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='颜色表';

-- ----------------------------
-- 尺码表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_size` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '尺码ID',
  `size_code` varchar(10) NOT NULL COMMENT '尺码编码',
  `sort` int DEFAULT '0' COMMENT '排序',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_size_code` (`size_code`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='尺码表';

-- ----------------------------
-- SKU表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `color_id` bigint NOT NULL COMMENT '颜色ID',
  `size_id` bigint NOT NULL COMMENT '尺码ID',
  `sku_code` varchar(50) NOT NULL COMMENT 'SKU编码（系统自动生成）',
  `price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '单价',
  `cost_price` decimal(12,2) DEFAULT '0.00' COMMENT '成本价',
  `bar_code` varchar(50) DEFAULT NULL COMMENT '条形码',
  `status` tinyint DEFAULT '1' COMMENT '状态: 1启用 0禁用',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`, `tenant_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_color_id` (`color_id`),
  KEY `idx_size_id` (`size_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU表';

-- ----------------------------
-- 商品-颜色关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_color_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `color_id` bigint NOT NULL COMMENT '颜色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_color` (`product_id`, `color_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_color_id` (`color_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品颜色关联表';

-- ----------------------------
-- 商品-尺码关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_size_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `size_id` bigint NOT NULL COMMENT '尺码ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_size` (`product_id`, `size_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_size_id` (`size_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品尺码关联表';

-- ----------------------------
-- 插入默认分类
-- ----------------------------
INSERT INTO `product_category` (`id`, `category_name`, `sort`, `tenant_id`)
VALUES
  (1, '上衣', 1, 1),
  (2, '裤子', 2, 1),
  (3, '裙子', 3, 1),
  (4, '外套', 4, 1),
  (5, '配饰', 5, 1);

-- ----------------------------
-- 插入默认颜色
-- ----------------------------
INSERT INTO `product_color` (`id`, `color_code`, `color_name`, `tenant_id`)
VALUES
  (1, 'BLACK', '黑色', 1),
  (2, 'WHITE', '白色', 1),
  (3, 'GRAY', '灰色', 1),
  (4, 'BLUE', '蓝色', 1),
  (5, 'RED', '红色', 1),
  (6, 'GREEN', '绿色', 1);

-- ----------------------------
-- 插入默认尺码
-- ----------------------------
INSERT INTO `product_size` (`id`, `size_code`, `sort`, `tenant_id`)
VALUES
  (1, 'XS', 1, 1),
  (2, 'S', 2, 1),
  (3, 'M', 3, 1),
  (4, 'L', 4, 1),
  (5, 'XL', 5, 1),
  (6, 'XXL', 6, 1);

-- ----------------------------
-- 插入测试商品
-- ----------------------------
INSERT INTO `product` (`id`, `product_code`, `name`, `category_id`, `unit`, `price`, `status`, `tenant_id`)
VALUES
  (1, 'P001', '经典T恤', 1, '件', 99.00, 1, 1),
  (2, 'P002', '休闲裤', 2, '条', 199.00, 1, 1),
  (3, 'P003', '商务衬衫', 1, '件', 299.00, 1, 1);
