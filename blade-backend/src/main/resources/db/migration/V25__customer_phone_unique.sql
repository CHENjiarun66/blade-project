-- V25: 客户电话唯一索引
-- 问题：同租户内可创建重复电话客户
-- 方案：对 crm_customer_phone 表的 (tenant_id, phone, deleted) 建唯一索引

-- 1. 清理 deleted=0 的重复数据：每个租户+电话只保留 id 最小的记录
-- 使用多表 DELETE 语法避免 MySQL 子查询限制
DELETE t1 FROM crm_customer_phone t1
INNER JOIN crm_customer_phone t2
WHERE t1.deleted = 0 AND t2.deleted = 0
  AND t1.tenant_id = t2.tenant_id
  AND t1.phone = t2.phone
  AND t1.id > t2.id;

-- 2. 清理 deleted=1 的重复数据：每个租户+电话只保留 id 最大的记录
DELETE t1 FROM crm_customer_phone t1
INNER JOIN crm_customer_phone t2
WHERE t1.deleted = 1 AND t2.deleted = 1
  AND t1.tenant_id = t2.tenant_id
  AND t1.phone = t2.phone
  AND t1.id < t2.id;

-- 3. 建唯一索引
ALTER TABLE crm_customer_phone
  ADD UNIQUE KEY uk_tenant_phone_deleted (tenant_id, phone, deleted);
