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
-- fail-closed：若历史上同一主体存在多条有效默认，唯一键冲突会让迁移中止，绝不静默删/改。
-- 上线前必须先执行重复 preflight SQL（见 docs 与生产 checklist）；有重复时由人工确认保留哪条、
-- 显式置其余 is_default=0 后再重跑迁移：
--   SELECT tenant_id, user_id, COUNT(*) FROM sys_user_outlet
--    WHERE deleted=0 AND status=1 AND is_default=1 GROUP BY tenant_id,user_id HAVING COUNT(*)>1;
--   SELECT tenant_id, agent_key_id, COUNT(*) FROM agent_key_outlet
--    WHERE status=1 AND is_default=1 GROUP BY tenant_id,agent_key_id HAVING COUNT(*)>1;
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
