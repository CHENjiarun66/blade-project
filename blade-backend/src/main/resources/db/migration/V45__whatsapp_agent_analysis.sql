CREATE TABLE `wa_analysis_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `customer_id` BIGINT NOT NULL,
  `wa_contact_id` BIGINT NOT NULL,
  `trigger_batch_id` BIGINT DEFAULT NULL,
  `context_version_hash` BINARY(32) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `priority` INT NOT NULL DEFAULT 100,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL DEFAULT 3,
  `available_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `lease_until` DATETIME(3) DEFAULT NULL,
  `claimed_by_agent_key_id` BIGINT DEFAULT NULL,
  `last_error_code` VARCHAR(64) DEFAULT NULL,
  `completed_at` DATETIME(3) DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wa_analysis_context` (`tenant_id`,`customer_id`,`context_version_hash`),
  KEY `idx_wa_analysis_claim` (`tenant_id`,`status`,`available_at`,`priority`,`id`),
  KEY `idx_wa_analysis_customer` (`tenant_id`,`customer_id`,`create_time`),
  KEY `idx_wa_analysis_lease` (`tenant_id`,`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WhatsApp Agent分析任务';

CREATE TABLE `wa_customer_analysis` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `analysis_job_id` BIGINT NOT NULL,
  `customer_id` BIGINT NOT NULL,
  `wa_contact_id` BIGINT NOT NULL,
  `summary_text` TEXT NOT NULL,
  `preferences_json` JSON DEFAULT NULL,
  `intent_stage` VARCHAR(32) NOT NULL,
  `sentiment` VARCHAR(20) NOT NULL,
  `churn_risk` VARCHAR(20) NOT NULL,
  `recommended_followup_at` DATETIME(3) DEFAULT NULL,
  `recommended_action` VARCHAR(1000) NOT NULL,
  `confidence` DECIMAL(5,4) NOT NULL,
  `evidence_message_ids` JSON NOT NULL,
  `provider` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(100) NOT NULL,
  `prompt_version` VARCHAR(32) NOT NULL,
  `analyzed_at` DATETIME(3) NOT NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wa_customer_analysis_job` (`tenant_id`,`analysis_job_id`),
  KEY `idx_wa_customer_analysis_customer` (`tenant_id`,`customer_id`,`analyzed_at`),
  KEY `idx_wa_customer_analysis_risk` (`tenant_id`,`churn_risk`,`recommended_followup_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WhatsApp客户分析历史';

CREATE TABLE `wa_followup_recommendation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `analysis_id` BIGINT NOT NULL,
  `customer_id` BIGINT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `due_at` DATETIME(3) DEFAULT NULL,
  `title` VARCHAR(255) NOT NULL,
  `recommended_action` VARCHAR(1000) NOT NULL,
  `confidence` DECIMAL(5,4) NOT NULL,
  `handled_by` BIGINT DEFAULT NULL,
  `handled_at` DATETIME(3) DEFAULT NULL,
  `handle_note` VARCHAR(500) DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wa_followup_analysis` (`tenant_id`,`analysis_id`),
  KEY `idx_wa_followup_queue` (`tenant_id`,`status`,`due_at`,`id`),
  KEY `idx_wa_followup_customer` (`tenant_id`,`customer_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WhatsApp客户跟进推荐';

INSERT INTO `sys_permission` (`parent_id`,`name`,`code`,`type`,`sort`,`status`,`tenant_id`,`deleted`)
SELECT p.id,'处理WhatsApp推荐','btn:whatsapp:recommendation',2,4,1,p.tenant_id,0
FROM `sys_permission` p
WHERE p.code='menu:whatsapp' AND p.deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_permission` x WHERE x.tenant_id=p.tenant_id AND x.code='btn:whatsapp:recommendation' AND x.deleted=0);

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`tenant_id`,`deleted`)
SELECT rp.role_id, child.id, rp.tenant_id, 0
FROM `sys_role_permission` rp
JOIN `sys_permission` parent ON parent.id=rp.permission_id AND parent.tenant_id=rp.tenant_id AND parent.code='menu:whatsapp' AND parent.deleted=0
JOIN `sys_permission` child ON child.parent_id=parent.id AND child.tenant_id=rp.tenant_id AND child.code='btn:whatsapp:recommendation' AND child.deleted=0
WHERE rp.deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=rp.role_id AND x.permission_id=child.id AND x.tenant_id=rp.tenant_id AND x.deleted=0);
