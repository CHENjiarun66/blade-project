import type { CatalogProductVO } from '@/api/catalog'
export function stockLabel(product: CatalogProductVO) {
  if (!product.hasStock) return '暂无现货'
  return typeof product.availableQty === 'number' && Number.isFinite(product.availableQty)
    ? `可选现货 ${Math.max(0, product.availableQty).toLocaleString('zh-CN')} 件` : '有现货'
}
