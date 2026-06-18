-- ================================================================
-- BE-1012: 图片派生图底座
-- 1. 新增 file_derivative：原图 thumb/card 派生版本
-- ================================================================

CREATE TABLE `file_derivative` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '派生文件ID',
  `file_id` bigint NOT NULL COMMENT '原图 file_storage.id',
  `variant_type` varchar(32) NOT NULL COMMENT '派生类型: thumb / card',
  `storage_type` varchar(32) NOT NULL DEFAULT 'local' COMMENT '存储类型: local',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '派生文件物理路径',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
  `width` int DEFAULT NULL COMMENT '宽度(px)',
  `height` int DEFAULT NULL COMMENT '高度(px)',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / READY / FAILED',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_variant` (`file_id`, `variant_type`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_tenant_file` (`tenant_id`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件派生图表';
