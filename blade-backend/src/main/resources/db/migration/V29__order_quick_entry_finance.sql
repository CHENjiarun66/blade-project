-- V29: PC快速录单、订单类型、运费与财务快照

ALTER TABLE `sale_order`
ADD COLUMN `order_date` date DEFAULT NULL COMMENT '订单日期（纸质单据日期）' AFTER `order_no`,
ADD COLUMN `source_doc_no` varchar(50) DEFAULT NULL COMMENT '纸质单据号/外部单号' AFTER `order_date`,
ADD COLUMN `order_type` varchar(20) NOT NULL DEFAULT 'SPOT' COMMENT '订单类型：SPOT现货/PREORDER订货' AFTER `source_doc_no`,
ADD COLUMN `freight_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '客户运费收入' AFTER `deposit_amount`,
ADD COLUMN `freight_cost` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '实际运费成本' AFTER `freight_amount`,
ADD COLUMN `total_cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '订单总成本（商品成本+运费成本）' AFTER `freight_cost`,
ADD COLUMN `gross_profit` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '订单毛利（商品毛利+运费收入-运费成本）' AFTER `total_cost_amount`;

ALTER TABLE `sale_order_item`
ADD COLUMN `cost_price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '下单成本价快照' AFTER `price`,
ADD COLUMN `cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '成本金额快照' AFTER `subtotal`,
ADD COLUMN `gross_profit` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '明细毛利快照' AFTER `cost_amount`;

CREATE INDEX `idx_order_type` ON `sale_order` (`order_type`);
CREATE INDEX `idx_order_date` ON `sale_order` (`order_date`);
CREATE INDEX `idx_source_doc_no` ON `sale_order` (`source_doc_no`);
