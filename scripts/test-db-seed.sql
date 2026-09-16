-- 隔离测试库种子：复刻开发库的登录约定（test_tenant/admin/admin123）
-- 用法：对全新 Flyway 迁移后的空库执行一次。开发库已有 test_tenant 时本脚本无副作用。
-- 背景：V6 种子建出的租户代码为 super_admin/demo_tenant；开发库中的 test_tenant 是历史手工数据，
--       不属于 migration，因此隔离库需要本脚本对齐测试登录约定。

UPDATE `sys_tenant`
SET `tenant_code` = 'test_tenant'
WHERE `id` = 1 AND `tenant_code` = 'super_admin';
