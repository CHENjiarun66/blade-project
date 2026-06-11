CREATE TABLE `agent_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent调用日志ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `agent_key_id` bigint NOT NULL COMMENT 'Agent Key ID',
  `key_prefix` varchar(64) NOT NULL COMMENT '公开key前缀',
  `method` varchar(10) NOT NULL COMMENT 'HTTP方法',
  `path` varchar(255) NOT NULL COMMENT '请求路径',
  `query_string` varchar(1000) DEFAULT NULL COMMENT '查询参数',
  `status` int DEFAULT NULL COMMENT 'HTTP状态码',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `ip` varchar(64) DEFAULT NULL COMMENT '来源IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_call_tenant_time` (`tenant_id`, `create_time`),
  KEY `idx_agent_call_key_time` (`agent_key_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Gateway调用日志';
