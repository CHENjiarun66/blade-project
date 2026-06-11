CREATE TABLE `agent_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent Key ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `name` varchar(100) NOT NULL COMMENT 'Agent Key名称',
  `key_prefix` varchar(64) NOT NULL COMMENT '公开key前缀',
  `key_hash` varchar(255) NOT NULL COMMENT 'key secret哈希',
  `scopes` varchar(500) NOT NULL COMMENT '逗号分隔权限范围',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
  `expires_time` datetime DEFAULT NULL COMMENT '过期时间',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',
  `last_used_ip` varchar(64) DEFAULT NULL COMMENT '最近使用IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_key_prefix` (`key_prefix`),
  KEY `idx_agent_key_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Gateway访问密钥';
