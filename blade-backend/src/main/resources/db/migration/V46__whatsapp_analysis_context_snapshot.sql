ALTER TABLE `wa_analysis_job`
  ADD COLUMN `context_message_ids` JSON DEFAULT NULL COMMENT '领取时的上下文消息ID快照' AFTER `context_version_hash`;
