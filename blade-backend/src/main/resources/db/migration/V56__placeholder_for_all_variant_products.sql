-- 任意显式规格商品（至少一个启用 NORMAL SKU）都必须有一个可用的整款录入 PLACEHOLDER。
-- 无规格商品只有 DEFAULT/NA-NA，不启用 PLACEHOLDER。

-- 兼容 V49 后新增的租户：按已有规格商品所在租户补齐系统保留属性。
UPDATE product_color reserved_color
JOIN (
  SELECT DISTINCT tenant_id
  FROM product_sku
  WHERE deleted = 0 AND status = 1 AND sku_type = 'NORMAL'
) variants ON variants.tenant_id = reserved_color.tenant_id
SET reserved_color.deleted = 0,
    reserved_color.status = 0,
    reserved_color.color_name = '未指定颜色'
WHERE reserved_color.color_code = 'UNSPECIFIED'
  AND reserved_color.deleted = 1;

INSERT INTO `product_color` (`color_code`, `color_name`, `status`, `tenant_id`, `deleted`)
SELECT 'UNSPECIFIED', '未指定颜色', 0, variants.tenant_id, 0
FROM (
  SELECT DISTINCT tenant_id
  FROM product_sku
  WHERE deleted = 0 AND status = 1 AND sku_type = 'NORMAL'
) variants
WHERE NOT EXISTS (
  SELECT 1 FROM product_color c
  WHERE c.tenant_id = variants.tenant_id
    AND c.color_code = 'UNSPECIFIED'
    AND c.deleted = 0
);

UPDATE product_size reserved_size
JOIN (
  SELECT DISTINCT tenant_id
  FROM product_sku
  WHERE deleted = 0 AND status = 1 AND sku_type = 'NORMAL'
) variants ON variants.tenant_id = reserved_size.tenant_id
SET reserved_size.deleted = 0,
    reserved_size.status = 0,
    reserved_size.sort = 9999
WHERE reserved_size.size_code = 'UNSPEC'
  AND reserved_size.deleted = 1;

INSERT INTO `product_size` (`size_code`, `sort`, `status`, `tenant_id`, `deleted`)
SELECT 'UNSPEC', 9999, 0, variants.tenant_id, 0
FROM (
  SELECT DISTINCT tenant_id
  FROM product_sku
  WHERE deleted = 0 AND status = 1 AND sku_type = 'NORMAL'
) variants
WHERE NOT EXISTS (
  SELECT 1 FROM product_size s
  WHERE s.tenant_id = variants.tenant_id
    AND s.size_code = 'UNSPEC'
    AND s.deleted = 0
);

-- 若旧占位项曾被软删除，优先恢复原行，避免唯一编码冲突并保留可能的历史引用。
UPDATE product_sku placeholder
JOIN (
  SELECT product_id, tenant_id, MIN(id) AS placeholder_id
  FROM product_sku
  WHERE deleted = 1 AND sku_type = 'PLACEHOLDER'
  GROUP BY product_id, tenant_id
) deleted_placeholder ON deleted_placeholder.placeholder_id = placeholder.id
JOIN product p
  ON p.id = placeholder.product_id
 AND p.tenant_id = placeholder.tenant_id
 AND p.deleted = 0
JOIN product_color unspecified_color
  ON unspecified_color.tenant_id = p.tenant_id
 AND unspecified_color.color_code = 'UNSPECIFIED'
 AND unspecified_color.deleted = 0
JOIN product_size unspecified_size
  ON unspecified_size.tenant_id = p.tenant_id
 AND unspecified_size.size_code = 'UNSPEC'
 AND unspecified_size.deleted = 0
JOIN product_sku normal_sku
  ON normal_sku.product_id = p.id
 AND normal_sku.tenant_id = p.tenant_id
 AND normal_sku.sku_type = 'NORMAL'
 AND normal_sku.status = 1
 AND normal_sku.deleted = 0
