<template>
  <div
    class="catalog-page"
    :class="{
      'is-landscape': isLandscape,
      'is-portrait': !isLandscape,
      'is-phone': isPhoneViewport,
      'is-phone-landscape': isPhoneLandscape,
    }"
  >
    <!-- ====== Header Bar ====== -->
    <header class="catalog-header">
      <div class="header-top">
        <div class="header-brand">
          <span class="brand-main">嘉嘉服饰</span>
          <span class="brand-sub">现货选款</span>
        </div>
        <div class="search-box">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索款号 / 商品名"
            clearable
            size="large"
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
            class="search-input"
          />
        </div>
        <div class="header-actions">
          <button class="mode-btn" type="button">
            <UserRound :size="17" />
            游客模式
          </button>
          <button class="icon-filter-btn" type="button" aria-label="筛选">
            <SlidersHorizontal :size="20" />
          </button>
        </div>
      </div>
      <div class="filter-chips">
        <el-button
          v-for="mode in stockModeOptions"
          :key="mode.code"
          type="default"
          size="large"
          round
          @click="setStockMode(mode.code!)"
          class="chip-btn"
          :class="{ 'is-active': filters.stockMode === mode.code }"
        >
          {{ mode.name }}
        </el-button>
        <el-button
          type="default"
          size="large"
          round
          @click="toggleHasImage"
          class="chip-btn"
          :class="{ 'is-active': Boolean(filters.hasImage) }"
        >
          有图
        </el-button>
        <el-select
          v-if="filterData.categories.length"
          v-model="filters.categoryId"
          placeholder="分类"
          clearable
          size="large"
          @change="handleSearch"
          class="filter-select"
        >
          <el-option
            v-for="c in filterData.categories"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-select
          v-if="filterData.colors.length"
          v-model="filters.colorId"
          placeholder="颜色"
          clearable
          size="large"
          @change="handleSearch"
          class="filter-select"
        >
          <el-option
            v-for="c in filterData.colors"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-select
          v-if="filterData.sizes.length"
          v-model="filters.sizeId"
          placeholder="尺码"
          clearable
          size="large"
          @change="handleSearch"
          class="filter-select"
        >
          <el-option
            v-for="s in filterData.sizes"
            :key="s.id"
            :label="s.name"
            :value="s.id"
          />
        </el-select>
      </div>
    </header>

    <!-- ====== Main Content ====== -->
    <div class="catalog-body">
      <!-- Loading -->
      <div v-if="loading && products.length === 0" class="loading-state">
        <div class="loading-grid">
          <div v-for="i in 6" :key="i" class="skeleton-card">
            <div class="skeleton-img" />
            <div class="skeleton-line skeleton-line-short" />
            <div class="skeleton-line" />
          </div>
        </div>
      </div>

      <!-- Error -->
      <div v-else-if="error && products.length === 0" class="empty-state">
        <AlertTriangle class="empty-icon" :size="48" />
        <p class="empty-title">加载失败</p>
        <p class="empty-desc">{{ error }}</p>
        <el-button size="large" round @click="fetchProducts">重新加载</el-button>
      </div>

      <!-- Empty -->
      <div v-else-if="!loading && products.length === 0" class="empty-state">
        <Inbox class="empty-icon" :size="48" />
        <p class="empty-title">暂无商品</p>
        <p class="empty-desc">尝试调整筛选条件</p>
      </div>

      <!-- Product Grid + Detail -->
      <template v-else>
        <!-- Grid area -->
        <div
          class="grid-area"
          :class="{ 'with-detail-landscape': isLandscape && selectedProduct }"
          @scroll="handleGridScroll"
        >
          <div class="product-grid">
            <div
              v-for="product in products"
              :key="product.id"
              class="product-card"
              :class="{ selected: selectedProduct?.id === product.id }"
              @click="selectProduct(product)"
            >
              <div
                class="card-image"
                :class="{ zoomable: productHasImages(product) }"
                @click.stop="handleCardImageClick(product)"
              >
                <CachedImage
                  v-if="product.mainImageUrl"
                  :src="product.mainImageUrl"
                  :alt="product.name"
                  loading="lazy"
                  image-class="card-img"
                />
                <div v-else class="card-img-placeholder">
                  <ImageIcon class="placeholder-icon" :size="34" />
                </div>
                <span class="stock-badge" :class="{ out: !product.hasStock }">
                  {{ product.stockStatus }}
                </span>
              </div>
              <div class="card-info">
                <div class="card-code">{{ product.productCode }}</div>
                <div class="card-name">{{ product.name }}</div>
                <div v-if="product.colors.length" class="card-colors">
                  <span
                    v-for="color in product.colors.slice(0, 5)"
                    :key="color.id"
                    class="color-dot"
                    :style="colorDotStyle(color)"
                    :title="color.name"
                  />
                  <span v-if="product.colors.length > 5" class="color-more">
                    +{{ product.colors.length - 5 }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div class="load-more-state">
            <span v-if="loadingMore">继续加载中...</span>
            <button v-else-if="loadMoreError" type="button" @click="loadNextPage">
              加载失败，点击重试
            </button>
            <span v-else-if="!hasMore && products.length">已加载全部 {{ total }} 款</span>
          </div>
        </div>

        <!-- ====== Detail Panel (landscape) ====== -->
        <div v-if="isLandscape && selectedProduct" class="detail-panel">
          <DetailView
            :product="selectedProduct"
            :all-images="selectedProductDetailImages"
            :fullscreen-images="selectedProductGalleryImages"
            @open-fullscreen="openFullscreen"
            @close="selectedProduct = null"
          />
        </div>
      </template>
    </div>

    <!-- ====== Detail Drawer (portrait) ====== -->
    <el-drawer
      v-if="!isLandscape"
      v-model="drawerVisible"
      direction="btt"
      :size="drawerHeight"
      :close-on-click-modal="true"
      :destroy-on-close="false"
      :with-header="false"
      class="detail-drawer"
    >
      <div class="drawer-grabber" />
      <DetailView
        v-if="selectedProduct"
        :product="selectedProduct"
        :all-images="selectedProductDetailImages"
        :fullscreen-images="selectedProductGalleryImages"
        :show-close="false"
        @open-fullscreen="openFullscreen"
      />
    </el-drawer>

    <div v-if="!isLandscape" class="mobile-action-bar">
      <button class="mobile-action" type="button">
        <SlidersHorizontal :size="18" />
        筛选
      </button>
      <button class="mobile-action mobile-primary" type="button">
        <ShoppingBag :size="18" />
        选款清单
        <span class="mobile-count">0</span>
      </button>
      <button class="mobile-action" type="button">
        <UserRound :size="18" />
        游客模式
      </button>
    </div>

    <div v-if="isPhoneLandscape" class="phone-orientation-lock" role="status">
      <div class="phone-lock-card">
        <div class="phone-lock-icon">
          <RotateCcw :size="28" />
        </div>
        <div>
          <p class="phone-lock-title">请切回竖屏浏览</p>
          <p class="phone-lock-desc">手机版选款页仅支持竖屏，方便客户连续滑动看款。</p>
        </div>
      </div>
    </div>

    <!-- ====== Fullscreen Image Viewer ====== -->
    <Teleport to="body">
      <div v-if="fullscreenVisible" class="fullscreen-overlay" @click.self="closeFullscreen">
        <button class="fs-close" @click="closeFullscreen" aria-label="关闭">
          <X :size="26" />
        </button>
        <button
          v-if="fullscreenImages.length > 1"
          class="fs-nav fs-prev"
          @click.stop="fsPrev"
          aria-label="上一张"
        >
          <ChevronLeft :size="34" />
        </button>
        <button
          v-if="fullscreenImages.length > 1"
          class="fs-nav fs-next"
          @click.stop="fsNext"
          aria-label="下一张"
        >
          <ChevronRight :size="34" />
        </button>
        <div
          class="fs-image-wrap"
          :data-active-index="fullscreenIndex"
          @dblclick.prevent
          @pointerdown="handleFullscreenPointerDown"
          @pointermove="handleFullscreenPointerMove"
          @pointerup="handleFullscreenPointerUp"
          @pointercancel="resetFullscreenSwipe"
          @pointerleave="resetFullscreenSwipe"
        >
          <div class="fs-image-track" :style="fullscreenTrackStyle">
            <div
              v-for="(img, idx) in fullscreenImages"
              :key="`${img}-${idx}`"
              class="fs-image-slide"
            >
              <CachedImage
                :src="img"
                :alt="`${fullscreenProduct?.name || ''} - ${idx + 1}`"
                image-class="fs-image"
                loading="eager"
              />
            </div>
          </div>
        </div>
        <div class="fs-info">
          <span class="fs-info-text">
            {{ fullscreenProduct?.productCode }} · {{ fullscreenProduct?.name }}
          </span>
          <span class="fs-counter">{{ fullscreenIndex + 1 }} / {{ fullscreenImages.length }}</span>
        </div>
        <div v-if="fullscreenImages.length > 1" class="fs-thumbs">
          <button
            v-for="(img, idx) in fullscreenImages"
            :key="idx"
            class="fs-thumb"
            :class="{ active: idx === fullscreenIndex }"
            @click="fullscreenIndex = idx"
          >
            <CachedImage :src="img" :alt="`缩略图 ${idx + 1}`" />
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Image as ImageIcon,
  Inbox,
  RotateCcw,
  ShoppingBag,
  SlidersHorizontal,
  UserRound,
  X,
} from 'lucide-vue-next'
import {
  getCatalogProducts,
  getCatalogProductById,
  getCatalogFilters,
  type CatalogProductVO,
  type CatalogFiltersVO,
  type FilterOption,
  type ColorSizeEntry,
} from '@/api/catalog'
import { filePreviewUrl } from '@/api/file'
import {
  readCatalogProductsCache,
  writeCatalogProductsCache,
  type CatalogCacheFilters,
} from '@/utils/catalogCache'
import CachedImage from '@/components/CachedImage.vue'
import DetailView from './DetailView.vue'

