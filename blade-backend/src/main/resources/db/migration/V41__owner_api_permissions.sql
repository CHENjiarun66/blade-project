-- V41: ROLE_OWNER 补齐 API 权限
-- 背景：V15 只给 ROLE_ADMIN 分配了 type=4 的 API 权限（user:create 等），
-- 但 ROLE_OWNER 在 V14 已拥有全部菜单/按钮/字段权限（type 1/2/3）。
-- 这导致 ROLE_OWNER 在系统管理页能看到"新建用户/角色/权限"按钮，但调用接口时 403。
-- 修复：为 ROLE_OWNER 分配全部 type=4 API 权限，与 ROLE_ADMIN 对齐。

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_OWNER' AND p.type = 4 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
