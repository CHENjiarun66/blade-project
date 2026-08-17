-- SOW-3: 抹零/短款结清字段
-- Adds write_off_amount and write_off_reason to sale_order.
-- No index is added: current queries do not filter or sort by write-off amount.

ALTER TABLE sale_order
    ADD COLUMN write_off_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '抹零/短款结清金额' AFTER paid_amount,
    ADD COLUMN write_off_reason VARCHAR(255) NULL COMMENT '抹零/短款结清原因' AFTER write_off_amount;
