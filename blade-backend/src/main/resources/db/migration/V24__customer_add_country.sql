-- 客户模块国际化：增加国家区号字段
ALTER TABLE crm_customer
  ADD COLUMN country_code VARCHAR(8)  COMMENT '国家区号，如+86' AFTER remark,
  ADD COLUMN country_name VARCHAR(64) COMMENT '国家名称，如China' AFTER country_code;
