-- V49: 区分真实、默认和 SPU 占位 SKU，支持纸单只识别到款号的场景

ALTER TABLE `product_sku`
  ADD COLUMN `sku_type` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL真实规格 DEFAULT无规格默认 PLACEHOLDER款号占位' AFTER `sku_code`,
  ADD KEY `idx_product_sku_type` (`tenant_id`, `product_id`, `sku_type`, `status`, `deleted`);

-- 系统保留规格不进入商品可选颜色/尺码关联；status=0 使其不出现在普通属性维护列表。
INSERT INTO `product_color` (`color_code`, `color_name`, `status`, `tenant_id`, `deleted`)
SELECT 'UNSPECIFIED', '未指定颜色', 0, tenants.tenant_id, 0
FROM (SELECT DISTINCT tenant_id FROM product) tenants
WHERE NOT EXISTS (
  SELECT 1 FROM product_color c
  WHERE c.tenant_id = tenants.tenant_id AND c.color_code = 'UNSPECIFIED'
);

INSERT INTO `product_size` (`size_code`, `sort`, `status`, `tenant_id`, `deleted`)
SELECT 'UNSPEC', 9999, 0, tenants.tenant_id, 0
FROM (SELECT DISTINCT tenant_id FROM product) tenants
WHERE NOT EXISTS (
  SELECT 1 FROM product_size s
  WHERE s.tenant_id = tenants.tenant_id AND s.size_code = 'UNSPEC'
);

INSERT INTO `product_color` (`color_code`, `color_name`, `status`, `tenant_id`, `deleted`)
SELECT 'NA', '不分颜色', 0, tenants.tenant_id, 0
FROM (SELECT DISTINCT tenant_id FROM product) tenants
WHERE NOT EXISTS (
  SELECT 1 FROM product_color c
  WHERE c.tenant_id = tenants.tenant_id AND c.color_code = 'NA'
);

INSERT INTO `product_size` (`size_code`, `sort`, `status`, `tenant_id`, `deleted`)
SELECT 'NA', 9998, 0, tenants.tenant_id, 0
FROM (SELECT DISTINCT tenant_id FROM product) tenants
WHERE NOT EXISTS (
  SELECT 1 FROM product_size s
  WHERE s.tenant_id = tenants.tenant_id AND s.size_code = 'NA'
);

-- 既没有颜色关系也没有尺码关系的既有单 SKU，定义为无规格默认 SKU。
UPDATE product_sku sku
JOIN product p ON p.id = sku.product_id AND p.tenant_id = sku.tenant_id
SET sku.sku_type = 'DEFAULT'
WHERE sku.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM product_color_rel cr WHERE cr.product_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM product_size_rel sr WHERE sr.product_id = p.id);

-- 对当前存在两个及以上启用真实 SKU 的款号补齐一个 SPU 占位 SKU。
INSERT INTO `product_sku`
  (`product_id`, `color_id`, `size_id`, `sku_code`, `sku_type`, `price`, `cost_price`, `status`, `tenant_id`, `deleted`)
SELECT p.id, unspecified_color.id, unspecified_size.id,
       CONCAT(p.product_code, '-UNSPEC-UNSPEC'), 'PLACEHOLDER',
       COALESCE(p.wholesale_price, 0), COALESCE(p.cost_price, 0), p.status, p.tenant_id, 0
FROM product p
JOIN (
  SELECT product_id, tenant_id
  FROM product_sku
  WHERE deleted = 0 AND status = 1 AND sku_type <> 'PLACEHOLDER'
  GROUP BY product_id, tenant_id
  HAVING COUNT(*) > 1
) multi ON multi.product_id = p.id AND multi.tenant_id = p.tenant_id
JOIN product_color unspecified_color
  ON unspecified_color.tenant_id = p.tenant_id AND unspecified_color.color_code = 'UNSPECIFIED'
JOIN product_size unspecified_size
  ON unspecified_size.tenant_id = p.tenant_id AND unspecified_size.size_code = 'UNSPEC'
WHERE p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM product_sku placeholder
    WHERE placeholder.product_id = p.id
      AND placeholder.tenant_id = p.tenant_id
      AND placeholder.sku_type = 'PLACEHOLDER'
      AND placeholder.deleted = 0
  );
