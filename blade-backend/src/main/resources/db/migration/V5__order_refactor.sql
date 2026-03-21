-- V5: 订单模块重构（对接库存联动）

-- ----------------------------
-- 更新订单表 product_order
-- ----------------------------
ALTER TABLE `product_order`
ADD COLUMN `customer_id` bigint DEFAULT NULL COMMENT '客户ID' AFTER `order_no`,
ADD COLUMN `paid_amount` decimal(12,2) DEFAULT 0 COMMENT '已支付金额' AFTER `total_amount`,
ADD COLUMN `warehouse_id` bigint DEFAULT NULL COMMENT '发货仓库' AFTER `status`,
ADD COLUMN `pay_time` datetime DEFAULT NULL COMMENT '支付时间' AFTER `update_time`,
ADD COLUMN `confirm_time` datetime DEFAULT NULL COMMENT '确认时间' AFTER `pay_time`,
ADD COLUMN `deliver_time` datetime DEFAULT NULL COMMENT '发货时间' AFTER `confirm_time`,
ADD COLUMN `complete_time` datetime DEFAULT NULL COMMENT '完成时间' AFTER `deliver_time`;

-- ----------------------------
-- 更新订单明细表 order_item
-- ----------------------------
ALTER TABLE `order_item`
ADD COLUMN `sku_code` varchar(50) DEFAULT NULL COMMENT 'SKU编码（冗余）' AFTER `sku_id`,
ADD COLUMN `color_name` varchar(20) DEFAULT NULL COMMENT '颜色（冗余）' AFTER `sku_code`,
ADD COLUMN `size_name` varchar(10) DEFAULT NULL COMMENT '尺码（冗余）' AFTER `color_name`,
MODIFY COLUMN `product_id` bigint NULL COMMENT '商品ID（旧字段，现使用sku_id）';
