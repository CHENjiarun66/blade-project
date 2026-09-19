-- Repair the canonical file bindings introduced by V35.
--
-- Historical order images were kept in sale_order.images and in the legacy
-- file_storage.business_* columns, but were never copied into
-- file_business_bind.  The file center intentionally reads only the canonical
-- binding table, so those valid images were incorrectly shown as unbound.

-- 1. Rebuild formal-order image bindings from the authoritative order JSON.
--    JSON entries may be numbers or numeric strings; VARCHAR handles both.
INSERT INTO `file_business_bind` (
    `file_id`, `business_type`, `business_id`, `bind_role`, `sort`,
    `is_primary`, `tenant_id`, `create_by`, `deleted`
)
SELECT
    f.`id`,
    'order',
    o.`id`,
    'attachment',
    refs.`ordinality` - 1,
    CASE WHEN refs.`ordinality` = 1 THEN 1 ELSE 0 END,
    o.`tenant_id`,
    f.`create_by`,
    0
FROM `sale_order` o
JOIN JSON_TABLE(
    CASE
        WHEN JSON_VALID(o.`images`) THEN o.`images`
        ELSE JSON_ARRAY()
    END,
    '$[*]' COLUMNS (
        `ordinality` FOR ORDINALITY,
        `file_id_text` VARCHAR(32) PATH '$'
    )
) refs
JOIN `file_storage` f
  ON f.`id` = CAST(refs.`file_id_text` AS UNSIGNED)
 AND f.`tenant_id` = o.`tenant_id`
 AND f.`status` = 1
LEFT JOIN `file_business_bind` existing
  ON existing.`file_id` = f.`id`
 AND existing.`business_type` = 'order'
 AND existing.`business_id` = o.`id`
 AND existing.`tenant_id` = o.`tenant_id`
 AND existing.`deleted` = 0
WHERE refs.`file_id_text` REGEXP '^[0-9]+$'
  AND existing.`id` IS NULL;

-- 2. Repair legacy direct uploads that already carry an order id but are not
--    present in sale_order.images (for example, an interrupted edit session).
INSERT INTO `file_business_bind` (
    `file_id`, `business_type`, `business_id`, `bind_role`, `sort`,
    `is_primary`, `tenant_id`, `create_by`, `deleted`
)
SELECT
    f.`id`, 'order', f.`business_id`, 'attachment', 0, 1,
    f.`tenant_id`, f.`create_by`, 0
FROM `file_storage` f
JOIN `sale_order` o
  ON o.`id` = f.`business_id`
 AND o.`tenant_id` = f.`tenant_id`
LEFT JOIN `file_business_bind` existing
  ON existing.`file_id` = f.`id`
 AND existing.`business_type` = 'order'
 AND existing.`business_id` = f.`business_id`
 AND existing.`tenant_id` = f.`tenant_id`
 AND existing.`deleted` = 0
WHERE f.`status` = 1
  AND f.`business_type` = 'order'
  AND f.`business_id` IS NOT NULL
  AND existing.`id` IS NULL;

-- 3. Repair the compatibility source_file_id of historical drafts.  New
--    drafts already use FileService.syncFiles and can bind multiple images.
INSERT INTO `file_business_bind` (
    `file_id`, `business_type`, `business_id`, `bind_role`, `sort`,
    `is_primary`, `tenant_id`, `create_by`, `deleted`
)
SELECT
    f.`id`, 'order_draft', d.`id`, 'source', 0, 1,
    d.`tenant_id`, f.`create_by`, 0
FROM `order_draft` d
JOIN `file_storage` f
  ON f.`id` = d.`source_file_id`
 AND f.`tenant_id` = d.`tenant_id`
 AND f.`status` = 1
LEFT JOIN `file_business_bind` existing
  ON existing.`file_id` = f.`id`
 AND existing.`business_type` = 'order_draft'
 AND existing.`business_id` = d.`id`
 AND existing.`tenant_id` = d.`tenant_id`
 AND existing.`deleted` = 0
WHERE d.`source_file_id` IS NOT NULL
  AND existing.`id` IS NULL;
