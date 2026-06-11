-- 客户操作日志表
CREATE TABLE crm_customer_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    operation VARCHAR(16) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    detail TEXT COMMENT '变更详情JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_customer_id (customer_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_operator_id (operator_id)
) COMMENT '客户操作日志表';
