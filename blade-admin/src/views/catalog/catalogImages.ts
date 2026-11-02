import type { CatalogProductVO } from '@/api/catalog'
import { filePreviewUrl, fileVariantUrl } from '@/api/file'

export function canonicalImage(url: string) {
  const match = url.match(/\/api\/files\/(\d+)\/(?:preview|variant)(?:\?.*)?$/)
  return match ? `/api/files/${match[1]}/preview` : url
}
export function productImages(product: CatalogProductVO): string[] {
  return [...new Set([product.mainImageUrl, ...(Array.isArray(product.imageUrls) ? product.imageUrls : []),
    ...(Array.isArray(product.skus) ? product.skus : []).flatMap(sku => Array.isArray(sku.imageUrls) ? sku.imageUrls : [])]
    .filter((url): url is string => typeof url === 'string' && !!url).map(canonicalImage))]
}
export function imageUrl(url: string, variant: 'thumb' | 'card' | 'original' = 'card') {
  const match = canonicalImage(url).match(/^\/api\/files\/(\d+)\/preview$/)
  if (!match) return url
  return variant === 'original' ? filePreviewUrl(match[1]!) : fileVariantUrl(match[1]!, variant)
}
export function coverImage(product: CatalogProductVO) {
  const cover = product.mainImageUrl || product.imageUrls?.[0]
  return cover ? imageUrl(cover, 'thumb') : ''
}
export function snapshotProduct(product: CatalogProductVO): CatalogProductVO {
  return { ...product, mainImageUrl: product.mainImageUrl ? canonicalImage(product.mainImageUrl) : null,
    imageUrls: productImages(product), skus: [],
    productCode: typeof product.productCode === 'string' ? product.productCode : '款号待核对',
    name: typeof product.name === 'string' ? product.name : '款式待核对',
    colors: Array.isArray(product.colors) ? product.colors : [], sizes: Array.isArray(product.sizes) ? product.sizes : [],
    hasImage: productImages(product).length > 0 }
}
