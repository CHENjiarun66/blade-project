-- V10: 订单表添加销售人员字段

-- 给 sale_order 表添加 salesman_id 字段
ALTER TABLE `sale_order` ADD COLUMN `salesman_id` bigint DEFAULT NULL COMMENT '开单销售人员ID' AFTER `warehouse_id`;

-- 添加备注说明
ALTER TABLE `sale_order` COMMENT '订单表 - 已添加 salesman_id 关联 sys_user.id';
