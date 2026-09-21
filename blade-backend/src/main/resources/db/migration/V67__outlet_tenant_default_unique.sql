-- V67: 租户默认档口数据库不变量（第二批B）
--
-- 目标：每个 tenant 的“未删除且 is_tenant_default=1”最多一条，由数据库唯一索引兜底，
-- 不再只依赖服务层事务。
--
-- 做法：在同一 ALTER TABLE 语句内新增 STORED 生成列 tenant_default_guard：
--   deleted=0 且 is_tenant_default=1 时为 1，否则为 NULL；
-- 并对 (tenant_id, tenant_default_guard) 建唯一索引。MySQL 唯一索引忽略 NULL，
-- 因此非默认/已删除行不参与冲突。
--
-- fail-closed：若历史上同一 tenant 已存在多条未删除默认档口，本迁移会因唯一键冲突失败，
-- 不会自动选择或清理任何一条。上线前必须先执行以下检查；若返回行数 > 0，必须由人工确认
-- 保留哪一条、将其余显式置 0 后再重新执行迁移：
--   SELECT tenant_id, COUNT(*) AS default_cnt
--     FROM sales_outlet
--    WHERE deleted = 0 AND is_tenant_default = 1
--    GROUP BY tenant_id
--   HAVING COUNT(*) > 1;
--
-- 只读检查参考 docs/20-OUTLET_ACCESS_CONTROL_DESIGN.md 与发布 checklist。
ALTER TABLE `sales_outlet`
  ADD COLUMN `tenant_default_guard` tinyint GENERATED ALWAYS AS (
    CASE WHEN `deleted` = 0 AND `is_tenant_default` = 1 THEN 1 ELSE NULL END
  ) STORED COMMENT '每租户唯一默认档口守卫：默认=1，其余=NULL',
  ADD UNIQUE KEY `uk_outlet_tenant_default` (`tenant_id`, `tenant_default_guard`);

SELECT 'V67 租户默认档口唯一性迁移完成' AS status;
