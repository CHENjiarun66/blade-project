import type { CatalogPageResult, CatalogProductVO } from '@/api/catalog'

const PRODUCT_CACHE_PREFIX = 'catalog:products:v1:'
const PRODUCT_CACHE_TTL_MS = 12 * 60 * 60 * 1000
const IMAGE_DB_NAME = 'blade-catalog-image-cache'
const IMAGE_DB_VERSION = 1
const IMAGE_STORE = 'images'

export interface CatalogCacheFilters {
  keyword?: string
  stockMode?: string
  hasImage?: boolean
  categoryId?: number
  colorId?: number
  sizeId?: number
}

interface CachedProductPage {
  savedAt: number
  products: CatalogProductVO[]
  total: number
  current: number
  pages: number
}

interface CachedImageRecord {
  key: string
  blob: Blob
  savedAt: number
}

let dbPromise: Promise<IDBDatabase> | null = null
const objectUrls = new Map<string, string>()

export function catalogProductsCacheKey(filters: CatalogCacheFilters) {
  return `${PRODUCT_CACHE_PREFIX}${[
    normalizePart(filters.keyword),
    normalizePart(filters.stockMode || 'all'),
    normalizePart(filters.hasImage === undefined ? 'all' : String(filters.hasImage)),
    normalizePart(filters.categoryId),
    normalizePart(filters.colorId),
    normalizePart(filters.sizeId),
  ].join('|')}`
}

export function readCatalogProductsCache(filters: CatalogCacheFilters): CachedProductPage | null {
  try {
    const raw = localStorage.getItem(catalogProductsCacheKey(filters))
    if (!raw) return null
    const cached = JSON.parse(raw) as CachedProductPage
    if (!cached.savedAt || Date.now() - cached.savedAt > PRODUCT_CACHE_TTL_MS) {
      localStorage.removeItem(catalogProductsCacheKey(filters))
      return null
    }
    return cached
  } catch {
    localStorage.removeItem(catalogProductsCacheKey(filters))
    return null
  }
}

export function writeCatalogProductsCache(filters: CatalogCacheFilters, page: CatalogPageResult, products: CatalogProductVO[]) {
  try {
    const payload: CachedProductPage = {
      savedAt: Date.now(),
      products,
      total: page.total || products.length,
      current: page.current || 1,
      pages: page.pages || 1,
    }
    localStorage.setItem(catalogProductsCacheKey(filters), JSON.stringify(payload))
  } catch {
    // Storage quota is best-effort; the UI should still work without cache.
  }
}

export function extractPreviewFileId(src: string | null | undefined): string | null {
  if (!src) return null
  const match = src.match(/\/api\/files\/(\d+)\/preview(?:\?.*)?$/)
  return match ? match[1] : null
}

export function extractVariantFileId(src: string | null | undefined): string | null {
  if (!src) return null
  const match = src.match(/\/api\/files\/(\d+)\/variant(?:\?|$)/)
  return match ? match[1] : null
}

export function detectVariantType(src: string): 'thumb' | 'card' | 'original' {
  if (/\/variant\b/.test(src)) {
    const m = src.match(/[?&]type=(thumb|card)(?:&|$)/)
    if (m) return m[1] as 'thumb' | 'card'
  }
  return 'original'
}

export function imageCacheKey(src: string) {
  const previewId = extractPreviewFileId(src)
  if (previewId) return `file:${previewId}:original`
  const variantId = extractVariantFileId(src)
  if (variantId) {
    const variantType = detectVariantType(src)
    return `file:${variantId}:${variantType}`
  }
  return src
}

export async function getCachedImageUrl(src: string): Promise<string> {
  const key = imageCacheKey(src)
  const isFileUrl = extractPreviewFileId(src) || extractVariantFileId(src)
  if (!isFileUrl) return src

  const cached = await readCachedImage(key)
  if (cached) {
    return objectUrlFor(key, cached.blob)
  }

  // Compat: try the old file:{id} key for original images (pre-variant era cache)
  if (key.endsWith(':original')) {
    const previewId = extractPreviewFileId(src)
    if (previewId) {
      const oldKey = `file:${previewId}`
      const oldCached = await readCachedImage(oldKey)
      if (oldCached) {
        await writeCachedImage(key, oldCached.blob) // promote to new key
        return objectUrlFor(key, oldCached.blob)
      }
    }
  }

  const response = await fetch(src)
  if (!response.ok) return src
  const blob = await response.blob()
  await writeCachedImage(key, blob)
  return objectUrlFor(key, blob)
}

export async function clearCatalogCaches() {
  for (const key of Object.keys(localStorage)) {
    if (key.startsWith(PRODUCT_CACHE_PREFIX)) {
      localStorage.removeItem(key)
    }
  }
  for (const objectUrl of objectUrls.values()) {
    URL.revokeObjectURL(objectUrl)
  }
  objectUrls.clear()
  try {
    const db = await openImageDb()
    await txDone(db.transaction(IMAGE_STORE, 'readwrite').objectStore(IMAGE_STORE).clear())
  } catch {
    // Cache cleanup should never block logout.
  }
}

function normalizePart(value: unknown) {
  if (value === undefined || value === null || value === '') return 'all'
  return encodeURIComponent(String(value))
}

function objectUrlFor(key: string, blob: Blob) {
  const existing = objectUrls.get(key)
  if (existing) return existing
  const objectUrl = URL.createObjectURL(blob)
  objectUrls.set(key, objectUrl)
  return objectUrl
}

async function readCachedImage(key: string): Promise<CachedImageRecord | null> {
  const db = await openImageDb()
  return new Promise((resolve) => {
    const request = db.transaction(IMAGE_STORE, 'readonly').objectStore(IMAGE_STORE).get(key)
    request.onsuccess = () => resolve((request.result as CachedImageRecord | undefined) || null)
    request.onerror = () => resolve(null)
  })
}

async function writeCachedImage(key: string, blob: Blob) {
  const db = await openImageDb()
  await txDone(db.transaction(IMAGE_STORE, 'readwrite').objectStore(IMAGE_STORE).put({
    key,
    blob,
    savedAt: Date.now(),
  } satisfies CachedImageRecord))
}

function openImageDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(IMAGE_DB_NAME, IMAGE_DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(IMAGE_STORE)) {
        db.createObjectStore(IMAGE_STORE, { keyPath: 'key' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  return dbPromise
}

function txDone(request: IDBRequest) {
  return new Promise<void>((resolve, reject) => {
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
  })
}