// --- orientation ---
const windowWidth = ref(window.innerWidth)
const windowHeight = ref(window.innerHeight)
const isLandscape = computed(() => windowWidth.value > windowHeight.value)
const isPhoneViewport = computed(() => Math.min(windowWidth.value, windowHeight.value) <= 480 && Math.max(windowWidth.value, windowHeight.value) <= 950)
const isPhoneLandscape = computed(() => isPhoneViewport.value && isLandscape.value)

function onResize() {
  windowWidth.value = window.innerWidth
  windowHeight.value = window.innerHeight
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

// --- filters ---
const filters = reactive({
  keyword: '',
  stockMode: 'all' as string,
  hasImage: undefined as boolean | undefined,
  categoryId: undefined as number | undefined,
  colorId: undefined as number | undefined,
  sizeId: undefined as number | undefined,
})

const stockModeOptions: FilterOption[] = [
  { id: null, name: '全部', code: 'all' },
  { id: null, name: '现货', code: 'in_stock' },
]

function setStockMode(mode: string) {
  filters.stockMode = mode
  handleSearch()
}

function toggleHasImage() {
  filters.hasImage = filters.hasImage ? undefined : true
  handleSearch()
}

// --- filter data from API ---
const filterData = reactive<CatalogFiltersVO>({
  categories: [],
  colors: [],
  sizes: [],
  stockModes: [],
})

async function fetchFilters() {
  try {
    const res = await getCatalogFilters()
    if (res.code === 200 && res.data) {
      filterData.categories = res.data.categories || []
      filterData.colors = res.data.colors || []
      filterData.sizes = res.data.sizes || []
      filterData.stockModes = res.data.stockModes || []
    }
  } catch {
    // filters are optional; fail silently
  }
}

// --- products ---
const products = ref<CatalogProductVO[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const loadMoreError = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const hasMore = computed(() => products.value.length < total.value)
let productRequestId = 0

function currentCacheFilters(): CatalogCacheFilters {
  return {
    keyword: filters.keyword,
    stockMode: filters.stockMode,
    hasImage: filters.hasImage,
    categoryId: filters.categoryId,
    colorId: filters.colorId,
    sizeId: filters.sizeId,
  }
}

function productQueryParams(page: number) {
  const params: Record<string, unknown> = {
    current: page,
    size: pageSize,
    stockMode: filters.stockMode,
  }
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.hasImage !== undefined) params.hasImage = filters.hasImage
  if (filters.categoryId) params.categoryId = filters.categoryId
  if (filters.colorId) params.colorId = filters.colorId
  if (filters.sizeId) params.sizeId = filters.sizeId
  return params
}

function applyCachedProducts() {
  const cached = readCatalogProductsCache(currentCacheFilters())
  if (!cached) return
  products.value = cached.products.map(normalizeCatalogProduct)
  total.value = cached.total || products.value.length
  currentPage.value = cached.current || Math.max(1, Math.ceil(products.value.length / pageSize))
}

async function fetchProducts({ reset = true, background = false } = {}) {
  const requestId = ++productRequestId
  if (reset) {
    currentPage.value = 1
    if (!background) {
      loading.value = true
    }
  } else {
    loadingMore.value = true
  }
  error.value = ''
  loadMoreError.value = false

  try {
    const page = reset ? 1 : currentPage.value + 1
    const res = await getCatalogProducts(productQueryParams(page))
    if (requestId !== productRequestId) return
    if (res.code === 200 && res.data) {
      const nextRecords = (res.data.records || []).map(normalizeCatalogProduct)
      products.value = reset ? nextRecords : mergeProducts(products.value, nextRecords)
      total.value = res.data.total || 0
      currentPage.value = res.data.current || page
      writeCatalogProductsCache(currentCacheFilters(), res.data, products.value)
    } else {
      error.value = (res as any).message || '加载失败'
      if (!reset) loadMoreError.value = true
    }
  } catch (e: any) {
    if (requestId !== productRequestId) return
    const message = e?.response?.data?.message || e?.message || '网络错误'
    if (reset) {
      error.value = message
    } else {
      loadMoreError.value = true
    }
  } finally {
    if (requestId === productRequestId) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

function handleSearch() {
  productRequestId++
  currentPage.value = 1
  products.value = []
  total.value = 0
  applyCachedProducts()
  fetchProducts({ reset: true, background: products.value.length > 0 })
}

function mergeProducts(existing: CatalogProductVO[], nextRecords: CatalogProductVO[]) {
  const seen = new Set(existing.map((item) => item.id))
  const merged = [...existing]
  for (const item of nextRecords) {
    if (!seen.has(item.id)) {
      merged.push(item)
      seen.add(item.id)
    }
  }
  return merged
}

function handleGridScroll(event: Event) {
  const el = event.currentTarget as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 720) {
    loadNextPage()
  }
}

function loadNextPage() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  fetchProducts({ reset: false })
}

function productImages(product: CatalogProductVO) {
  const imgs: string[] = []
  if (product.mainImageUrl) imgs.push(withPreviewToken(product.mainImageUrl))
  if (product.imageUrls) imgs.push(...product.imageUrls.map(withPreviewToken))
  return [...new Set(imgs.filter(Boolean))]
}

function skuImages(product: CatalogProductVO) {
  const imgs = (product.skus || []).flatMap((sku) => sku.imageUrls || []).map(withPreviewToken)
  return [...new Set(imgs.filter(Boolean))]
}

function detailImages(product: CatalogProductVO) {
  return [...new Set([...productImages(product), ...skuImages(product)].filter(Boolean))]
}

function withPreviewToken(url: string) {
  const match = url.match(/^\/api\/files\/(\d+)\/preview(?:\?.*)?$/)
  return match ? filePreviewUrl(match[1]) : url
}

function normalizeCatalogProduct(product: CatalogProductVO): CatalogProductVO {
  return {
    ...product,
    mainImageUrl: product.mainImageUrl ? withPreviewToken(product.mainImageUrl) : product.mainImageUrl,
    imageUrls: (product.imageUrls || []).map(withPreviewToken),
    skus: (product.skus || []).map((sku) => ({
      ...sku,
      imageUrls: (sku.imageUrls || []).map(withPreviewToken),
    })),
  }
}

function productHasImages(product: CatalogProductVO) {
  return productImages(product).length > 0
}

function handleCardImageClick(product: CatalogProductVO) {
  if (productHasImages(product)) {
    openFullscreen(product, 0)
  } else {
    selectProduct(product)
  }
}

function colorDotStyle(color: ColorSizeEntry) {
  const code = (color.code || '').trim()
  const colorMap: Record<string, string> = {
    BLACK: '#1f1f1f',
    WHITE: '#f7f2ea',
    GRAY: '#8f8a84',
    GREY: '#8f8a84',
    BLUE: '#315f9b',
    RED: '#a94442',
    GREEN: '#50745d',
    YELLOW: '#d4a83f',
    PINK: '#d98ba5',
    PURPLE: '#6b5b7b',
    BROWN: '#7a563a',
  }
  if (/^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(code)) {
    return { backgroundColor: code }
  }
  return { backgroundColor: colorMap[code.toUpperCase()] || 'var(--accent-gold-light)' }
}

// --- product selection ---
const selectedProduct = ref<CatalogProductVO | null>(null)
const drawerVisible = ref(false)
const drawerHeight = computed(() => {
  const ratio = isPhoneViewport.value ? 0.68 : 0.56
  return `${Math.round(windowHeight.value * ratio)}px`
})

const selectedProductGalleryImages = computed(() => {
  if (!selectedProduct.value) return []
  return productImages(selectedProduct.value)
})

const selectedProductDetailImages = computed(() => {
  if (!selectedProduct.value) return []
  return detailImages(selectedProduct.value)
})

async function selectProduct(product: CatalogProductVO) {
  try {
    const res = await getCatalogProductById(product.id)
    if (res.code === 200 && res.data) {
      selectedProduct.value = normalizeCatalogProduct(res.data)
    } else {
      selectedProduct.value = product
    }
  } catch {
    selectedProduct.value = product
  }
  if (isLandscape.value) {
    drawerVisible.value = false
  } else {
    drawerVisible.value = true
  }
}

watch(isLandscape, (landscape) => {
  if (landscape && selectedProduct.value) {
    drawerVisible.value = false
  }
})

// --- fullscreen image viewer ---
const fullscreenVisible = ref(false)
const fullscreenProduct = ref<CatalogProductVO | null>(null)
const fullscreenImages = ref<string[]>([])
const fullscreenIndex = ref(0)
const fullscreenSwipeStartX = ref<number | null>(null)
const fullscreenSwipeStartY = ref<number | null>(null)
const fullscreenSwipeOffsetX = ref(0)
const fullscreenDragging = ref(false)

const fullscreenTrackStyle = computed(() => ({
  transform: `translate3d(calc(${-fullscreenIndex.value * 100}% + ${fullscreenSwipeOffsetX.value}px), 0, 0)`,
  transition: fullscreenDragging.value ? 'none' : 'transform 220ms cubic-bezier(0.22, 0.61, 0.36, 1)',
}))

function openFullscreen(product: CatalogProductVO, startIndex = 0) {
  const imgs = productImages(product)
  if (imgs.length === 0) return
  fullscreenProduct.value = product
  fullscreenImages.value = imgs
  fullscreenIndex.value = Math.max(0, Math.min(startIndex, fullscreenImages.value.length - 1))
  fullscreenVisible.value = true
}

function closeFullscreen() {
  fullscreenVisible.value = false
}

function fsPrev() {
  if (fullscreenImages.value.length === 0) return
  fullscreenIndex.value =
    (fullscreenIndex.value - 1 + fullscreenImages.value.length) % fullscreenImages.value.length
}

function fsNext() {
  if (fullscreenImages.value.length === 0) return
  fullscreenIndex.value = (fullscreenIndex.value + 1) % fullscreenImages.value.length
}

function handleFullscreenPointerDown(event: PointerEvent) {
  if (fullscreenImages.value.length <= 1) return
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  fullscreenSwipeStartX.value = event.clientX
  fullscreenSwipeStartY.value = event.clientY
  fullscreenSwipeOffsetX.value = 0
  fullscreenDragging.value = true
}

function handleFullscreenPointerMove(event: PointerEvent) {
  if (fullscreenSwipeStartX.value === null || fullscreenSwipeStartY.value === null) return
  const dx = event.clientX - fullscreenSwipeStartX.value
  const dy = event.clientY - fullscreenSwipeStartY.value
  if (Math.abs(dx) <= Math.abs(dy)) return
  event.preventDefault()
  fullscreenSwipeOffsetX.value = dx
}

function handleFullscreenPointerUp(event: PointerEvent) {
  if (fullscreenSwipeStartX.value === null || fullscreenSwipeStartY.value === null) return
  const dx = event.clientX - fullscreenSwipeStartX.value
  const dy = event.clientY - fullscreenSwipeStartY.value
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
  resetFullscreenSwipe()
  if (!isHorizontalSwipe(dx, dy)) return
  if (dx < 0) {
    fsNext()
  } else {
    fsPrev()
  }
}

function resetFullscreenSwipe() {
  fullscreenSwipeStartX.value = null
  fullscreenSwipeStartY.value = null
  fullscreenSwipeOffsetX.value = 0
  fullscreenDragging.value = false
}

function isHorizontalSwipe(dx: number, dy: number) {
  return Math.abs(dx) >= 44 && Math.abs(dx) > Math.abs(dy) * 1.25
}

function onKeydown(e: KeyboardEvent) {
  if (!fullscreenVisible.value) return
  if (e.key === 'Escape') closeFullscreen()
  if (e.key === 'ArrowLeft') fsPrev()
  if (e.key === 'ArrowRight') fsNext()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

function resetViewportScale() {
  window.scrollTo(0, 0)
  document.documentElement.scrollTop = 0
  document.body.scrollTop = 0
}

let lastSingleTouchEnd = 0
let touchStartedWithSingleFinger = false

function recordTouchStart(event: TouchEvent) {
  touchStartedWithSingleFinger = event.touches.length === 1
}

function preventDoubleTapZoom(event: TouchEvent) {
  if (!touchStartedWithSingleFinger || event.touches.length > 0 || event.changedTouches.length !== 1) {
    if (event.touches.length === 0) touchStartedWithSingleFinger = false
    return
  }
  const now = Date.now()
  if (now - lastSingleTouchEnd < 300) {
    event.preventDefault()
  }
  lastSingleTouchEnd = now
  touchStartedWithSingleFinger = false
}

onMounted(() => {
  window.addEventListener('orientationchange', resetViewportScale)
  window.visualViewport?.addEventListener('resize', resetViewportScale)
  document.addEventListener('touchstart', recordTouchStart, { passive: true })
  document.addEventListener('touchend', preventDoubleTapZoom, { passive: false })
})
onUnmounted(() => {
  window.removeEventListener('orientationchange', resetViewportScale)
  window.visualViewport?.removeEventListener('resize', resetViewportScale)
  document.removeEventListener('touchstart', recordTouchStart)
  document.removeEventListener('touchend', preventDoubleTapZoom)
})

// --- init ---
onMounted(() => {
  fetchFilters()
  applyCachedProducts()
  fetchProducts({ reset: true, background: products.value.length > 0 })
})
</script>

<style scoped>
/* ================================================================
   CSS Custom Properties — Quiet Luxury Palette
   ================================================================ */
.catalog-page {
  --bg-primary: #fbfaf8;
  --bg-secondary: #f4efe8;
  --bg-card: #FFFFFF;
  --bg-skeleton: #eee8df;
  --text-primary: #191714;
  --text-secondary: #5f5a52;
  --text-muted: #9a9287;
  --border-light: #e5ddd2;
  --border-medium: #d0bea8;
  --accent-gold: #9b6b22;
  --accent-gold-dark: #7e5414;
  --accent-gold-light: #eadfcf;
  --badge-in-stock: #a6782a;
  --badge-out: #a49a90;
  --shadow-card: 0 2px 10px rgba(44, 35, 23, 0.08);
  --shadow-panel: 0 12px 30px rgba(44, 35, 23, 0.1);
  --radius-sm: 7px;
  --radius-md: 10px;
  --el-color-primary: var(--accent-gold);
  --el-color-primary-light-3: #b88b48;
  --el-color-primary-light-5: #cba873;
  --el-color-primary-light-7: #dec8a7;
  --el-color-primary-light-9: #f5efe5;
  --el-color-primary-dark-2: var(--accent-gold-dark);

  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at 18% 0%, rgba(176, 126, 48, 0.08), transparent 34%),
    linear-gradient(180deg, #fffdfb 0%, var(--bg-primary) 62%, #f8f5f0 100%);
  color: var(--text-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  overflow: hidden;
  user-select: none;
}

/* ================================================================
   Header
   ================================================================ */
.catalog-header {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 14px 28px 12px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid var(--border-light);
  gap: 14px;
  z-index: 10;
}

.header-top {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(190px, 1fr) minmax(280px, 430px) minmax(170px, 1fr);
  align-items: center;
  gap: 18px;
}

.header-brand {
  color: var(--text-primary);
  white-space: nowrap;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.brand-main {
  font-size: 24px;
  font-weight: 760;
  letter-spacing: 0;
}

.brand-sub {
  font-size: 15px;
  font-weight: 540;
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}

.mode-btn,
.icon-filter-btn,
.mobile-action {
  border: 1px solid var(--border-medium);
  background: rgba(255, 255, 255, 0.72);
  color: var(--accent-gold-dark);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 38px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 650;
}

.mode-btn {
  padding: 0 15px;
}

.icon-filter-btn {
  width: 38px;
  padding: 0;
  color: #26231e;
  border-color: transparent;
  background: transparent;
}

.search-box {
  width: 100%;
}

.search-input :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #d9cfc3;
  box-shadow: none;
  transition: border-color 0.2s;
}
.search-input :deep(.el-input__wrapper:hover) {
  border-color: var(--accent-gold);
}
.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent-gold);
  box-shadow: 0 0 0 2px rgba(155, 107, 34, 0.12);
}

.filter-chips {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.chip-btn {
  --el-button-bg-color: var(--bg-secondary);
  --el-button-border-color: var(--border-light);
  --el-button-text-color: var(--text-secondary);
  --el-button-hover-bg-color: var(--bg-secondary);
  --el-button-hover-border-color: var(--accent-gold);
  --el-button-hover-text-color: var(--text-primary);
  height: 36px;
  min-width: 68px;
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s;
}
.chip-btn.is-active {
  background-color: var(--accent-gold);
  border-color: var(--accent-gold);
  color: #fff;
}
.chip-btn.is-active:hover,
.chip-btn.is-active:focus {
  background-color: var(--accent-gold-dark);
  border-color: var(--accent-gold-dark);
  color: #fff;
}

.filter-select {
  width: 112px;
}
.filter-select :deep(.el-input__wrapper) {
  min-height: 36px;
  border-radius: 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  box-shadow: none;
}

/* ================================================================
   Body
   ================================================================ */
.catalog-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
  padding-bottom: env(safe-area-inset-bottom);
}

/* ================================================================
   Grid Area
   ================================================================ */
.grid-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 18px 28px 34px;
  -webkit-overflow-scrolling: touch;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.is-portrait .product-grid {
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.is-landscape .with-detail-landscape .product-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

/* ================================================================
   Product Card
   ================================================================ */
.product-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: var(--shadow-card);
}
.product-card:hover {
  border-color: var(--accent-gold);
  box-shadow: 0 8px 20px rgba(44, 35, 23, 0.1);
}
.product-card.selected {
  border-color: var(--accent-gold);
  box-shadow: 0 0 0 2px rgba(155, 107, 34, 0.15), var(--shadow-card);
}
.product-card.selected .card-image::after {
  content: '✓';
  position: absolute;
  top: 9px;
  right: 9px;
  z-index: 3;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-gold);
  color: #fff;
  border: 2px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 8px rgba(44, 35, 23, 0.16);
  font-size: 15px;
  font-weight: 800;
  line-height: 21px;
  text-align: center;
}
.product-card:active {
  transform: scale(0.985);
}

