-- V12: 权限系统核心表
-- 创建权限定义表和角色权限关联表

-- ----------------------------
-- 权限定义表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `code` varchar(100) NOT NULL COMMENT '权限编码，全局唯一',
  `type` int NOT NULL COMMENT '权限类型: 1菜单 2按钮 3字段 4API',
  `module` varchar(50) DEFAULT NULL COMMENT '所属模块: order/inventory/product/finance/system',
  `parent_id` bigint DEFAULT 0 COMMENT '父权限ID，0表示顶级',
  `path` varchar(200) DEFAULT NULL COMMENT '路由路径（菜单）或接口路径（API）',
  `method` varchar(10) DEFAULT NULL COMMENT 'HTTP方法: GET/POST/PUT/DELETE',
  `icon` varchar(50) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT 0 COMMENT '排序',
  `status` int DEFAULT 1 COMMENT '状态: 1启用 0禁用',
  `mask_type` int DEFAULT NULL COMMENT '脱敏类型: 0不脱敏 1置空 2脱星 3替换',
  `mask_value` varchar(100) DEFAULT NULL COMMENT '脱敏替换值',
  `description` varchar(200) DEFAULT NULL COMMENT '权限描述',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `deleted` int DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_type` (`type`),
  KEY `idx_module` (`module`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限定义表';

-- ----------------------------
-- 角色权限关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `deleted` int DEFAULT 0 COMMENT '软删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';
