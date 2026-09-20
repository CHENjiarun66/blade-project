-- ============================================================================
-- DATA-OUTLET-001：历史档口（source_shop）只读审计报告
-- ============================================================================
-- 用途：生成 source_shop 分布、空值、疑似批次、纯数字和订单/草稿冲突候选，
--       为 Series F 的生产副本映射预演提供事实依据。
--
-- 安全约束（强制）：
--   1. 本文件只包含 SELECT 语句，不包含任何 UPDATE / DELETE / INSERT。
--   2. 只允许在「生产库副本」上执行，禁止连接或修改 NAS / 生产环境。
--   3. 不自动创建、不自动回填档口主数据；"42" 等纯数字文本一律进异常清单。
--
-- 说明：本脚本跑在 V63 迁移之后。迁移只新增可空的 source_outlet_id，不回填，
--       因此正式订单与草稿的 source_outlet_id 当前应全部为 NULL；真正回填在
--       Series F（DATA-OUTLET-002）中按人工确认的映射执行。
-- ============================================================================

-- ── A. 正式订单 sale_order.source_shop ────────────────────────────────────

-- A1 总览：订单总数、空值/空白、非空
SELECT
  COUNT(*)                                                        AS total_orders,
  SUM(CASE WHEN source_shop IS NULL OR TRIM(source_shop) = '' THEN 1 ELSE 0 END) AS blank_or_null_shop,
  SUM(CASE WHEN source_shop IS NOT NULL AND TRIM(source_shop) <> '' THEN 1 ELSE 0 END) AS with_shop
FROM sale_order
WHERE deleted = 0;

-- A2 分布：非空 source_shop 分组计数
SELECT TRIM(source_shop) AS shop_value, COUNT(*) AS cnt
FROM sale_order
WHERE deleted = 0
  AND source_shop IS NOT NULL
  AND TRIM(source_shop) <> ''
GROUP BY TRIM(source_shop)
ORDER BY cnt DESC, shop_value;

-- A3 纯数字 source_shop（疑似把单据批次/编号写成档口，如 "42"）
SELECT id, order_no, source_shop, source_doc_no, status
FROM sale_order
WHERE deleted = 0
  AND source_shop IS NOT NULL
  AND source_shop REGEXP '^[0-9]+$'
ORDER BY id;

-- A4 source_shop 等于 source_doc_no 的批次前缀（批次_单号 的批次段，疑似批次误写）
SELECT id, order_no, source_shop, source_doc_no, status
FROM sale_order
WHERE deleted = 0
  AND source_shop IS NOT NULL AND TRIM(source_shop) <> ''
  AND source_doc_no IS NOT NULL AND TRIM(source_doc_no) <> ''
  AND source_shop = SUBSTRING_INDEX(source_doc_no, '_', 1)
ORDER BY id;

-- ── B. 草稿 order_draft.source_shop ───────────────────────────────────────

-- B1 总览：草稿总数、空值/空白
SELECT
  COUNT(*)                                                        AS total_drafts,
  SUM(CASE WHEN source_shop IS NULL OR TRIM(source_shop) = '' THEN 1 ELSE 0 END) AS blank_or_null_shop
FROM order_draft
WHERE deleted = 0;

-- B2 分布：非空 source_shop 分组计数
SELECT TRIM(source_shop) AS shop_value, COUNT(*) AS cnt
FROM order_draft
WHERE deleted = 0
  AND source_shop IS NOT NULL
  AND TRIM(source_shop) <> ''
GROUP BY TRIM(source_shop)
ORDER BY cnt DESC, shop_value;

-- B3 source_shop = source_batch_no（草稿直接用了单据批次当来源档口）
SELECT id, external_ref_no, source_shop, source_batch_no, status
FROM order_draft
WHERE deleted = 0
  AND source_shop IS NOT NULL AND TRIM(source_shop) <> ''
  AND source_batch_no IS NOT NULL AND TRIM(source_batch_no) <> ''
  AND source_shop = source_batch_no
ORDER BY id;

-- B4 纯数字 source_shop（疑似批次/编号）
SELECT id, external_ref_no, source_shop, source_batch_no, status
FROM order_draft
WHERE deleted = 0
  AND source_shop IS NOT NULL
  AND source_shop REGEXP '^[0-9]+$'
ORDER BY id;

-- ── C. 订单与草稿冲突候选 ─────────────────────────────────────────────────

-- C1 草稿确认生成的正式订单，其 source_shop 与草稿 source_shop 归一化后不一致
--    （空值与空串归一为相同；差异说明确认链路可能改写或遗漏了来源档口快照）
SELECT
  d.id                                        AS draft_id,
  d.external_ref_no                           AS draft_ref,
  TRIM(COALESCE(d.source_shop, ''))           AS draft_shop,
  o.id                                        AS order_id,
  o.order_no                                  AS order_no,
  TRIM(COALESCE(o.source_shop, ''))           AS order_shop
FROM order_draft d
JOIN sale_order o ON o.id = d.confirmed_order_id AND o.deleted = 0
WHERE d.deleted = 0
  AND d.confirmed_order_id IS NOT NULL
  AND TRIM(COALESCE(d.source_shop, '')) <> TRIM(COALESCE(o.source_shop, ''))
ORDER BY o.id;
