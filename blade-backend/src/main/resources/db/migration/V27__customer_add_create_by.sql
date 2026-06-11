-- 客户表添加创建人字段（用于数据权限）
ALTER TABLE crm_customer ADD COLUMN create_by BIGINT COMMENT '创建人用户ID';