.card-image {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  background: var(--bg-secondary);
  overflow: hidden;
}

.card-image.zoomable {
  cursor: zoom-in;
}

.card-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}
.product-card:hover .card-img {
  transform: scale(1.04);
}

.card-img-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
}
.placeholder-icon {
  color: var(--text-muted);
  opacity: 0.55;
}

.stock-badge {
  position: absolute;
  right: 9px;
  bottom: 9px;
  padding: 4px 9px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(255, 250, 243, 0.92);
  color: var(--accent-gold-dark);
  border: 1px solid rgba(155, 107, 34, 0.28);
  backdrop-filter: blur(8px);
}
.stock-badge.out {
  background: rgba(244, 240, 235, 0.92);
  color: #8d847a;
  border-color: rgba(164, 154, 144, 0.32);
}

.card-info {
  padding: 10px 11px 12px;
}

.card-code {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 760;
  margin-bottom: 3px;
}

.card-name {
  font-size: 13px;
  font-weight: 520;
  color: var(--text-primary);
  line-height: 1.32;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-colors {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
}

.color-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--accent-gold-light);
  border: 1px solid var(--border-medium);
  flex-shrink: 0;
}

.color-more {
  font-size: 11px;
  color: var(--text-muted);
}

/* ================================================================
   Infinite loading state
   ================================================================ */
