-- V58: Agent Key 安全签发、轮换与 Owner 管理权限

ALTER TABLE `agent_key`
  ADD COLUMN `created_by_user_id` bigint DEFAULT NULL COMMENT '签发用户ID' AFTER `last_used_ip`,
  ADD COLUMN `disabled_time` datetime DEFAULT NULL COMMENT '停用时间' AFTER `created_by_user_id`,
  ADD COLUMN `rotated_from_key_id` bigint DEFAULT NULL COMMENT '轮换来源Key ID' AFTER `disabled_time`,
  ADD KEY `idx_agent_key_rotated_from` (`rotated_from_key_id`);

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `method`, `sort`, `status`, `description`, `tenant_id`)
VALUES
  ('Agent Key 管理', 'btn:system:agentKey', 2, 'system',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:system' LIMIT 1) AS t),
   NULL, 4, 1, '显示 Agent Key 管理页签', 1),
  ('Agent Key 管理接口', 'agent-key:manage', 4, 'system',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:system' LIMIT 1) AS t),
   'ALL', 30, 1, '创建、轮换、停用并查看本租户 Agent Key', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `description` = VALUES(`description`), `sort` = VALUES(`sort`),
  `status` = 1, `deleted` = 0;

-- 仅 Owner 可以签发和管理外部机器凭证；按角色所属租户写入关联。
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r
JOIN `sys_permission` p ON p.code IN ('btn:system:agentKey', 'agent-key:manage')
WHERE r.role_code = 'ROLE_OWNER' AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `tenant_id` = VALUES(`tenant_id`), `deleted` = 0;
