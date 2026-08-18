-- V42: 修正 ROLE_OWNER API 权限关联的多租户正确性
-- 背景：V41 为 ROLE_OWNER 补齐 type=4 API 权限时，未限制 r.tenant_id = p.tenant_id，
-- 且 INSERT 未显式写入 tenant_id（sys_role_permission.tenant_id 落入建表默认值 1）。
-- 单租户（tenant_id=1）下无影响；多租户下会产生跨租户角色-权限关联（角色租户与权限租户不一致），
-- 以及关联行 tenant_id 与角色租户不一致的问题。
-- 本迁移（幂等）：
--   1) 清理 ROLE_OWNER 与 type=4 权限之间租户不匹配的错误关联；
--   2) 按 r.tenant_id = p.tenant_id 匹配重新补齐关联，并显式写入角色所在租户 ID。

-- 1. 清理跨租户 / tenant_id 错位的错误关联
DELETE rp FROM sys_role_permission rp
INNER JOIN sys_role r ON r.id = rp.role_id
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'ROLE_OWNER' AND p.type = 4
  AND (r.tenant_id <> p.tenant_id OR rp.tenant_id <> r.tenant_id);

-- 2. 按租户匹配补齐 ROLE_OWNER 的 API 权限关联，显式写入角色租户 ID
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`, `deleted`, `create_time`)
SELECT r.id, p.id, r.tenant_id, 0, NOW()
FROM `sys_role` r
INNER JOIN `sys_permission` p
  ON p.type = 4 AND p.deleted = 0 AND p.tenant_id = r.tenant_id
WHERE r.role_code = 'ROLE_OWNER' AND r.deleted = 0
ON DUPLICATE KEY UPDATE `tenant_id` = VALUES(`tenant_id`), `deleted` = 0;
