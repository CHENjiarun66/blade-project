-- V8: 订单新增支付状态和配送设置字段
-- 新增字段：payment_status, deposit_amount, need_delivery, delivery_address, is_delivered, delivered_at

-- ----------------------------
-- 1. 新增支付状态字段
-- ----------------------------
ALTER TABLE `sale_order` ADD COLUMN `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态: 0未付款 1已付定金 2已付全款' AFTER `paid_amount`;

-- ----------------------------
-- 2. 新增定金金额字段
-- ----------------------------
ALTER TABLE `sale_order` ADD COLUMN `deposit_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '定金金额' AFTER `payment_status`;

-- ----------------------------
-- 3. 新增配送设置字段
-- ----------------------------
ALTER TABLE `sale_order` ADD COLUMN `need_delivery` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要送货: 0否 1是' AFTER `deposit_amount`;
ALTER TABLE `sale_order` ADD COLUMN `delivery_address` VARCHAR(255) DEFAULT NULL COMMENT '送货地址' AFTER `need_delivery`;
ALTER TABLE `sale_order` ADD COLUMN `is_delivered` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已送货: 0否 1是' AFTER `delivery_address`;
ALTER TABLE `sale_order` ADD COLUMN `delivered_at` DATETIME DEFAULT NULL COMMENT '送货时间' AFTER `is_delivered`;

-- ----------------------------
-- 4. 验证字段
-- ----------------------------
SELECT 'V8 订单支付状态和配送设置字段新增完成' AS status;
DESCRIBE sale_order;
