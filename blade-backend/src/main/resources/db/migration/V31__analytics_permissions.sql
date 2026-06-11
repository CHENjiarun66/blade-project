-- V31: 数据分析菜单与毛利数据权限

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `path`, `icon`, `sort`, `tenant_id`)
VALUES ('数据分析', 'menu:analytics', 1, 'analytics', 0, '/analytics', 'analytics', 2, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `mask_type`, `description`, `tenant_id`)
VALUES ('数据分析毛利字段', 'data:analytics:profit', 3, 'analytics',
        (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:analytics') AS t),
        1, '数据分析页成本、毛利、毛利率字段，仅老板/系统管理员默认可见', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`);

-- 老板和系统管理员拥有数据分析菜单与毛利数据权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code IN ('menu:analytics', 'data:analytics:profit')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 销售员默认可进入数据分析页，但不分配毛利数据权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES'
  AND p.code = 'menu:analytics'
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
