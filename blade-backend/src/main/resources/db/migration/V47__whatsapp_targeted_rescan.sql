-- BE-581/BE-582: WhatsApp 账号全盘扫描 + 单客户定向扫描

ALTER TABLE `wa_scan_job`
  ADD COLUMN `scope_type` varchar(16) NOT NULL DEFAULT 'ACCOUNT' COMMENT 'ACCOUNT/CONTACT' AFTER `account_id`,
  ADD COLUMN `target_phone_normalized` varchar(32) DEFAULT NULL COMMENT 'CONTACT扫描真实号码' AFTER `scope_type`,
  ADD COLUMN `target_conversation_jid` varchar(191) DEFAULT NULL COMMENT 'CONTACT扫描会话技术标识' AFTER `target_phone_normalized`,
  ADD KEY `idx_wa_scan_job_scope` (`tenant_id`, `account_id`, `scope_type`, `target_phone_normalized`, `status`, `requested_at`);

ALTER TABLE `wa_import_batch`
  ADD COLUMN `scan_scope_type` varchar(16) NOT NULL DEFAULT 'ACCOUNT' COMMENT 'ACCOUNT/CONTACT' AFTER `account_id`,
  ADD COLUMN `target_phone_normalized` varchar(32) DEFAULT NULL COMMENT 'CONTACT扫描真实号码' AFTER `scan_scope_type`,
  ADD COLUMN `target_conversation_jid` varchar(191) DEFAULT NULL COMMENT 'CONTACT扫描会话技术标识' AFTER `target_phone_normalized`,
  ADD KEY `idx_wa_batch_scope` (`tenant_id`, `account_id`, `scan_scope_type`, `target_phone_normalized`, `started_at`);
