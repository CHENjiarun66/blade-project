export interface SkuDisplaySource {
  skuCode?: string | null
  skuType?: string | null
  placeholder?: boolean | null
  variantUnresolved?: boolean | null
}

const DEFAULT_SUFFIX = /-NA-NA$/i
const PLACEHOLDER_SUFFIX = /-(?:UNSPECIFIED|UNSPEC)-UNSPEC$/i

export function isDefaultSku(sku: SkuDisplaySource): boolean {
  return sku.skuType === 'DEFAULT' || DEFAULT_SUFFIX.test(sku.skuCode || '')
}

export function isPlaceholderSku(sku: SkuDisplaySource): boolean {
  return sku.placeholder === true
    || sku.skuType === 'PLACEHOLDER'
    || PLACEHOLDER_SUFFIX.test(sku.skuCode || '')
}

export function skuFriendlyName(sku: SkuDisplaySource): string {
  if (isPlaceholderSku(sku)) return '整款录入（颜色/尺码未指定）'
  if (isDefaultSku(sku)) return '无规格商品（实际 SKU）'
  return sku.skuCode || '未命名 SKU'
}

export function hasFriendlySkuName(sku: SkuDisplaySource): boolean {
  return isPlaceholderSku(sku) || isDefaultSku(sku)
}

export function skuColorDisplay(sku: SkuDisplaySource & { colorName?: string | null }): string {
  if (isPlaceholderSku(sku)) return '未指定颜色'
  if (isDefaultSku(sku)) return '不分颜色'
  return sku.colorName || '—'
}

export function skuSizeDisplay(sku: SkuDisplaySource & { sizeName?: string | null }): string {
  if (isPlaceholderSku(sku)) return '未指定尺码'
  if (isDefaultSku(sku)) return '不分尺码'
  return sku.sizeName || '—'
}
