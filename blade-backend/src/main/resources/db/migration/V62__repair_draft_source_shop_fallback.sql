-- V62: 修复草稿确认时错误地用“单据批次”填充“来源档口/店铺”的历史数据。
--
-- 只处理能够通过原草稿确定为程序错误的数据：
-- 1. 正式订单由该草稿确认生成；
-- 2. 草稿来源档口为空；
-- 3. 正式订单来源档口恰好等于草稿单据批次。
-- 用户明确填写过来源档口的订单以及没有关联草稿的历史订单均不修改。

UPDATE sale_order
SET source_shop = NULL
WHERE deleted = 0
  AND EXISTS (
    SELECT 1
    FROM order_draft
    WHERE order_draft.deleted = 0
      AND order_draft.confirmed_order_id = sale_order.id
      AND (order_draft.source_shop IS NULL OR TRIM(order_draft.source_shop) = '')
      AND order_draft.source_batch_no IS NOT NULL
      AND TRIM(order_draft.source_batch_no) <> ''
      AND sale_order.source_shop = order_draft.source_batch_no
  );