.load-more-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 54px;
  padding: 18px 0 2px;
  color: var(--text-muted);
  font-size: 13px;
}

.load-more-state button {
  height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  border: 1px solid var(--border-medium);
  background: rgba(255, 255, 255, 0.78);
  color: var(--accent-gold-dark);
  font-weight: 600;
  cursor: pointer;
}

/* ================================================================
   Detail Panel (landscape)
   ================================================================ */
.detail-panel {
  width: 452px;
  flex-shrink: 0;
  margin: 18px 28px 34px 0;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  box-shadow: var(--shadow-panel);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* ================================================================
   Loading Skeleton
   ================================================================ */
.loading-state {
  flex: 1;
  display: flex;
  align-items: flex-start;
  padding: 20px 24px;
}

.loading-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  width: 100%;
}
.is-portrait .loading-grid {
  grid-template-columns: repeat(2, 1fr);
}

.skeleton-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  overflow: hidden;
}

.skeleton-img {
  width: 100%;
  padding-top: 100%;
  background: var(--bg-skeleton);
  animation: shimmer 1.5s ease-in-out infinite;
}

.skeleton-line {
  height: 12px;
  margin: 8px 14px;
  border-radius: 4px;
  background: var(--bg-skeleton);
  animation: shimmer 1.5s ease-in-out infinite;
}
.skeleton-line-short {
  width: 50%;
}

