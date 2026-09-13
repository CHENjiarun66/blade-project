-- Agent 新增客户的来源审计：不把 Agent Key ID 冒充为系统用户 ID。
ALTER TABLE crm_customer
  ADD COLUMN created_by_agent_key_id BIGINT NULL COMMENT '创建该客户的 Agent Key ID' AFTER create_by,
  ADD KEY idx_customer_agent_key (created_by_agent_key_id);

ALTER TABLE crm_customer_operation_log
  MODIFY COLUMN operator_id BIGINT NULL COMMENT '操作人用户ID，Agent 操作时为空',
  ADD COLUMN agent_key_id BIGINT NULL COMMENT '操作 Agent Key ID' AFTER operator_id,
  ADD KEY idx_customer_log_agent_key (agent_key_id);
