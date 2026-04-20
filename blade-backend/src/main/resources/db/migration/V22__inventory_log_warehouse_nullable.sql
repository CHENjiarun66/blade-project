-- V22: 修改 inventory_log 表 warehouse_id 字段允许为 NULL
-- 跨仓总量预留操作不绑定具体仓库，使用 NULL 表示

ALTER TABLE inventory_log MODIFY COLUMN warehouse_id bigint DEFAULT NULL COMMENT '仓库ID（跨仓预留时为NULL）';