@keyframes shimmer {
  0% { opacity: 0.5; }
  50% { opacity: 1; }
  100% { opacity: 0.5; }
}

/* ================================================================
   Empty / Error States
   ================================================================ */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: var(--text-muted);
}

.empty-icon {
  margin-bottom: 8px;
  color: var(--accent-gold);
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-secondary);
  margin: 0;
}

.empty-desc {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

/* ================================================================
   Drawer overrides (portrait)
   ================================================================ */
.drawer-grabber {
  width: 44px;
  height: 4px;
  margin: 10px auto 8px;
  border-radius: 999px;
  background: #d4ccc2;
}
:global(.detail-drawer .el-drawer__body) {
  padding: 0;
  overflow-y: auto;
}
:global(.detail-drawer .el-drawer) {
  border-radius: 22px 22px 0 0;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -14px 40px rgba(34, 28, 20, 0.14);
}

/* ================================================================
   Fullscreen Image Viewer
   ================================================================ */
.fullscreen-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: radial-gradient(circle at 50% 35%, #1c1b18 0%, #090908 72%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  animation: fsFadeIn 0.2s ease;
}

@keyframes fsFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.fs-close {
  position: fixed;
  top: 22px;
  right: 24px;
  z-index: 10001;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(255,255,255,0.12);
  color: #fff;
  font-size: 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  backdrop-filter: blur(4px);
}
.fs-close:hover {
  background: rgba(255,255,255,0.2);
}

.fs-nav {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10001;
  width: 50px;
  height: 50px;
  border: none;
  border-radius: 50%;
  background: rgba(255,255,255,0.18);
  color: #fff;
  font-size: 36px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  backdrop-filter: blur(4px);
}
.fs-nav:hover {
  background: rgba(255,255,255,0.16);
}
.fs-prev { left: 34px; }
.fs-next { right: 34px; }

.fs-image-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 70px 100px 124px;
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  overflow: hidden;
  touch-action: pan-y pinch-zoom;
  user-select: none;
  -webkit-user-select: none;
}

