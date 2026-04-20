-- V8: 订单表添加图片字段
-- 用于上传资质单、客户地址图等附件

ALTER TABLE sale_order ADD COLUMN images VARCHAR(1000) DEFAULT NULL COMMENT '订单图片，JSON数组格式，最多9张' AFTER remark;
