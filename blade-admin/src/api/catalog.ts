import client from './client'

// --- TypeScript interfaces matching backend Catalog DTOs ---

/** Color/size entry in catalog product */
export interface ColorSizeEntry {
  id: number
  name: string
  code?: string
}

/** SKU info for catalog display — no cost/price, no raw inventory quantities */
export interface CatalogSkuVO {
  id: number
  skuCode: string
  colorId: number
  colorName: string
  sizeId: number
  sizeCode: string
  imageUrls: string[]
  hasStock: boolean
  stockStatus: string // "有现货" | "暂无现货"
}

/** Product info for catalog display */
export interface CatalogProductVO {
  id: number
  productCode: string
  name: string
  categoryId: number | null
  categoryName: string | null
  mainImageUrl: string | null
  imageUrls: string[]
  hasImage: boolean
  hasStock: boolean
  stockStatus: string // "有现货" | "暂无现货"
  tags: string[] | null
  colors: ColorSizeEntry[]
  sizes: ColorSizeEntry[]
  skus: CatalogSkuVO[]
  createTime: string
}

/** Filter option (category / color / size / stockMode) */
export interface FilterOption {
  id: number | null
  name: string
  code?: string
}

/** Available filter options for the catalog page */
export interface CatalogFiltersVO {
  categories: FilterOption[]
  colors: FilterOption[]
  sizes: FilterOption[]
  stockModes: FilterOption[]
}

/** Paginated product list response */
export interface CatalogPageResult {
  records: CatalogProductVO[]
  total: number
  size: number
  current: number
  pages: number
}

/** Query params for catalog product list */
export interface CatalogPageParams {
  keyword?: string
  categoryId?: number
  colorId?: number
  sizeId?: number
  stockMode?: string // 'all' | 'in_stock'
  hasImage?: boolean
  current?: number
  size?: number
}

// --- API functions ---

/** Get paginated product list for catalog display */
export function getCatalogProducts(params?: CatalogPageParams) {
  return client.get('/catalog/products', { params }) as Promise<{
    code: number
    data: CatalogPageResult
    message: string
  }>
}

/** Get single product detail for catalog display */
export function getCatalogProductById(id: number) {
  return client.get(`/catalog/products/${id}`) as Promise<{
    code: number
    data: CatalogProductVO
    message: string
  }>
}

/** Get available filter options */
export function getCatalogFilters() {
  return client.get('/catalog/filters') as Promise<{
    code: number
    data: CatalogFiltersVO
    message: string
  }>
}