.fs-image-track {
  width: 100%;
  height: 100%;
  display: flex;
  will-change: transform;
}

.fs-image-slide {
  width: 100%;
  height: 100%;
  flex: 0 0 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fs-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 16px;
  box-shadow: 0 22px 60px rgba(0, 0, 0, 0.35);
  -webkit-user-drag: none;
}

.fs-info {
  position: fixed;
  top: 28px;
  left: 32px;
  transform: none;
  display: flex;
  align-items: center;
  gap: 16px;
  color: rgba(255,255,255,0.86);
  font-size: 14px;
  z-index: 10001;
}

.fs-info-text {
  font-weight: 500;
}

.fs-counter {
  opacity: 0.6;
}

.fs-thumbs {
  position: fixed;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  overflow-x: auto;
  max-width: 90vw;
  padding: 4px 0;
  z-index: 10001;
  -webkit-overflow-scrolling: touch;
}

.fs-thumb {
  width: 58px;
  height: 58px;
  border-radius: 8px;
  border: 2px solid rgba(255,255,255,0.22);
  overflow: hidden;
  cursor: pointer;
  padding: 0;
  background: none;
  flex-shrink: 0;
  transition: border-color 0.2s, transform 0.2s;
}
.fs-thumb.active {
  border-color: var(--accent-gold);
  transform: scale(1.08);
}

