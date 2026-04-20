-- V16: 修复 sys_role_permission 和 sys_user_role 表的 tenant_id 为 NULL 问题
-- 租户拦截器会过滤 tenant_id != 当前租户ID 的记录，导致权限查询失败

UPDATE sys_role_permission SET tenant_id = 1 WHERE tenant_id IS NULL AND deleted = 0;
UPDATE sys_user_role SET tenant_id = 1 WHERE tenant_id IS NULL AND deleted = 0;
