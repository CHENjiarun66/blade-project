-- V51: 订单生命周期、收款状态、履约方式、金额快照、财务流水与状态流转日志
-- 加法迁移：不修改 V1-V50；不为历史订单自动推断新状态（由 SOW-7 离线迁移工具按证据写入）。
-- 兼容期旧 status/payment_status 保留，仅由 OrderCompatAdapter 在同一事务内投影。

ALTER TABLE `sale_order`
  ADD COLUMN `fulfillment_status` varchar(32) NULL COMMENT '履约状态: CONFIRMED/WAITING_ALLOCATION/ALLOCATING/READY_TO_SHIP/SHIPPED/COMPLETED/CANCELLED' AFTER `status`,
  ADD COLUMN `collection_status` varchar(16) NULL COMMENT '收款状态: UNPAID/PARTIAL/SETTLED' AFTER `payment_status`,
  ADD COLUMN `fulfillment_mode` varchar(24) NOT NULL DEFAULT 'UNDECIDED' COMMENT '履约方式: UNDECIDED/STOCK_LINKED/RECORD_ONLY' AFTER `collection_status`,
  ADD COLUMN `fulfillment_decided_at` datetime NULL COMMENT '履约方式确认时间' AFTER `fulfillment_mode`,
  ADD COLUMN `fulfillment_decided_by` bigint NULL COMMENT '履约方式确认人' AFTER `fulfillment_decided_at`,
  ADD COLUMN `settled_at` datetime NULL COMMENT '首次达到已结清的时间' AFTER `fulfillment_decided_by`,
  ADD COLUMN `settlement_method` varchar(32) NULL COMMENT '结清方式: FULL_RECEIPT/WRITE_OFF/MIGRATION_CONFIRMED' AFTER `settled_at`,
  ADD COLUMN `gross_received_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计实收（Σ 有效 RECEIPT）' AFTER `settlement_method`,
  ADD COLUMN `cash_refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计现金退款（Σ 有效 REFUND）' AFTER `gross_received_amount`,
  ADD COLUMN `sales_return_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '销售退回（价值减少，非现金）' AFTER `cash_refund_amount`,
  ADD COLUMN `net_received_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '净实收快照 = max(累计实收-现金退款, 0)' AFTER `sales_return_amount`,
  ADD COLUMN `balance_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '当前尾款快照' AFTER `net_received_amount`,
  ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观并发版本' AFTER `balance_amount`;

ALTER TABLE `sale_order`
  ADD KEY `idx_so_tenant_fulfillment` (`tenant_id`, `fulfillment_status`),
  ADD KEY `idx_so_tenant_collection` (`tenant_id`, `collection_status`),
  ADD KEY `idx_so_tenant_settled` (`tenant_id`, `settled_at`),
  ADD CONSTRAINT `chk_so_snapshots_nonnegative` CHECK (
    `gross_received_amount` >= 0
    AND `cash_refund_amount` >= 0
    AND `sales_return_amount` >= 0
    AND `net_received_amount` >= 0
    AND `balance_amount` >= 0
    AND `write_off_amount` >= 0
  );

-- 财务流水：只追加，纠错只能追加 REVERSAL；实体与服务不提供更新/软删/物理删除能力。
CREATE TABLE `order_financial_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `record_type` varchar(24) NOT NULL COMMENT 'RECEIPT收款/WRITE_OFF短款核销/REFUND现金退款/REVERSAL冲销/MIGRATION_OPENING迁移期初',
  `amount` decimal(12,2) NOT NULL COMMENT '本次金额，恒为正数',
  `payment_method` varchar(32) NULL COMMENT '现金、转账等方式，可空',
  `occurred_at` datetime(3) NOT NULL COMMENT '业务发生时间（现金流统计口径）',
  `operator_id` bigint NULL COMMENT 'MIGRATION_OPENING 历史迁移允许 NULL',
  `operator_name` varchar(64) NULL,
  `reason` varchar(255) NULL COMMENT '核销/退款/冲销原因',
  `source` varchar(24) NOT NULL COMMENT 'PC/MOBILE/AGENT/MIGRATION',
  `idempotency_key` varchar(64) NULL COMMENT '请求幂等键，按租户全局唯一',
  `reversed_record_id` bigint unsigned NULL COMMENT '仅 REVERSAL 可填写，指向被冲销流水',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '仅满足项目字段规范；不提供软删能力',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ofr_tenant_order` (`tenant_id`, `order_id`, `occurred_at`),
  KEY `idx_ofr_tenant_type_time` (`tenant_id`, `record_type`, `occurred_at`),
  UNIQUE KEY `uk_ofr_idempotency` (`tenant_id`, `idempotency_key`),
  UNIQUE KEY `uk_ofr_reversal` (`tenant_id`, `reversed_record_id`),
  CONSTRAINT `chk_ofr_amount_positive` CHECK (`amount` > 0),
  CONSTRAINT `chk_ofr_reversal_shape` CHECK (
    (`record_type` = 'REVERSAL' AND `reversed_record_id` IS NOT NULL)
    OR (`record_type` <> 'REVERSAL' AND `reversed_record_id` IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单财务流水（只追加，不可变）';

-- 状态流转日志：只追加；幂等键按租户全局唯一，由数据库唯一约束承接并发重复请求。
CREATE TABLE `order_state_transition_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `action` varchar(48) NOT NULL COMMENT 'confirmDraft/recordPayment/settleWithWriteOff/refundPayment/reverseFinancialRecord/chooseFulfillmentMode/startAllocation/confirmAllocation/shipOrder/completeOrder/cancelOrder/migrate',
  `from_fulfillment_status` varchar(32) NULL,
  `to_fulfillment_status` varchar(32) NULL,
  `from_collection_status` varchar(16) NULL,
  `to_collection_status` varchar(16) NULL,
  `from_fulfillment_mode` varchar(24) NULL,
  `to_fulfillment_mode` varchar(24) NULL,
  `operator_id` bigint NULL,
  `operator_name` varchar(64) NULL,
  `source` varchar(24) NOT NULL COMMENT 'PC/MOBILE/AGENT/MIGRATION',
  `reason` varchar(255) NULL,
  `idempotency_key` varchar(64) NULL COMMENT '按租户全局唯一',
  `occurred_at` datetime(3) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ostl_tenant_order` (`tenant_id`, `order_id`, `occurred_at`),
  UNIQUE KEY `uk_ostl_idempotency` (`tenant_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志（只追加）';

SELECT 'V51 订单生命周期与财务 schema 完成' AS status;