.mobile-action-bar {
  position: fixed;
  left: 18px;
  right: 18px;
  bottom: max(14px, env(safe-area-inset-bottom));
  z-index: 1200;
  display: grid;
  grid-template-columns: 1fr 1.28fr 1fr;
  gap: 10px;
  pointer-events: none;
}

.mobile-action {
  pointer-events: auto;
  height: 46px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--accent-gold-dark);
  box-shadow: 0 8px 24px rgba(44, 35, 23, 0.1);
}

.mobile-primary {
  border-color: var(--border-medium);
}

.mobile-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.fs-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.phone-orientation-lock {
  position: fixed;
  inset: 0;
  z-index: 10050;
  display: none;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 22% 20%, rgba(155, 107, 34, 0.18), transparent 32%),
    linear-gradient(135deg, #fffdfb 0%, #f4efe8 100%);
}

.phone-lock-card {
  width: min(440px, 86vw);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid var(--border-medium);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 48px rgba(44, 35, 23, 0.16);
}

.phone-lock-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--accent-gold);
}

.phone-lock-title {
  margin: 0 0 5px;
  font-size: 17px;
  font-weight: 760;
  color: var(--text-primary);
}

.phone-lock-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-secondary);
}

/* ================================================================
   Responsive fine-tuning
   ================================================================ */
@media (max-width: 1024px) {
  .catalog-header {
    padding: 12px 14px 10px;
    gap: 10px;
  }
  .header-top {
    grid-template-columns: 1fr auto;
    gap: 10px;
  }
  .search-box {
    grid-column: 1 / -1;
    grid-row: 2;
  }
  .header-actions {
    grid-column: 2;
    grid-row: 1;
  }
  .brand-main {
    font-size: 20px;
  }
  .brand-sub {
    font-size: 13px;
  }
  .mode-btn {
    height: 34px;
    padding: 0 10px;
    font-size: 12px;
  }
  .icon-filter-btn {
    width: 34px;
    height: 34px;
  }
  .filter-chips {
    gap: 7px;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 2px;
  }
  .chip-btn {
    height: 31px;
    min-width: 56px;
    padding: 0 12px;
    font-size: 12px;
  }
  .filter-select {
    width: 86px;
    flex: 0 0 auto;
  }
  .filter-select :deep(.el-input__wrapper) {
    min-height: 31px;
  }
  .grid-area {
    padding: 12px 14px 82px;
  }
  .card-image {
    aspect-ratio: 3 / 4;
  }
  .card-info {
    padding: 8px 8px 10px;
  }
  .card-code {
    font-size: 13px;
  }
  .card-name {
    font-size: 12px;
  }
  .color-dot {
    width: 14px;
    height: 14px;
  }
  .stock-badge {
    right: 7px;
    bottom: 7px;
    padding: 3px 7px;
    font-size: 10px;
  }
}

