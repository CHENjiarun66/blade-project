-- V50: DEFAULT 只允许用于无颜色、无尺码且仅有一个有效 SKU 的商品

UPDATE product_sku
SET sku_type = 'NORMAL'
WHERE sku_type = 'DEFAULT';

UPDATE product_sku sku
JOIN product p ON p.id = sku.product_id AND p.tenant_id = sku.tenant_id
JOIN (
  SELECT product_id, tenant_id
  FROM product_sku
  WHERE deleted = 0 AND sku_type <> 'PLACEHOLDER'
  GROUP BY product_id, tenant_id
  HAVING COUNT(*) = 1
) single_sku ON single_sku.product_id = p.id AND single_sku.tenant_id = p.tenant_id
SET sku.sku_type = 'DEFAULT'
WHERE sku.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM product_color_rel cr WHERE cr.product_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM product_size_rel sr WHERE sr.product_id = p.id);
