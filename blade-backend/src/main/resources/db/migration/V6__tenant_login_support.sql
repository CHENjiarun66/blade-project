-- V6: 添加多租户测试数据
-- 注意：密码全部是 123456，BCrypt 加密后的结果

-- ----------------------------
-- 插入测试租户
-- ----------------------------
INSERT INTO `sys_tenant` (`id`, `tenant_name`, `tenant_code`, `status`) VALUES
(1, '测试服装公司', 'test_tenant', 1),
(2, '演示服装企业', 'demo_tenant', 1)
ON DUPLICATE KEY UPDATE `tenant_name` = VALUES(`tenant_name`);

-- ----------------------------
-- 插入测试租户的管理员用户 (密码: admin123)
-- BCrypt hash: $2a$10$KLMg4aoraH703rwMILz3IOVcD.OgKEBAglSrZhOUTxhzBAT7gBRJq
-- 注意：sys_user 表的 uk_username 仅在 username 上建立唯一索引，多租户场景下不同租户可以用相同用户名
-- ----------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `tenant_id`, `status`) VALUES
(2, 'admin', '$2a$10$KLMg4aoraH703rwMILz3IOVcD.OgKEBAglSrZhOUTxhzBAT7gBRJq', '租户管理员', 1, 1),
(3, 'admin_demo', '$2a$10$KLMg4aoraH703rwMILz3IOVcD.OgKEBAglSrZhOUTxhzBAT7gBRJq', '演示管理员', 2, 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`);
