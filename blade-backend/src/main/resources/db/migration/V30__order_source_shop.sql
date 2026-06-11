-- V30: 订单来源档口/店铺

ALTER TABLE `sale_order`
ADD COLUMN `source_shop` varchar(100) DEFAULT NULL COMMENT '订单来源档口/店铺' AFTER `source_doc_no`;

CREATE INDEX `idx_source_shop` ON `sale_order` (`source_shop`);
