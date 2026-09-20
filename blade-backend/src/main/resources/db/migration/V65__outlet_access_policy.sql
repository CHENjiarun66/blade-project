-- V65: Series C 统一数据访问策略支撑：待归档档口权限 + 手工草稿创建人
--
-- 1) data:outlet:unassigned：仅 OWNER/ADMIN 可访问 source_outlet_id 为空的遗留订单/草稿。
--    FINANCE 拥有 data:outlet:all 也不自动获得该权限。
-- 2) order_draft.created_by_user_id：手工草稿创建用户；历史 NULL 表示遗留/待归档。
--    不回填历史行，不猜测创建人。
-- 权限沿用 V64 恢复语义（ON DUPLICATE KEY UPDATE 恢复 tenant_id/deleted）。

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `status`, `description`, `tenant_id`)
VALUES ('待归档档口数据', 'data:outlet:unassigned', 2, 'outlet',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
        12, 1, '可访问 source_outlet_id 为空的遗留订单/草稿', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`),
  `description` = VALUES(`description`), `status` = 1, `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code = 'data:outlet:unassigned'
  AND r.deleted = 0 AND r.status = 1 AND p.deleted = 0 AND p.status = 1
ON DUPLICATE KEY UPDATE `tenant_id` = VALUES(`tenant_id`), `deleted` = 0;

ALTER TABLE `order_draft`
  ADD COLUMN `created_by_user_id` bigint DEFAULT NULL COMMENT '手工草稿创建用户ID' AFTER `created_by_agent_key_id`,
  ADD KEY `idx_order_draft_tenant_outlet` (`tenant_id`, `source_outlet_id`, `status`),
  ADD KEY `idx_order_draft_tenant_creator` (`tenant_id`, `created_by_user_id`, `status`);

SELECT 'V65 待归档档口权限与草稿创建人迁移完成' AS status;
