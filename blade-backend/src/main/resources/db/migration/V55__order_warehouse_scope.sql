-- V55: 仓库履约订单访问范围
-- 权限定义为全局共享。仓库角色需要读取全部待履约订单及其子资源，
-- 但仍不授予 viewFinance，财务流水继续由字段/动作权限隔离。

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_WAREHOUSE'
  AND p.code IN ('menu:order', 'btn:order:view', 'btn:order:viewAll')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `tenant_id` = VALUES(`tenant_id`), `deleted` = 0;

SELECT 'V55 仓库履约订单访问范围完成' AS status;
