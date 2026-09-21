-- V68: 用户/Agent Key 每主体一个有效默认档口的数据库不变量（最终权限与数据一致性收口）
--
-- 背景：V67 已用 generated guard + unique 保证每租户一个未删除默认档口；本迁移把同一模式
-- 扩展到 sys_user_outlet（每 tenant_id+user_id）与 agent_key_outlet（每 tenant_id+agent_key_id）。
--
-- 做法：每个表用单条 ALTER 增加 STORED 生成列（subject id 作为 guard）+ 唯一索引。
-- MySQL/MariaDB 唯一索引忽略 NULL，因此非默认/禁用/软删行不参与冲突：
--   sys_user_outlet：deleted=0 且 status=1 且 is_default=1 时 guard=user_id，否则 NULL
--   agent_key_outlet：status=1 且 is_default=1 时 guard=agent_key_id，否则 NULL
--
-- MySQL DDL 非事务性：若先 ALTER 一张表、另一张因历史重复失败，会留下半迁移结构，人工清理后
-- 重跑又因列/索引已存在而继续失败。因此本迁移在**任何 ALTER 之前**统一检查两张表的重复默认；
-- 任一表有重复即 fail-closed（fail-fast）：故意 EXECUTE 一条引用不存在表的语句，错误名可定位，
-- 两张表都**零 DDL**，绝不静默删/改数据。
--
-- 上线前置检查（只读，可与本迁移内的 preflight 相互印证）；有重复时由人工确认保留哪条、
-- 显式置其余 is_default=0 后再重跑迁移：
--   SELECT tenant_id, user_id, COUNT(*) FROM sys_user_outlet
--    WHERE deleted=0 AND status=1 AND is_default=1 GROUP BY tenant_id,user_id HAVING COUNT(*)>1;
--   SELECT tenant_id, agent_key_id, COUNT(*) FROM agent_key_outlet
--    WHERE status=1 AND is_default=1 GROUP BY tenant_id,agent_key_id HAVING COUNT(*)>1;
--
-- 失败恢复：迁移失败后 Flyway 会在 schema_history 记录 failed row；清理重复数据后必须执行
-- `flyway repair`（移除 failed row）再 `migrate`。因 preflight 失败时零 DDL，重跑不会遇到
-- “列/索引已存在”的阻塞。
SET @v68_user_dup := (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, user_id
      FROM `sys_user_outlet`
     WHERE `deleted` = 0 AND `status` = 1 AND `is_default` = 1
     GROUP BY tenant_id, user_id
    HAVING COUNT(*) > 1
  ) AS v68_user_dup
);
SET @v68_key_dup := (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, agent_key_id
      FROM `agent_key_outlet`
     WHERE `status` = 1 AND `is_default` = 1
     GROUP BY tenant_id, agent_key_id
    HAVING COUNT(*) > 1
  ) AS v68_key_dup
);
SET @v68_preflight_sql := IF(@v68_user_dup + @v68_key_dup > 0,
  'SELECT 1 FROM outlet_default_unique_preflight_failed_v68_see_migration_comment',
  'SELECT 1');
PREPARE v68_preflight_stmt FROM @v68_preflight_sql;
EXECUTE v68_preflight_stmt;
DEALLOCATE PREPARE v68_preflight_stmt;

ALTER TABLE `sys_user_outlet`
  ADD COLUMN `user_default_guard` bigint GENERATED ALWAYS AS (
    CASE WHEN `deleted` = 0 AND `status` = 1 AND `is_default` = 1 THEN `user_id` ELSE NULL END
  ) STORED COMMENT '每用户唯一有效默认守卫：默认=user_id，其余=NULL',
  ADD UNIQUE KEY `uk_user_outlet_default` (`tenant_id`, `user_default_guard`);

ALTER TABLE `agent_key_outlet`
  ADD COLUMN `agent_key_default_guard` bigint GENERATED ALWAYS AS (
    CASE WHEN `status` = 1 AND `is_default` = 1 THEN `agent_key_id` ELSE NULL END
  ) STORED COMMENT '每Agent Key唯一有效默认守卫：默认=agent_key_id，其余=NULL',
  ADD UNIQUE KEY `uk_agent_key_outlet_default` (`tenant_id`, `agent_key_default_guard`);

SELECT 'V68 用户/Agent Key 默认档口唯一性迁移完成' AS status;
