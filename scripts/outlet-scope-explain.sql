-- ============================================================================
-- TEST-OUTLET-004（本地）：档口范围查询计划与索引核对预演（只读）
-- ============================================================================
-- 用途：在生产副本上核对 single / multi / all / none / unassigned 五类范围下的
--       订单、草稿查询计划，以及档口相关索引是否存在。
--
-- 重要：脚本只输出 EXPLAIN / information_schema，不写库、不建索引、不改数据。
--       所有 EXPLAIN 必须显式提供 :tenant_id；未提供/为 NULL 时 fail-closed（零行）。
--       不把 EXPLAIN 结果写成脆弱断言（不同 MySQL 版本/统计信息会变化）；
--       自动化断言只核对「索引存在」，生产规模性能结论保持 pending。
--
-- 用法：把 :tenant_id 替换为真实租户，把 :outlet_ids 替换为逗号分隔的档口 ID。
-- ============================================================================

-- ── A. 索引核对：应存在的档口范围索引 ───────────────────────────────────
SELECT
  table_name,
  index_name,
  GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'sale_order'      AND index_name IN ('idx_tenant_id', 'idx_status', 'idx_so_source_outlet'))
    OR (table_name = 'order_draft'  AND index_name IN ('idx_order_draft_tenant_status', 'idx_order_draft_source_outlet',
                                                       'idx_order_draft_tenant_outlet', 'idx_order_draft_tenant_creator'))
    OR (table_name = 'sales_outlet' AND index_name IN ('uk_outlet_code_tenant', 'idx_outlet_tenant_status'))
    OR (table_name = 'sys_user_outlet' AND index_name IN ('idx_user_outlet_user', 'idx_user_outlet_outlet'))
    OR (table_name = 'agent_key_outlet' AND index_name IN ('idx_agent_key_outlet_key', 'uk_agent_key_outlet'))
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- 已知缺口（Series G 评估，不在本地改 schema）：sale_order.salesman_id 无独立索引；
-- peopleScope=SELF 的 `salesman_id = ?` 过滤在超大规模下可能跟随 (tenant_id, source_outlet_id) 范围扫描。

-- ── B. single：单档口 ───────────────────────────────────────────────────
EXPLAIN
SELECT id FROM sale_order
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id = :outlet_id AND salesman_id = :salesman_id
ORDER BY create_time DESC LIMIT 20;

EXPLAIN
SELECT id FROM order_draft
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id = :outlet_id AND created_by_user_id = :user_id
ORDER BY update_time DESC LIMIT 20;

-- ── C. multi：多档口 IN ─────────────────────────────────────────────────
EXPLAIN
SELECT id FROM sale_order
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id IN (:outlet_ids) AND salesman_id = :salesman_id
ORDER BY create_time DESC LIMIT 20;

EXPLAIN
SELECT id FROM order_draft
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id IN (:outlet_ids) AND created_by_user_id = :user_id
ORDER BY update_time DESC LIMIT 20;

-- ── D. all：租户全部档口（NOT NULL，无 IN 列表）─────────────────────────
EXPLAIN
SELECT id FROM sale_order
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id IS NOT NULL
ORDER BY create_time DESC LIMIT 20;

-- ── E. none：无档口范围（谓词 1=0，应直接 zero row）─────────────────────
EXPLAIN
SELECT id FROM sale_order
WHERE tenant_id = :tenant_id AND deleted = 0 AND 1 = 0
ORDER BY create_time DESC LIMIT 20;

-- ── F. unassigned：待归档 NULL（仅授权者显式查看）───────────────────────
EXPLAIN
SELECT id FROM sale_order
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id IS NULL
ORDER BY create_time DESC LIMIT 20;

EXPLAIN
SELECT id FROM order_draft
WHERE tenant_id = :tenant_id AND deleted = 0 AND source_outlet_id IS NULL
ORDER BY update_time DESC LIMIT 20;
