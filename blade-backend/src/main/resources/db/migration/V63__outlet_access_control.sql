-- V63: 档口主数据与跨档口数据权限（Series A 数据模型，加法迁移）。
--
-- 设计依据：docs/20-OUTLET_ACCESS_CONTROL_DESIGN.md 第三章。
-- 兼容性：
--   * 正式订单 sale_order.source_outlet_id 保持可空（历史订单尚未回填，禁止 NOT NULL）。
--   * 草稿 order_draft.source_outlet_id 允许为空（历史迁移 / Owner 待补资料场景）。
--   * source_shop 继续保留为名称快照，不改写历史数据。
--   * 只追加新表、新列和索引，不修改、不删除已有迁移或历史数据。

-- ── 3.1 sales_outlet 档口主表 ──────────────────────────────────────────
CREATE TABLE `sales_outlet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `outlet_code` varchar(30) NOT NULL COMMENT '档口稳定编码（API/导入/Agent 使用）',
  `outlet_name` varchar(100) NOT NULL COMMENT '档口名称',
  `outlet_type` varchar(20) NOT NULL DEFAULT 'STORE' COMMENT '档口类型',
  `contact_name` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(30) DEFAULT NULL COMMENT '电话',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `is_tenant_default` tinyint NOT NULL DEFAULT 0 COMMENT '租户默认档口：1是 0否',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标记',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outlet_code_tenant` (`tenant_id`, `outlet_code`),
  KEY `idx_outlet_tenant_status` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档口主数据';

-- ── 3.2 sys_user_outlet 用户档口关联 ────────────────────────────────────
CREATE TABLE `sys_user_outlet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID（sys_user.id）',
  `outlet_id` bigint NOT NULL COMMENT '档口ID（sales_outlet.id）',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '个人默认档口：1是 0否',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除标记',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_outlet_tenant` (`tenant_id`, `user_id`, `outlet_id`),
  KEY `idx_user_outlet_user` (`tenant_id`, `user_id`, `status`, `deleted`),
  KEY `idx_user_outlet_outlet` (`tenant_id`, `outlet_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户档口关联';

-- ── 3.4 agent_key_outlet Agent Key 档口关联 ─────────────────────────────
CREATE TABLE `agent_key_outlet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `agent_key_id` bigint NOT NULL COMMENT 'Agent Key ID（agent_key.id）',
  `outlet_id` bigint NOT NULL COMMENT '档口ID（sales_outlet.id）',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT 'Key 默认档口：1是 0否',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_key_outlet` (`tenant_id`, `agent_key_id`, `outlet_id`),
  KEY `idx_agent_key_outlet_key` (`tenant_id`, `agent_key_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Key 档口关联';

-- ── 3.4a agent_key 档口范围类型 ──────────────────────────────────────────
-- 消除“无关联行到底是 ALL 还是 NONE”的歧义：ALL 不依赖关联行；ASSIGNED 必须至少
-- 一条有效 agent_key_outlet；NONE 拒绝档口业务。历史 Key 迁移后默认 NONE，不得
-- 静默获得全档口权限；生产 Key 范围由 Series F 人工确认。
ALTER TABLE `agent_key`
  ADD COLUMN `outlet_scope_type` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '档口范围：ALL/ASSIGNED/NONE' AFTER `scopes`;

-- ── 3.5 order_outlet_change_log 订单档口变更审计 ────────────────────────
CREATE TABLE `order_outlet_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID（sale_order.id）',
  `old_outlet_id` bigint DEFAULT NULL COMMENT '原档口ID',
  `old_outlet_name` varchar(100) DEFAULT NULL COMMENT '原档口名称快照',
  `new_outlet_id` bigint NOT NULL COMMENT '新档口ID',
  `new_outlet_name` varchar(100) NOT NULL COMMENT '新档口名称快照',
  `reason` varchar(500) NOT NULL COMMENT '变更原因',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_outlet_change_order` (`tenant_id`, `order_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单档口变更审计（只追加）';

-- ── 3.3 订单与草稿增加可空 source_outlet_id ────────────────────────────
ALTER TABLE `sale_order`
  ADD COLUMN `source_outlet_id` bigint DEFAULT NULL COMMENT '档口主数据ID（权限与统计依据）' AFTER `source_shop`,
  ADD KEY `idx_so_source_outlet` (`tenant_id`, `source_outlet_id`);

ALTER TABLE `order_draft`
  ADD COLUMN `source_outlet_id` bigint DEFAULT NULL COMMENT '档口主数据ID（权限与统计依据）' AFTER `source_shop`,
  ADD KEY `idx_order_draft_source_outlet` (`tenant_id`, `source_outlet_id`);
