-- V7: 订单表重命名与字段完善
-- 1. 表名从 product_order → sale_order, order_item → sale_order_item
-- 2. 填充订单明细冗余字段（product_name, color_name, size_name, sku_code）
-- 3. 订单状态值统一：0=创建, 1=已付款, 2=已发货, 3=已完成, 4=已取消, 5=退货中, 6=已退货

-- ----------------------------
-- 注意：本脚本假设 product_order 和 order_item 已经存在
-- 如果表已重命名，请跳过第1、2步
-- ----------------------------

-- ----------------------------
-- 1. 重命名订单表（如需要）
-- ----------------------------
SET @rename_sale_order_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'product_order'
    ) AND NOT EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'sale_order'
    ),
    'RENAME TABLE `product_order` TO `sale_order`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @rename_sale_order_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 2. 重命名订单明细表（如需要）
-- ----------------------------
SET @rename_sale_order_item_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'order_item'
    ) AND NOT EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'sale_order_item'
    ),
    'RENAME TABLE `order_item` TO `sale_order_item`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @rename_sale_order_item_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 3. 为 sale_order_item 填充冗余字段
-- 通过 SKU 关联查询商品、颜色、尺码信息填充
-- ----------------------------
UPDATE `sale_order_item` oi
INNER JOIN `product_sku` sku ON oi.sku_id = sku.id
INNER JOIN `product` p ON sku.product_id = p.id
LEFT JOIN `product_color` pc ON sku.color_id = pc.id
LEFT JOIN `product_size` ps ON sku.size_id = ps.id
SET
    oi.product_name = p.name,
    oi.sku_code = sku.sku_code,
    oi.color_name = COALESCE(pc.color_name, ''),
    oi.size_name = COALESCE(ps.size_code, '')
WHERE oi.product_name = '' OR oi.product_name IS NULL;

-- ----------------------------
-- 4. 验证数据
-- ----------------------------
SELECT 'sale_order_item 冗余字段填充完成' AS status;
SELECT COUNT(*) AS total_items,
       SUM(CASE WHEN product_name IS NOT NULL AND product_name != '' THEN 1 ELSE 0 END) AS filled_items
FROM sale_order_item;
