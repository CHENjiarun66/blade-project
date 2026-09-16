ALTER TABLE `order_draft`
  ADD COLUMN `entry_source` varchar(20) NOT NULL DEFAULT 'AGENT' COMMENT '草稿来源：AGENT/MANUAL' AFTER `external_ref_no`,
  ADD COLUMN `source_shop` varchar(100) DEFAULT NULL COMMENT '来源档口/店铺' AFTER `source_order_no`,
  ADD COLUMN `order_type` varchar(20) DEFAULT NULL COMMENT '订单类型：SPOT/PREORDER' AFTER `source_shop`,
  ADD COLUMN `customer_country_code` varchar(10) DEFAULT NULL COMMENT '客户国家区号' AFTER `customer_phone`,
  ADD COLUMN `customer_address` varchar(255) DEFAULT NULL COMMENT '客户地址快照' AFTER `customer_country_code`,
  ADD COLUMN `paid_amount` decimal(12,2) DEFAULT NULL COMMENT '手工草稿初始实收金额' AFTER `deposit`,
  ADD COLUMN `freight_amount` decimal(12,2) DEFAULT NULL COMMENT '客户运费收入' AFTER `paper_total_amount`,
  ADD COLUMN `freight_cost` decimal(12,2) DEFAULT NULL COMMENT '实际运费成本' AFTER `freight_amount`,
  ADD COLUMN `need_delivery` tinyint DEFAULT NULL COMMENT '是否需要送货：0否/1是' AFTER `freight_cost`,
  ADD COLUMN `delivery_address` varchar(255) DEFAULT NULL COMMENT '送货地址' AFTER `need_delivery`;

ALTER TABLE `order_draft_item`
  ADD COLUMN `cost_price` decimal(12,2) DEFAULT NULL COMMENT '成本价快照' AFTER `sale_price`;

CREATE INDEX `idx_order_draft_tenant_source`
  ON `order_draft` (`tenant_id`, `entry_source`, `status`, `update_time`);