LEFT JOIN product_sku active_placeholder
  ON active_placeholder.product_id = p.id
 AND active_placeholder.tenant_id = p.tenant_id
 AND active_placeholder.sku_type = 'PLACEHOLDER'
 AND active_placeholder.deleted = 0
SET placeholder.color_id = unspecified_color.id,
    placeholder.size_id = unspecified_size.id,
    placeholder.sku_type = 'PLACEHOLDER',
    placeholder.status = p.status,
    placeholder.deleted = 0
WHERE active_placeholder.id IS NULL;

-- 旧规则曾把“只有一个真实 SKU”的占位项禁用；现在重新启用并校正系统属性。
UPDATE product_sku placeholder
JOIN product p
  ON p.id = placeholder.product_id
 AND p.tenant_id = placeholder.tenant_id
 AND p.deleted = 0
JOIN product_color unspecified_color
  ON unspecified_color.tenant_id = p.tenant_id
 AND unspecified_color.color_code = 'UNSPECIFIED'
 AND unspecified_color.deleted = 0
JOIN product_size unspecified_size
  ON unspecified_size.tenant_id = p.tenant_id
 AND unspecified_size.size_code = 'UNSPEC'
 AND unspecified_size.deleted = 0
JOIN product_sku normal_sku
  ON normal_sku.product_id = p.id
 AND normal_sku.tenant_id = p.tenant_id
 AND normal_sku.sku_type = 'NORMAL'
 AND normal_sku.status = 1
 AND normal_sku.deleted = 0
SET placeholder.color_id = unspecified_color.id,
    placeholder.size_id = unspecified_size.id,
    placeholder.sku_type = 'PLACEHOLDER',
    placeholder.status = p.status
WHERE placeholder.deleted = 0
  AND placeholder.sku_type = 'PLACEHOLDER';

-- 为从未生成过占位项的既有规格商品补齐整款录入 SKU。
INSERT INTO `product_sku`
  (`product_id`, `color_id`, `size_id`, `sku_code`, `sku_type`, `price`, `cost_price`, `status`, `tenant_id`, `deleted`)
SELECT p.id, unspecified_color.id, unspecified_size.id,
       CONCAT(p.product_code, '-UNSPECIFIED-UNSPEC'), 'PLACEHOLDER',
       COALESCE(p.wholesale_price, 0), COALESCE(p.cost_price, 0), p.status, p.tenant_id, 0
FROM product p
JOIN (
  SELECT DISTINCT product_id, tenant_id
  FROM product_sku
  WHERE deleted = 0 AND status = 1 AND sku_type = 'NORMAL'
) variants ON variants.product_id = p.id AND variants.tenant_id = p.tenant_id
JOIN product_color unspecified_color
  ON unspecified_color.tenant_id = p.tenant_id
 AND unspecified_color.color_code = 'UNSPECIFIED'
 AND unspecified_color.deleted = 0
JOIN product_size unspecified_size
  ON unspecified_size.tenant_id = p.tenant_id
 AND unspecified_size.size_code = 'UNSPEC'
 AND unspecified_size.deleted = 0
WHERE p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM product_sku placeholder
    WHERE placeholder.product_id = p.id
      AND placeholder.tenant_id = p.tenant_id
      AND placeholder.sku_type = 'PLACEHOLDER'
      AND placeholder.deleted = 0
  );

-- 防御性收口：没有启用 NORMAL SKU 的商品不得暴露整款录入项。
UPDATE product_sku placeholder
LEFT JOIN product_sku normal_sku
  ON normal_sku.product_id = placeholder.product_id
 AND normal_sku.tenant_id = placeholder.tenant_id
 AND normal_sku.sku_type = 'NORMAL'
 AND normal_sku.status = 1
 AND normal_sku.deleted = 0
SET placeholder.status = 0
WHERE placeholder.deleted = 0
  AND placeholder.sku_type = 'PLACEHOLDER'
  AND normal_sku.id IS NULL;
