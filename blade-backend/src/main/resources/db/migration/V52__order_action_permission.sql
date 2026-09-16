-- V52: 订单动作与财务权限
-- 权限种子幂等写入；角色赋权全部按同租户 JOIN（规避 V41 曾发生的跨租户关联问题，参照 V42 修复模式）。
-- 不包含任何迁移权限或迁移端点（SOW-7 迁移工具为离线程序）。

-- ----------------------------
-- 权限定义（type=2 按钮，挂在 menu:order 下）
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('确认收款', 'btn:order:recordPayment', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 8, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('短款核销', 'btn:order:writeOff', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 9, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('现金退款', 'btn:order:refund', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 10, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('冲销财务流水', 'btn:order:reverse', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 11, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('履约方式选择', 'btn:order:chooseFulfillment', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 12, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('配货管理', 'btn:order:allocate', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 13, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('订单导出', 'btn:order:export', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 14, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('财务查看', 'btn:order:viewFinance', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 15, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

-- ----------------------------
-- 角色赋权（同租户 JOIN + 幂等）
-- OWNER/ADMIN：全部正常订单动作
-- FINANCE：收款、核销、退款、冲销、导出、财务查看
-- SALES：收款、导出（viewFinance 待数据范围过滤与字段裁剪落地后由后续迁移赋权）
-- WAREHOUSE：履约方式选择、配货管理、发货（deliver 为存量 code）
-- ----------------------------

-- OWNER + ADMIN 全量
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code IN (
    'btn:order:recordPayment', 'btn:order:writeOff', 'btn:order:refund', 'btn:order:reverse',
    'btn:order:chooseFulfillment', 'btn:order:allocate', 'btn:order:export', 'btn:order:viewFinance'
  )
  AND r.tenant_id = p.tenant_id
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- FINANCE
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_FINANCE'
  AND p.code IN (
    'btn:order:recordPayment', 'btn:order:writeOff', 'btn:order:refund', 'btn:order:reverse',
    'btn:order:export', 'btn:order:viewFinance'
  )
  AND r.tenant_id = p.tenant_id
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- SALES
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES'
  AND p.code IN ('btn:order:recordPayment', 'btn:order:export')
  AND r.tenant_id = p.tenant_id
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- WAREHOUSE
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_WAREHOUSE'
  AND p.code IN ('btn:order:chooseFulfillment', 'btn:order:allocate', 'btn:order:deliver')
  AND r.tenant_id = p.tenant_id
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

SELECT 'V52 订单动作与财务权限完成' AS status;
