-- 客户标签表
CREATE TABLE crm_customer_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(32) NOT NULL COMMENT '标签名',
    color VARCHAR(8) NOT NULL COMMENT '颜色，如 #FF6B6B',
    sort INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_name (tenant_id, name)
) COMMENT '客户标签表';

-- 客户-标签关联表
CREATE TABLE crm_customer_tag_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_tag (customer_id, tag_id, tenant_id),
    KEY idx_customer_id (customer_id),
    KEY idx_tag_id (tag_id),
    KEY idx_tenant_id (tenant_id)
) COMMENT '客户标签关联表';
