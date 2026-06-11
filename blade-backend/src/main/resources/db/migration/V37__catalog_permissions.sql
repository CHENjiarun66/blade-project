-- V37: Catalog permissions for iPad showroom

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `path`, `icon`, `sort`, `tenant_id`)
VALUES ('客户展示页', 'menu:catalog', 1, 'catalog', 0, '/catalog', 'photo_library', 50, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('查看展示数据', 'data:catalog:view', 2, 'catalog', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:catalog') AS t), 1, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `sort` = VALUES(`sort`);

-- Assign catalog permissions to ROLE_OWNER, ROLE_ADMIN, ROLE_SALES
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN', 'ROLE_SALES')
  AND p.code IN ('menu:catalog', 'data:catalog:view')
  AND r.deleted = 0
  AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