@media (max-width: 480px) {
  .catalog-page {
    background:
      radial-gradient(circle at 12% 0%, rgba(155, 107, 34, 0.1), transparent 34%),
      linear-gradient(180deg, #fffdfb 0%, #fbfaf8 48%, #f5f0ea 100%);
  }

  .catalog-header {
    padding: max(10px, env(safe-area-inset-top)) 12px 9px;
    gap: 9px;
  }

  .header-top {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
  }

  .header-brand {
    gap: 8px;
    min-width: 0;
  }

  .brand-main {
    font-size: 20px;
    line-height: 1.1;
  }

  .brand-sub {
    font-size: 12px;
  }

  .header-actions {
    gap: 6px;
  }

  .mode-btn {
    height: 32px;
    padding: 0 9px;
    font-size: 12px;
    border-color: #d8c4a8;
    background: rgba(255, 252, 248, 0.86);
  }

  .icon-filter-btn {
    display: none;
  }

  .search-input :deep(.el-input__wrapper) {
    min-height: 40px;
    border-radius: 8px;
  }

  .search-input :deep(.el-input__inner) {
    font-size: 14px;
  }

  .filter-chips {
    gap: 7px;
    margin: 0 -12px;
    padding: 0 12px 2px;
    scrollbar-width: none;
  }

  .filter-chips::-webkit-scrollbar {
    display: none;
  }

  .chip-btn {
    height: 30px;
    min-width: auto;
    padding: 0 12px;
    font-size: 12px;
    border-radius: 8px;
  }

  .filter-select {
    width: 82px;
  }

  .filter-select :deep(.el-input__wrapper) {
    min-height: 30px;
    border-radius: 8px;
  }

  .filter-select :deep(.el-input__inner) {
    font-size: 12px;
  }

  .grid-area {
    padding: 10px 12px 86px;
  }

  .product-grid,
  .is-portrait .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .product-card {
    border-radius: 9px;
    box-shadow: 0 2px 8px rgba(44, 35, 23, 0.07);
  }

  .product-card:hover .card-img {
    transform: none;
  }

  .product-card.selected {
    box-shadow: 0 0 0 1px rgba(155, 107, 34, 0.32), 0 4px 12px rgba(44, 35, 23, 0.09);
  }

  .product-card.selected .card-image::after {
    top: 7px;
    right: 7px;
    width: 21px;
    height: 21px;
    font-size: 13px;
    line-height: 18px;
  }

  .card-info {
    padding: 8px 8px 9px;
  }

  .card-code {
    font-size: 13px;
    margin-bottom: 2px;
  }

  .card-name {
    min-height: 32px;
    font-size: 12px;
    line-height: 1.35;
  }

  .card-colors {
    gap: 6px;
    margin-top: 8px;
  }

  .color-dot {
    width: 13px;
    height: 13px;
  }

  .stock-badge {
    right: 7px;
    bottom: 7px;
    padding: 3px 7px;
    font-size: 10px;
    border-radius: 6px;
  }

  .load-more-state {
    min-height: 48px;
    padding-top: 14px;
    font-size: 12px;
  }

  :global(.detail-drawer .el-drawer) {
    border-radius: 18px 18px 0 0;
  }

  :global(.detail-drawer .el-drawer__body) {
    padding-bottom: calc(10px + env(safe-area-inset-bottom));
  }

  .drawer-grabber {
    width: 40px;
    height: 4px;
    margin: 8px auto 6px;
  }

  .mobile-action-bar {
    left: 10px;
    right: 10px;
    bottom: max(8px, env(safe-area-inset-bottom));
    grid-template-columns: 0.86fr 1.2fr 0.92fr;
    gap: 7px;
  }

  .mobile-action {
    height: 44px;
    gap: 5px;
    border-radius: 9px;
    font-size: 12px;
    box-shadow: 0 8px 22px rgba(44, 35, 23, 0.12);
  }

  .mobile-primary {
    color: #fff;
    border-color: var(--accent-gold);
    background: linear-gradient(180deg, #a7772d 0%, #8c5e1a 100%);
  }

  .mobile-primary .mobile-count {
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
  }

  .fs-close {
    top: max(12px, env(safe-area-inset-top));
    right: 12px;
    width: 38px;
    height: 38px;
  }

  .fs-nav {
    display: none;
  }

  .fs-image-wrap {
    padding: calc(62px + env(safe-area-inset-top)) 14px calc(112px + env(safe-area-inset-bottom));
  }

  .fs-image {
    border-radius: 10px;
    box-shadow: 0 18px 44px rgba(0, 0, 0, 0.36);
  }

  .fs-info {
    top: max(15px, env(safe-area-inset-top));
    left: 14px;
    right: 58px;
    gap: 8px;
    font-size: 12px;
  }

  .fs-info-text {
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .fs-thumbs {
    bottom: max(18px, env(safe-area-inset-bottom));
    max-width: calc(100vw - 24px);
    gap: 7px;
  }

  .fs-thumb {
    width: 48px;
    height: 48px;
    border-radius: 7px;
  }
}

@media (max-width: 950px) and (max-height: 480px) and (orientation: landscape) {
  .is-phone-landscape .catalog-header,
  .is-phone-landscape .catalog-body,
  .is-phone-landscape .mobile-action-bar,
  .is-phone-landscape .fullscreen-overlay {
    display: none;
  }

  .is-phone-landscape .phone-orientation-lock {
    display: flex;
  }
}
</style>
