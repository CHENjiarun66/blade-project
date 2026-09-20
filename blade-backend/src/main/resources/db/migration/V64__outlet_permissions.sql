-- V64: 档口主数据与数据权限（Series B1 权限编码与角色迁移）
--
-- 权限模型（遵循 V54）：sys_permission 的 uk_code(code) 全局唯一；权限定义全局共享；
-- sys_role_permission.tenant_id = 角色所在租户。全部幂等（ON DUPLICATE KEY UPDATE）。
-- 数据范围权限沿用 type=2 保存（编码 data:*），不新增权限类型。

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `path`, `icon`, `sort`, `status`, `tenant_id`)
VALUES ('档口管理', 'menu:outlet', 1, 'outlet', 0, '/outlets', 'storefront', 9, 1, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `sort` = VALUES(`sort`), `status` = 1, `deleted` = 0;

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `status`, `description`, `tenant_id`)
VALUES
  ('新建档口', 'btn:outlet:create', 2, 'outlet',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
   1, 1, '新建档口', 1),
  ('编辑档口', 'btn:outlet:edit', 2, 'outlet',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
   2, 1, '编辑档口', 1),
  ('启用/禁用档口', 'btn:outlet:disable', 2, 'outlet',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
   3, 1, '启用/禁用档口', 1),
  ('全部档口数据', 'data:outlet:all', 2, 'outlet',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
   10, 1, '可访问当前租户全部档口', 1),
  ('档口内全部人员订单', 'data:order:peopleAll', 2, 'outlet',
   (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
   11, 1, '在允许档口内查看全部人员订单', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`),
  `description` = VALUES(`description`), `status` = 1, `deleted` = 0;

INSERT INTO `sys_permission`
  (`name`, `code`, `type`, `module`, `parent_id`, `path`, `method`, `sort`, `status`, `description`, `tenant_id`)
VALUES ('Agent 档口选项', 'agent:outlets:read', 4, 'outlet',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:outlet' LIMIT 1) AS t),
        '/api/agent/outlets', 'GET', 20, 1, 'Agent 查询已授权档口选项', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
  `method` = VALUES(`method`), `sort` = VALUES(`sort`), `description` = VALUES(`description`),
  `status` = 1, `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code IN ('menu:outlet', 'btn:outlet:create', 'btn:outlet:edit', 'btn:outlet:disable',
                 'data:outlet:all', 'data:order:peopleAll')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_FINANCE'
  AND p.code IN ('menu:outlet', 'data:outlet:all', 'data:order:peopleAll')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT rp.role_id, p.id, rp.tenant_id
FROM `sys_role_permission` rp
JOIN `sys_permission` old ON old.id = rp.permission_id AND old.code = 'btn:order:viewAll'
JOIN `sys_permission` p ON p.code IN ('data:outlet:all', 'data:order:peopleAll');

SELECT 'V64 档口权限编码与角色迁移完成' AS status;
