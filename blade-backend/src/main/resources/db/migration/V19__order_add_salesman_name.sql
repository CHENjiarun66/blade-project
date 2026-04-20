-- V19: 订单表添加销售人员名称字段
-- 用于存储开单人员姓名，避免跨租户查询 sys_user 表

ALTER TABLE `sale_order` ADD COLUMN `salesman_name` varchar(100) DEFAULT NULL COMMENT '开单销售人员姓名' AFTER `salesman_id`;
