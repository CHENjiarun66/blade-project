-- V14: 权限系统预置数据
-- 插入预置角色和权限数据

-- ----------------------------
-- 插入预置角色
-- ----------------------------
INSERT INTO `sys_role` (`role_name`, `role_code`, `description`, `tenant_id`, `status`) VALUES
('老板/经理', 'ROLE_OWNER', '查看所有数据', 1, 1),
('销售员', 'ROLE_SALES', '负责订单和销售', 1, 1),
('仓库管理员', 'ROLE_WAREHOUSE', '管理库存和配送', 1, 1),
('财务', 'ROLE_FINANCE', '负责收款和金额相关', 1, 1),
('采购', 'ROLE_PURCHASE', '负责采购和商品入库', 1, 1),
('系统管理员', 'ROLE_ADMIN', '系统运维和用户管理', 1, 1)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- ----------------------------
-- 插入一级菜单权限
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `path`, `icon`, `sort`, `tenant_id`) VALUES
('仪表盘', 'menu:dashboard', 1, 'dashboard', 0, '/dashboard', 'dashboard', 1, 1),
('订单管理', 'menu:order', 1, 'order', 0, '/orders', 'receipt_long', 10, 1),
('库存管理', 'menu:inventory', 1, 'inventory', 0, '/inventory', 'inventory', 20, 1),
('商品管理', 'menu:product', 1, 'product', 0, '/products', 'category', 30, 1),
('客户管理', 'menu:customer', 1, 'customer', 0, '/clients', 'people', 40, 1),
('系统管理', 'menu:system', 1, 'system', 0, '/system', 'settings', 100, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入商品管理子菜单
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `path`, `sort`, `tenant_id`) VALUES
('商品列表', 'menu:product:list', 1, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), '/products', 1, 1),
('颜色管理', 'menu:product:colors', 1, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), '/products/colors', 2, 1),
('尺码管理', 'menu:product:sizes', 1, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), '/products/sizes', 3, 1),
('分类管理', 'menu:product:categories', 1, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), '/products/categories', 4, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入订单按钮权限
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`) VALUES
('新建订单', 'btn:order:create', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 1, 1),
('编辑订单', 'btn:order:edit', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 2, 1),
('删除订单', 'btn:order:delete', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 3, 1),
('确认付款', 'btn:order:confirmPayment', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 4, 1),
('订单发货', 'btn:order:deliver', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 5, 1),
('取消订单', 'btn:order:cancel', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 6, 1),
('查看订单详情', 'btn:order:view', 2, 'order', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:order') AS t), 7, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入库存按钮权限
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`) VALUES
('入库操作', 'btn:inventory:in', 2, 'inventory', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:inventory') AS t), 1, 1),
('出库操作', 'btn:inventory:out', 2, 'inventory', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:inventory') AS t), 2, 1),
('库存调整', 'btn:inventory:adjust', 2, 'inventory', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:inventory') AS t), 3, 1),
('查看库存记录', 'btn:inventory:viewLog', 2, 'inventory', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:inventory') AS t), 4, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入商品按钮权限
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`) VALUES
('新建商品', 'btn:product:create', 2, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), 10, 1),
('编辑商品', 'btn:product:edit', 2, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), 11, 1),
('删除商品', 'btn:product:delete', 2, 'product', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:product') AS t), 12, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入客户按钮权限
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`) VALUES
('新建客户', 'btn:customer:create', 2, 'customer', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:customer') AS t), 1, 1),
('编辑客户', 'btn:customer:edit', 2, 'customer', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:customer') AS t), 2, 1),
('删除客户', 'btn:customer:delete', 2, 'customer', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:customer') AS t), 3, 1),
('查看客户订单', 'btn:customer:viewOrders', 2, 'customer', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:customer') AS t), 4, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入系统管理按钮权限
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `parent_id`, `sort`, `tenant_id`) VALUES
('用户管理', 'btn:system:user', 2, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 1, 1),
('角色管理', 'btn:system:role', 2, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 2, 1),
('权限配置', 'btn:system:permission', 2, 'system', (SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'menu:system') AS t), 3, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ----------------------------
-- 插入字段权限（用于数据脱敏）
-- ----------------------------
INSERT INTO `sys_permission` (`name`, `code`, `type`, `module`, `mask_type`, `description`, `tenant_id`) VALUES
('成本价字段', 'field:cost_price', 3, 'product', 1, '采购成本价，仅老板/采购/系统管理员可见', 1),
('销售价字段', 'field:sale_price', 3, 'order', 0, '订单销售价，老板/销售/财务可见', 1),
('利润字段', 'field:profit', 3, 'order', 1, '利润=销售价-成本价，仅老板/财务可见', 1),
('配送数量字段', 'field:delivery_qty', 3, 'order', 0, '配送数量，老板/销售/仓库可见', 1),
('收款金额字段', 'field:paid_amount', 3, 'order', 0, '已收款金额，老板/财务可见', 1),
('定金金额字段', 'field:deposit_amount', 3, 'order', 0, '定金金额，老板/财务可见', 1)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- ----------------------------
-- 插入角色-权限关联
-- 老板 ROLE_OWNER（拥有所有菜单和按钮权限）
-- ----------------------------
-- 先插入菜单权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_OWNER' AND p.type = 1 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 插入按钮权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_OWNER' AND p.type = 2 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- 插入字段权限（老板可见全部字段）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_OWNER' AND p.type = 3 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- ----------------------------
-- 销售员 ROLE_SALES
-- 菜单：仪表盘、订单、客户、商品（只读）、库存（只读）
-- 按钮：新建/编辑订单、取消订单
-- 字段：销售价、配送数量
-- ----------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES' AND p.code IN (
  'menu:dashboard', 'menu:order', 'menu:customer', 'menu:product', 'menu:inventory'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES' AND p.code IN (
  'btn:order:create', 'btn:order:edit', 'btn:order:cancel', 'btn:order:view'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_SALES' AND p.code IN (
  'field:sale_price', 'field:delivery_qty'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- ----------------------------
-- 仓库管理员 ROLE_WAREHOUSE
-- 菜单：仪表盘、库存
-- 按钮：入库、出库、调整、查看库存记录
-- 字段：配送数量
-- ----------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_WAREHOUSE' AND p.code IN (
  'menu:dashboard', 'menu:inventory'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_WAREHOUSE' AND p.code IN (
  'btn:inventory:in', 'btn:inventory:out', 'btn:inventory:adjust', 'btn:inventory:viewLog'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_WAREHOUSE' AND p.code = 'field:delivery_qty'
AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- ----------------------------
-- 财务 ROLE_FINANCE
-- 菜单：仪表盘、订单
-- 按钮：确认付款、查看订单详情
-- 字段：销售价、利润、收款金额、定金金额
-- ----------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_FINANCE' AND p.code IN (
  'menu:dashboard', 'menu:order'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_FINANCE' AND p.code IN (
  'btn:order:confirmPayment', 'btn:order:view'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_FINANCE' AND p.code IN (
  'field:sale_price', 'field:profit', 'field:paid_amount', 'field:deposit_amount'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- ----------------------------
-- 采购 ROLE_PURCHASE
-- 菜单：仪表盘、商品、库存
-- 按钮：新建/编辑商品、入库
-- 字段：成本价
-- ----------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_PURCHASE' AND p.code IN (
  'menu:dashboard', 'menu:product', 'menu:inventory'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_PURCHASE' AND p.code IN (
  'btn:product:create', 'btn:product:edit', 'btn:inventory:in'
) AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_PURCHASE' AND p.code = 'field:cost_price'
AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- ----------------------------
-- 系统管理员 ROLE_ADMIN（拥有所有权限）
-- ----------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_ADMIN' AND p.type = 1 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_ADMIN' AND p.type = 2 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r, `sys_permission` p
WHERE r.role_code = 'ROLE_ADMIN' AND p.type = 3 AND p.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- ----------------------------
-- 为 admin 用户分配系统管理员角色
-- ----------------------------
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `tenant_id`)
SELECT u.id, r.id, u.tenant_id FROM `sys_user` u, `sys_role` r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN' AND u.deleted = 0 AND r.deleted = 0
ON DUPLICATE KEY UPDATE `user_id` = `user_id`;
