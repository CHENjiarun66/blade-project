#!/usr/bin/env bash
set -euo pipefail

EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION:-58}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-blade-mysql}"
MYSQL_DATABASE_NAME="${MYSQL_DATABASE_NAME:-}"

mysql_scalar() {
  if [ -n "$MYSQL_DATABASE_NAME" ]; then
    /usr/local/bin/docker exec -i -e "VERIFY_DATABASE=$MYSQL_DATABASE_NAME" "$MYSQL_CONTAINER" sh -c \
      'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" "$VERIFY_DATABASE"' <<< "$1"
  else
    /usr/local/bin/docker exec -i "$MYSQL_CONTAINER" sh -c \
      'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' <<< "$1"
  fi
}

assert_zero() {
  local label="$1"
  local sql="$2"
  local value
  value="$(mysql_scalar "$sql")"
  if [ "$value" != "0" ]; then
    echo "ERROR: $label = $value"
    exit 1
  fi
  echo "OK: $label = 0"
}

current_version="$(mysql_scalar "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;")"
if [ "$current_version" != "$EXPECTED_FLYWAY_VERSION" ]; then
  echo "ERROR: Flyway version is $current_version, expected $EXPECTED_FLYWAY_VERSION"
  exit 1
fi
echo "OK: Flyway version $current_version"

assert_zero "unmigrated orders" \
  "SELECT COUNT(*) FROM sale_order WHERE deleted=0 AND (fulfillment_status IS NULL OR collection_status IS NULL);"

assert_zero "invalid lifecycle values" \
  "SELECT COUNT(*) FROM sale_order WHERE deleted=0 AND (fulfillment_status NOT IN ('CONFIRMED','WAITING_ALLOCATION','ALLOCATING','READY_TO_SHIP','SHIPPED','COMPLETED','CANCELLED') OR collection_status NOT IN ('UNPAID','PARTIAL','SETTLED') OR fulfillment_mode NOT IN ('UNDECIDED','STOCK_LINKED','RECORD_ONLY'));"

assert_zero "negative finance snapshots" \
  "SELECT COUNT(*) FROM sale_order WHERE deleted=0 AND (gross_received_amount<0 OR cash_refund_amount<0 OR sales_return_amount<0 OR net_received_amount<0 OR balance_amount<0 OR write_off_amount<0);"

assert_zero "finance ledger/snapshot mismatches" \
  "SELECT COUNT(*) FROM sale_order o LEFT JOIN (SELECT r.tenant_id,r.order_id,SUM(CASE WHEN r.record_type IN ('RECEIPT','MIGRATION_OPENING') THEN r.amount ELSE 0 END) gross,SUM(CASE WHEN r.record_type='REFUND' THEN r.amount ELSE 0 END) refund,SUM(CASE WHEN r.record_type='WRITE_OFF' THEN r.amount ELSE 0 END) writeoff FROM order_financial_record r LEFT JOIN order_financial_record rv ON rv.tenant_id=r.tenant_id AND rv.record_type='REVERSAL' AND rv.reversed_record_id=r.id AND rv.deleted=0 WHERE r.deleted=0 AND r.record_type<>'REVERSAL' AND rv.id IS NULL GROUP BY r.tenant_id,r.order_id) f ON f.tenant_id=o.tenant_id AND f.order_id=o.id WHERE o.deleted=0 AND o.collection_status IS NOT NULL AND (ABS(o.gross_received_amount-COALESCE(f.gross,0))>0.001 OR ABS(o.cash_refund_amount-COALESCE(f.refund,0))>0.001 OR ABS(o.write_off_amount-COALESCE(f.writeoff,0))>0.001 OR ABS(o.net_received_amount-GREATEST(COALESCE(f.gross,0)-COALESCE(f.refund,0),0))>0.001 OR ABS(o.balance_amount-GREATEST(GREATEST(o.total_amount-o.sales_return_amount,0)-COALESCE(f.writeoff,0)-GREATEST(COALESCE(f.gross,0)-COALESCE(f.refund,0),0),0))>0.001);"

assert_zero "variant products without exactly one active placeholder" \
  "SELECT COUNT(*) FROM (SELECT p.id FROM product p JOIN product_sku n ON n.product_id=p.id AND n.tenant_id=p.tenant_id AND n.deleted=0 AND n.sku_type='NORMAL' LEFT JOIN product_sku h ON h.product_id=p.id AND h.tenant_id=p.tenant_id AND h.deleted=0 AND h.sku_type='PLACEHOLDER' WHERE p.deleted=0 GROUP BY p.id HAVING COUNT(DISTINCT h.id)<>1) x;"

echo "All order release invariants passed."
