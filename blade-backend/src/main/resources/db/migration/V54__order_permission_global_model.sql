-- V54: 订单动作权限多租户模型修正（终审第二轮 P0-5）
--
-- 权限模型确定：sys_permission 的 uk_code(code) 为全局唯一，权限定义是全局共享的
-- （每个 code 只有一行），与租户无关。租户差异体现在 sys_role（按租户）和
-- sys_role_permission.tenant_id（角色所在租户）。
--
-- 因此本迁移：
-- 1. 将 V52 种子的 tenant_id=1 行保留不动（它们就是全局定义），删除 V53 试图
--    按 (code, tenant) 复制产生的任何额外行（实际未成功插入，防御性清理）
-- 2. 重新对全部租户做角色赋权：不再要求 r.tenant_id = p.tenant_id（权限定义是全局的），
--    sys_role_permission.tenant_id 写角色的租户
-- 3. 全部幂等（ON DUPLICATE KEY UPDATE）

-- 防御性清理：删除 V53 可能产生的重复行（同 code 不同 tenant_id 的多余行，保留 id 最小）
DELETE p1 FROM `sys_permission` p1
JOIN `sys_permission` p2 ON p1.`code` = p2.`code` AND p1.`id` > p2.`id`
WHERE p1.`code` IN (
  'btn:order:recordPayment', 'btn:order:writeOff', 'btn:order:refund', 'btn:order:reverse',
  'btn:order:chooseFulfillment', 'btn:order:allocate', 'btn:order:export', 'btn:order:viewFinance'
);

-- 对全部租户的角色赋权（不再限制 r.tenant_id = p.tenant_id）

-- OWNER + ADMIN 全量
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
  AND p.code IN (
    'btn:order:recordPayment', 'btn:order:writeOff', 'btn:order:refund', 'btn:order:reverse',
    'btn:order:chooseFulfillment', 'btn:order:allocate', 'btn:order:export', 'btn:order:viewFinance'
  )
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
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- SALES
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES'
  AND p.code IN ('btn:order:recordPayment', 'btn:order:export')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- WAREHOUSE
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_WAREHOUSE'
  AND p.code IN ('btn:order:chooseFulfillment', 'btn:order:allocate', 'btn:order:deliver')
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 新增 viewAll 权限（数据范围：老板/管理员/财务可看全部订单）
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`)
VALUES ('查看全部订单', 'btn:order:viewAll', 2, 'order',
        (SELECT id FROM (SELECT id FROM `sys_permission` WHERE `code` = 'menu:order') AS t), 16, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT r.id, p.id, r.tenant_id
FROM `sys_role` r, `sys_permission` p
WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN', 'ROLE_FINANCE')
  AND p.code = 'btn:order:viewAll'
  AND r.deleted = 0 AND p.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

SELECT 'V54 订单动作权限多租户模型修正完成' AS status;
