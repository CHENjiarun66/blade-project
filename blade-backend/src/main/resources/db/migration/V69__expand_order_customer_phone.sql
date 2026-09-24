-- 正式订单需要保留草稿/快速录单中的国际电话号码快照。
-- 旧字段 varchar(11) 只能容纳国内纯数字号码，会导致带国家区号或格式符的号码确认失败。
-- 本迁移仅扩容，不改写任何历史订单值。
ALTER TABLE `sale_order`
  MODIFY COLUMN `customer_phone` varchar(50) DEFAULT NULL COMMENT '客户电话（允许国际区号）';
