-- V13: 修复 sys_user_role 表结构
-- 增加 tenant_id, deleted, create_time 字段，添加复合唯一约束

SET @add_tenant_id_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_user_role'
        AND column_name = 'tenant_id'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user_role` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户ID'''
  )
);
PREPARE stmt FROM @add_tenant_id_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_deleted_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_user_role'
        AND column_name = 'deleted'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user_role` ADD COLUMN `deleted` int DEFAULT 0 COMMENT ''软删除标记'''
  )
);
PREPARE stmt FROM @add_deleted_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_create_time_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_user_role'
        AND column_name = 'create_time'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user_role` ADD COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'''
  )
);
PREPARE stmt FROM @add_create_time_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_old_user_role_index_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_user_role'
        AND index_name = 'uk_user_role'
    ),
    'ALTER TABLE `sys_user_role` DROP INDEX `uk_user_role`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @drop_old_user_role_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_new_user_role_index_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_user_role'
        AND index_name = 'uk_user_role_tenant'
    ),
    'SELECT 1',
    'ALTER TABLE `sys_user_role` ADD UNIQUE INDEX `uk_user_role_tenant` (`user_id`, `role_id`, `tenant_id`)'
  )
);
PREPARE stmt FROM @add_new_user_role_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
