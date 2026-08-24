-- BE-567/BE-572/BA-1101: Collector 独立鉴权与采集完整性工作台

CREATE TABLE `wa_collector_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `account_id` bigint NOT NULL COMMENT '绑定的WhatsApp账号ID',
  `name` varchar(100) NOT NULL COMMENT '采集器名称',
  `key_prefix` varchar(24) NOT NULL COMMENT '公开密钥前缀',
  `key_hash` varchar(100) NOT NULL COMMENT 'BCrypt密钥摘要',
  `scopes` varchar(500) NOT NULL COMMENT '逗号分隔scope',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `expires_time` datetime(3) DEFAULT NULL,
  `last_used_time` datetime(3) DEFAULT NULL,
  `last_used_ip` varchar(64) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wa_collector_prefix` (`key_prefix`),
  KEY `idx_wa_collector_account` (`tenant_id`, `account_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WhatsApp Collector独立凭证';

CREATE TABLE `wa_collection_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL COMMENT '最近检测批次',
  `conversation_id` bigint DEFAULT NULL,
  `message_id` bigint DEFAULT NULL,
  `media_id` bigint DEFAULT NULL,
  `issue_key_hash` binary(32) NOT NULL COMMENT '稳定问题键SHA-256',
  `issue_type` varchar(32) NOT NULL COMMENT 'MEDIA_PATH_EMPTY/MEDIA_FILE_MISSING/MEDIA_SIZE_MISMATCH/MEDIA_READ_FAILED',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/IGNORED',
  `severity` varchar(16) NOT NULL DEFAULT 'WARNING',
  `media_type` varchar(20) DEFAULT NULL,
  `occurrence_count` int NOT NULL DEFAULT 1,
  `first_detected_at` datetime(3) NOT NULL,
  `last_detected_at` datetime(3) NOT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  `detail_json` json DEFAULT NULL COMMENT '白名单诊断字段，不含绝对路径',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wa_issue_key` (`tenant_id`, `account_id`, `issue_key_hash`),
  KEY `idx_wa_issue_status` (`tenant_id`, `status`, `media_type`, `last_detected_at`),
  KEY `idx_wa_issue_conversation` (`tenant_id`, `conversation_id`, `status`),
  KEY `idx_wa_issue_batch` (`tenant_id`, `account_id`, `batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WhatsApp Mac端采集完整性问题';

CREATE TABLE `wa_scan_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/SUCCEEDED/FAILED/CANCELLED',
  `requested_by` bigint DEFAULT NULL,
  `requested_at` datetime(3) NOT NULL,
  `claimed_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `collector_instance_hash` binary(32) DEFAULT NULL,
  `result_batch_id` bigint DEFAULT NULL,
  `error_summary` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_wa_scan_job_poll` (`tenant_id`, `account_id`, `status`, `requested_at`),
  KEY `idx_wa_scan_job_recent` (`tenant_id`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WhatsApp采集器重扫任务';

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `path`, `icon`, `sort`, `tenant_id`)
VALUES
  ('WhatsApp归档', 'menu:whatsapp', 1, 'whatsapp', 0, '/whatsapp', 'chat', 46, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `path` = VALUES(`path`), `icon` = VALUES(`icon`);

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES
  ('创建采集器凭证', 'btn:whatsapp:collector', 2, 'whatsapp', (SELECT id FROM (SELECT id FROM sys_permission WHERE code='menu:whatsapp' AND tenant_id=1) p), 1, 1),
  ('触发重新扫描', 'btn:whatsapp:rescan', 2, 'whatsapp', (SELECT id FROM (SELECT id FROM sys_permission WHERE code='menu:whatsapp' AND tenant_id=1) p), 2, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `parent_id` = VALUES(`parent_id`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`, `deleted`, `create_time`)
SELECT r.id, p.id, r.tenant_id, 0, NOW()
FROM `sys_role` r
INNER JOIN `sys_permission` p ON p.tenant_id = r.tenant_id
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND r.deleted = 0 AND p.deleted = 0
  AND p.code IN ('menu:whatsapp', 'btn:whatsapp:collector', 'btn:whatsapp:rescan')
ON DUPLICATE KEY UPDATE `tenant_id` = VALUES(`tenant_id`), `deleted` = 0;
