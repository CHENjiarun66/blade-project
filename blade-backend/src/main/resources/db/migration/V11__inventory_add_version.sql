-- V11: 库存表添加乐观锁版本号
-- 用于解决并发控制问题，防止超卖

ALTER TABLE `inventory` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `alert_threshold`;

-- 为已存在的记录初始化版本号
UPDATE `inventory` SET `version` = 0 WHERE `version` IS NULL OR `version` = 0;
