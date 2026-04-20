-- V15: 添加 API 级别权限
-- 用户管理 API 权限
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `method`, `sort`, `tenant_id`) VALUES
('创建用户', 'user:create', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'POST', 1, 1),
('更新用户', 'user:update', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'PUT', 2, 1),
('删除用户', 'user:delete', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'DELETE', 3, 1),
('重置密码', 'user:password:reset', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'PUT', 4, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 角色管理 API 权限
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `method`, `sort`, `tenant_id`) VALUES
('创建角色', 'role:create', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'POST', 10, 1),
('更新角色', 'role:update', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'PUT', 11, 1),
('删除角色', 'role:delete', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'DELETE', 12, 1),
('分配权限', 'role:assign', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'POST', 13, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 权限管理 API 权限
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `method`, `sort`, `tenant_id`) VALUES
('创建权限', 'permission:create', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'POST', 20, 1),
('更新权限', 'permission:update', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'PUT', 21, 1),
('删除权限', 'permission:delete', 4, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 'DELETE', 22, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 为系统管理员角色分配 API 权限
-- ----------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_ADMIN' AND p.type = 4 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
