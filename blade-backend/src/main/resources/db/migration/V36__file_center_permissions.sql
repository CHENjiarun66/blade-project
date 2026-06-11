-- V36: 文件中心菜单与按钮权限

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `path`, `icon`, `sort`, `tenant_id`)
VALUES ('文件中心', 'menu:file', 1, 'file', 0, '/files', 'folder', 45, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES
('上传文件', 'btn:file:upload', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 1, 1),
('删除文件', 'btn:file:delete', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 2, 1),
('绑定业务对象', 'btn:file:bind', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 3, 1),
('解除绑定', 'btn:file:unbind', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 4, 1),
('批量操作', 'btn:file:batch', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 5, 1),
('查看全部文件', 'btn:file:viewAll', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 6, 1),
('查看自己上传', 'btn:file:viewOwn', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 7, 1),
('文件清理', 'btn:file:cleanup', 2, 'file', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:file') AS t), 8, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `sort` = VALUES(`sort`);

-- 第一版默认老板和系统管理员拥有完整文件中心权限。
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code IN (
    'menu:file',
    'btn:file:upload',
    'btn:file:delete',
    'btn:file:bind',
    'btn:file:unbind',
    'btn:file:batch',
    'btn:file:viewAll',
    'btn:file:viewOwn',
    'btn:file:cleanup'
  )
  AND r.deleted = 0
  AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
