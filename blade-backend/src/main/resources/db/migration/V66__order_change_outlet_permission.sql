-- V66: Series D 订单改档口高权限（btn:order:changeOutlet）
--
-- 变更正式订单来源档口属于高风险业务动作，只授予 OWNER/ADMIN；后端不依赖前端隐藏。
-- 迁移幂等：沿用 V64/V65 恢复语义（ON DUPLICATE KEY UPDATE 恢复 tenant_id/deleted）。
-- 不新增权限类型，仍为 type=2 业务按钮，挂在 menu:order 下。

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `status`, `description`, `tenant_id`)
VALUES ('修改订单档口', 'btn:order:changeOutlet', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order' LIMIT 1) AS t),
        30, 1, '修改正式订单来源档口（需填写原因并写审计）', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`),
  `description` = VALUES(`description`), `status` = 1, `deleted` = 0;

-- ── 角色赋权：仅 OWNER/ADMIN ───────────────────────────────────────────
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code = 'btn:order:changeOutlet'
  AND r.deleted = 0 AND r.status = 1 AND p.deleted = 0 AND p.status = 1
ON DUPLICATE KEY UPDATE `tenant_id` = VALUES(`tenant_id`), `deleted` = 0;

SELECT 'V66 订单改档口高权限迁移完成' AS status;
