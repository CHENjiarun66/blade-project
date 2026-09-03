-- V57: 收款动作与金额字段权限保持一致
-- 销售角色已有 recordPayment 动作；为避免允许收款却看不到当前实收/定金，补齐必要字段。
-- 仓库角色不授予任何价格或金额字段权限。

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES'
  AND p.code IN ('field:paid_amount', 'field:deposit_amount')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

SELECT 'V57 销售收款字段权限补齐完成' AS status;
