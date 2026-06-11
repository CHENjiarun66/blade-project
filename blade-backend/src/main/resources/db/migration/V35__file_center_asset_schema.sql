-- ================================================================
-- BE-1001: 数字资产表结构扩展
-- 1. 扩展 file_storage：新增核心资产字段和基础媒体元数据字段
-- 2. 新增 file_folder：用户自建文件夹
-- 3. 新增 file_business_bind：多业务绑定表
-- 4. 新增 file_operation_log：操作日志
-- 5. 新增 file_cleanup_log：清理日志
-- ================================================================

-- 1. 扩展 file_storage
ALTER TABLE `file_storage`
  ADD COLUMN `folder_id` bigint DEFAULT NULL COMMENT '所属文件夹ID' AFTER `business_id`,
  ADD COLUMN `file_type` varchar(20) DEFAULT NULL COMMENT '文件类型: IMAGE/VIDEO/DOCUMENT/ARCHIVE/OTHER' AFTER `folder_id`,
  ADD COLUMN `file_ext` varchar(20) DEFAULT NULL COMMENT '文件扩展名' AFTER `file_type`,
  ADD COLUMN `file_hash` varchar(64) DEFAULT NULL COMMENT '文件SHA256哈希' AFTER `file_ext`,
  ADD COLUMN `source` varchar(30) DEFAULT NULL COMMENT '上传来源: admin/mobile/ocr/import' AFTER `file_hash`,
  ADD COLUMN `purpose` varchar(30) DEFAULT NULL COMMENT '文件用途: product/sku/order/inventory/temp' AFTER `source`,
  ADD COLUMN `bind_count` int NOT NULL DEFAULT 0 COMMENT '有效绑定数量冗余' AFTER `purpose`,
  ADD COLUMN `visibility` varchar(20) DEFAULT 'PRIVATE' COMMENT '可见性: PUBLIC/PRIVATE' AFTER `bind_count`,
  ADD COLUMN `image_width` int DEFAULT NULL COMMENT '图片宽度(px)' AFTER `visibility`,
  ADD COLUMN `image_height` int DEFAULT NULL COMMENT '图片高度(px)' AFTER `image_width`,
  ADD COLUMN `duration_seconds` int DEFAULT NULL COMMENT '视频时长(秒)' AFTER `image_height`,
  ADD COLUMN `cover_file_id` bigint DEFAULT NULL COMMENT '视频封面fileId' AFTER `duration_seconds`,
  ADD COLUMN `deleted_time` datetime DEFAULT NULL COMMENT '软删除时间' AFTER `update_time`,
  ADD COLUMN `purged_time` datetime DEFAULT NULL COMMENT '物理删除时间' AFTER `deleted_time`;

-- 2. file_folder
CREATE TABLE `file_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件夹ID',
  `parent_id` bigint DEFAULT NULL COMMENT '父文件夹ID',
  `folder_name` varchar(128) NOT NULL COMMENT '文件夹名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件文件夹表';

-- 3. file_business_bind
CREATE TABLE `file_business_bind` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` bigint NOT NULL COMMENT '文件ID',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型: product/sku/order/inventory_log/ocr_document',
  `business_id` bigint NOT NULL COMMENT '业务对象ID',
  `bind_role` varchar(30) DEFAULT NULL COMMENT '绑定角色: main/gallery/sku_image/receipt/source/attachment',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `is_primary` tinyint NOT NULL DEFAULT 0 COMMENT '是否主图: 0否 1是',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_business` (`business_type`, `business_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件业务绑定表';

-- 4. file_operation_log
CREATE TABLE `file_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` bigint DEFAULT NULL COMMENT '操作的文件ID',
  `operation_type` varchar(30) NOT NULL COMMENT '操作类型: upload/bind/unbind/move/delete/restore/purge',
  `detail` varchar(500) DEFAULT NULL COMMENT '操作详情',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件操作日志表';

-- 5. file_cleanup_log
CREATE TABLE `file_cleanup_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` bigint NOT NULL COMMENT '清理的文件ID',
  `cleanup_type` varchar(30) NOT NULL COMMENT '清理类型: soft_delete/physical_delete',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
  `file_size` bigint DEFAULT 0 COMMENT '文件大小',
  `reason` varchar(200) DEFAULT NULL COMMENT '清理原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人(自动任务时为null)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件清理日志表';
