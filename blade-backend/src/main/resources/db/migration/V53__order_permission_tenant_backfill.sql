-- V53: 订单动作权限多租户补齐（终审 P1-5）
-- V52 的权限种子只写了 tenant_id=1；本迁移为每个拥有订单菜单的租户补齐
-- 8 个订单动作权限按钮，并按 14.2 矩阵对全部租户重新执行同租户幂等赋权。
-- 不修改 V51/V52 已提交内容。

-- ----------------------------
-- 1. 按租户补齐权限定义（以各租户的 menu:order 为父级）
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '确认收款', 'btn:order:recordPayment', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       8, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:recordPayment' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '短款核销', 'btn:order:writeOff', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       9, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:writeOff' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '现金退款', 'btn:order:refund', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       10, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:refund' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '冲销财务流水', 'btn:order:reverse', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       11, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:reverse' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '履约方式选择', 'btn:order:chooseFulfillment', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       12, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:chooseFulfillment' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '配货管理', 'btn:order:allocate', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       13, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:allocate' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '订单导出', 'btn:order:export', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       14, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:export' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
SELECT '财务查看', 'btn:order:viewFinance', 2, 'order',
       (SELECT m.id FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id LIMIT 1),
       15, t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `sys_permission` WHERE code = 'menu:order' AND deleted = 0) t
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` p
  WHERE p.code = 'btn:order:viewFinance' AND p.tenant_id = t.tenant_id AND p.deleted = 0)
  AND EXISTS (SELECT 1 FROM `sys_permission` m WHERE m.code = 'menu:order' AND m.tenant_id = t.tenant_id AND m.deleted = 0);

-- ----------------------------
-- 2. 按矩阵对全部租户重新幂等赋权（同租户 JOIN）
-- OWNER/ADMIN：全部动作
-- ----------------------------
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

SELECT 'V53 订单动作权限多租户补齐完成' AS status;
