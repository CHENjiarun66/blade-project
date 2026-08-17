<template>
  <div class="detail-content">
    <!-- Close button (landscape panel) -->
    <button v-if="props.showClose" class="detail-close" @click="$emit('close')" aria-label="关闭">
      <X :size="20" />
    </button>

    <!-- Image Carousel -->
    <div class="detail-carousel">
      <div
        v-if="allImages.length > 0"
        class="carousel-main"
        :data-active-index="carouselIndex"
        @click="openFullscreenFromCarousel"
        @dblclick.prevent
        @pointerdown="handleCarouselPointerDown"
        @pointermove="handleCarouselPointerMove"
        @pointerup="handleCarouselPointerUp"
        @pointercancel="resetCarouselSwipe"
        @pointerleave="resetCarouselSwipe"
      >
        <div class="carousel-track" :style="carouselTrackStyle">
          <div
            v-for="(img, i) in allImages"
            :key="`${img}-${i}`"
            class="carousel-slide"
          >
            <CachedImage :src="img" :alt="`${product.name} ${i + 1}`" image-class="carousel-img" />
          </div>
        </div>
        <button
          v-if="carouselIndex > 0"
          class="carousel-arrow carousel-prev"
          @click.stop="carouselIndex--"
        >
          <ChevronLeft :size="24" />
        </button>
        <button
          v-if="carouselIndex < allImages.length - 1"
          class="carousel-arrow carousel-next"
          @click.stop="carouselIndex++"
        >
          <ChevronRight :size="24" />
        </button>
        <div v-if="allImages.length > 1" class="carousel-dots">
          <span
            v-for="(_, i) in allImages"
            :key="i"
            class="dot"
            :class="{ active: i === carouselIndex }"
            @click.stop="carouselIndex = i"
          />
        </div>
      </div>
      <div v-else class="carousel-placeholder">
        <ImageIcon class="placeholder-icon" :size="34" />
        <p>暂无图片</p>
      </div>
      <!-- Thumbnail strip (thumb variant, falls back to allImages) -->
      <div v-if="stripImages.length > 1" class="carousel-thumbs">
        <button
          v-for="(img, i) in stripImages"
          :key="i"
          class="carousel-thumb"
          :class="{ active: i === carouselIndex }"
          @click="carouselIndex = i"
        >
          <CachedImage :src="img" :alt="`${product.name} ${i + 1}`" />
        </button>
      </div>
    </div>

    <!-- Product Info -->
    <div class="detail-info">
      <div class="detail-code">{{ product.productCode }}</div>
      <h1 class="detail-name">{{ product.name }}</h1>
      <div v-if="product.tags && product.tags.length" class="detail-tags">
        <span v-for="tag in product.tags" :key="tag" class="detail-tag">{{ tag }}</span>
      </div>
      <div v-if="product.categoryName" class="detail-category">分类：{{ product.categoryName }}</div>
    </div>

    <!-- SKU Matrix: color (rows) × size (columns) -->
    <div v-if="skuMatrix.colors.length && skuMatrix.sizes.length" class="sku-matrix">
      <h3 class="sku-matrix-title">颜色 / 尺码现货</h3>
      <div class="sku-table-wrap">
        <table class="sku-table">
          <thead>
            <tr>
              <th class="sku-corner" />
              <th
                v-for="(sid, i) in skuMatrix.sizes"
                :key="sid"
                class="sku-col-header"
              >{{ skuMatrix.sizeNames[i] }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(cid, ci) in skuMatrix.colors" :key="cid">
              <td class="sku-row-header">{{ skuMatrix.colorNames[ci] }}</td>
              <td
                v-for="sid in skuMatrix.sizes"
                :key="sid"
                :class="skuCellClass(cid, sid)"
              >{{ skuCellText(cid, sid) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Color & Size chips (fallback when no SKU matrix) -->
    <div v-if="(product.colors || []).length || (product.sizes || []).length" class="detail-attrs">
      <div v-if="(product.colors || []).length" class="attr-group">
        <span class="attr-label">颜色：</span>
        <span v-for="c in (product.colors || [])" :key="c.id" class="attr-chip">{{ c.name }}</span>
      </div>
      <div v-if="(product.sizes || []).length" class="attr-group">
        <span class="attr-label">尺码：</span>
        <span v-for="s in (product.sizes || [])" :key="s.id" class="attr-chip">{{ s.name || s.code }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ChevronLeft, ChevronRight, Image as ImageIcon, X } from 'lucide-vue-next'
import type { CatalogProductVO, CatalogSkuVO } from '@/api/catalog'
import CachedImage from '@/components/CachedImage.vue'

const props = withDefaults(defineProps<{
  product: CatalogProductVO
  allImages: string[]
  fullscreenImages?: string[]
  thumbImages?: string[]
  showClose?: boolean
}>(), {
  fullscreenImages: () => [],
  thumbImages: () => [],
  showClose: true,
})

const emit = defineEmits<{
  openFullscreen: [product: CatalogProductVO, index: number]
  close: []
}>()

const carouselIndex = ref(0)
const swipeStartX = ref<number | null>(null)
const swipeStartY = ref<number | null>(null)
const swipeOffsetX = ref(0)
const isDragging = ref(false)
const suppressNextClick = ref(false)
let suppressClickTimer: number | null = null

const stripImages = computed(() => props.thumbImages.length > 0 ? props.thumbImages : props.allImages)

const carouselTrackStyle = computed(() => ({
  transform: `translate3d(calc(${-carouselIndex.value * 100}% + ${swipeOffsetX.value}px), 0, 0)`,
  transition: isDragging.value ? 'none' : 'transform 220ms cubic-bezier(0.22, 0.61, 0.36, 1)',
}))

function openFullscreenFromCarousel() {
  if (suppressNextClick.value) {
    suppressNextClick.value = false
    return
  }
  // allImages includes product+SKU images; fullscreenImages has only product images.
  // When clicking a SKU image (index >= fullscreenImages.length), open the product
  // gallery at index 0 instead of passing an out-of-bounds index.
  const idx = carouselIndex.value < props.fullscreenImages.length ? carouselIndex.value : 0
  emit('openFullscreen', props.product, idx)
}

function handleCarouselPointerDown(event: PointerEvent) {
  if (props.allImages.length <= 1) return
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  swipeStartX.value = event.clientX
  swipeStartY.value = event.clientY
  swipeOffsetX.value = 0
  isDragging.value = true
}

function handleCarouselPointerMove(event: PointerEvent) {
  if (swipeStartX.value === null || swipeStartY.value === null) return
  const dx = event.clientX - swipeStartX.value
  const dy = event.clientY - swipeStartY.value
  if (Math.abs(dx) <= Math.abs(dy)) return
  event.preventDefault()
  swipeOffsetX.value = dampEdgeSwipe(dx, carouselIndex.value, props.allImages.length)
}

function handleCarouselPointerUp(event: PointerEvent) {
  if (swipeStartX.value === null || swipeStartY.value === null) return
  const dx = event.clientX - swipeStartX.value
  const dy = event.clientY - swipeStartY.value
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
  resetCarouselSwipe()
  if (!isHorizontalSwipe(dx, dy)) return

  if (dx < 0 && carouselIndex.value < props.allImages.length - 1) {
    carouselIndex.value += 1
    suppressClickBriefly()
  } else if (dx > 0 && carouselIndex.value > 0) {
    carouselIndex.value -= 1
    suppressClickBriefly()
  }
}

function resetCarouselSwipe() {
  swipeStartX.value = null
  swipeStartY.value = null
  swipeOffsetX.value = 0
  isDragging.value = false
}

function isHorizontalSwipe(dx: number, dy: number) {
  return Math.abs(dx) >= 44 && Math.abs(dx) > Math.abs(dy) * 1.25
}

function dampEdgeSwipe(dx: number, index: number, count: number) {
  const atStart = index === 0 && dx > 0
  const atEnd = index === count - 1 && dx < 0
  return atStart || atEnd ? dx * 0.28 : dx
}

function suppressClickBriefly() {
  suppressNextClick.value = true
  if (suppressClickTimer !== null) {
    window.clearTimeout(suppressClickTimer)
  }
  suppressClickTimer = window.setTimeout(() => {
    suppressNextClick.value = false
    suppressClickTimer = null
  }, 0)
}

// Build SKU matrix: color (rows) × size (columns)
const skuMatrix = computed(() => {
  const skus = props.product.skus || []
  if (!skus.length) {
    return { colors: [] as string[], colorNames: [] as string[], sizes: [] as string[], sizeNames: [] as string[], cells: {} as Record<string, CatalogSkuVO> }
  }

  const colorSet = new Map<string, string>()
  const sizeSet = new Map<string, string>()
  const cells: Record<string, CatalogSkuVO> = {}

  for (const sku of skus) {
    const ck = String(sku.colorId)
    const sk = String(sku.sizeId)
    if (!colorSet.has(ck)) colorSet.set(ck, sku.colorName)
    if (!sizeSet.has(sk)) sizeSet.set(sk, sku.sizeCode)
    cells[`${ck}:${sk}`] = sku
  }

  return {
    colors: Array.from(colorSet.keys()),
    colorNames: Array.from(colorSet.values()),
    sizes: Array.from(sizeSet.keys()),
    sizeNames: Array.from(sizeSet.values()),
    cells,
  }
})

function getSku(colorId: string, sizeId: string): CatalogSkuVO | null {
  return skuMatrix.value.cells[`${colorId}:${sizeId}`] || null
}

function skuCellClass(colorId: string, sizeId: string): Record<string, boolean> {
  const sku = getSku(colorId, sizeId)
  return {
    'sku-cell': true,
    'sku-in-stock': sku ? sku.hasStock : false,
    'sku-out': sku ? !sku.hasStock : false,
    'sku-na': !sku,
  }
}

function skuCellText(colorId: string, sizeId: string): string {
  const sku = getSku(colorId, sizeId)
  if (!sku) return '—'
  return sku.stockStatus
}
</script>

<style scoped>
/* ================================================================
   Detail Content — used in landscape panel & portrait drawer
   Inherits CSS custom properties from parent .catalog-page
   ================================================================ */
.detail-content {
  position: relative;
  padding: 14px;
}

.detail-close {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 5;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.78);
  color: var(--text-secondary, #6B6560);
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.detail-close:hover {
  background: #eee;
  color: var(--text-primary, #2D2D2D);
}

/* Carousel */
.detail-carousel {
  position: relative;
  margin-bottom: 14px;
}

.carousel-main {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  background: var(--bg-secondary, #F5F0EB);
  cursor: zoom-in;
  overflow: hidden;
  border-radius: 9px;
  border: 1px solid var(--border-light, #E5DFD8);
  touch-action: pan-y pinch-zoom;
  user-select: none;
  -webkit-user-select: none;
}

.carousel-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  -webkit-user-drag: none;
}

.carousel-track {
  position: absolute;
  inset: 0;
  display: flex;
  width: 100%;
  height: 100%;
  will-change: transform;
}

.carousel-slide {
  position: relative;
  width: 100%;
  height: 100%;
  flex: 0 0 100%;
  overflow: hidden;
}

.carousel-placeholder {
  width: 100%;
  aspect-ratio: 1 / 1;
  background: var(--bg-secondary, #F5F0EB);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #9E9892);
  font-size: 14px;
}
.carousel-placeholder p {
  margin: 4px 0 0;
}
.placeholder-icon {
  color: var(--text-muted, #9E9892);
  opacity: 0.55;
}

.carousel-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: rgba(255,255,255,0.88);
  color: var(--text-primary, #2D2D2D);
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  z-index: 2;
  box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}
.carousel-prev { left: 10px; }
.carousel-next { right: 10px; }
.carousel-arrow:hover {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.carousel-dots {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 6px;
  z-index: 2;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255,255,255,0.5);
  cursor: pointer;
  transition: all 0.2s;
}
.dot.active {
  background: var(--accent-gold, #9B6B22);
  transform: scale(1.3);
}

.carousel-thumbs {
  display: flex;
  gap: 7px;
  padding: 9px 2px 0;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.carousel-thumb {
  width: 46px;
  height: 46px;
  border-radius: 6px;
  border: 2px solid transparent;
  overflow: hidden;
  cursor: pointer;
  padding: 0;
  background: none;
  flex-shrink: 0;
  transition: border-color 0.2s;
}
.carousel-thumb.active {
  border-color: var(--accent-gold, #9B6B22);
}
.carousel-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Info */
.detail-info {
  padding: 0 0 14px;
  border-bottom: none;
}

.detail-code {
  font-size: 24px;
  color: var(--text-primary, #2D2D2D);
  font-weight: 780;
  margin-bottom: 2px;
}

.detail-name {
  font-size: 17px;
  font-weight: 580;
  color: var(--text-primary, #2D2D2D);
  margin: 0 0 12px;
  line-height: 1.3;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.detail-tag {
  padding: 4px 10px;
  border-radius: 7px;
  font-size: 12px;
  background: var(--bg-secondary, #F5F0EB);
  color: var(--accent-gold, #9B6B22);
  border: 1px solid var(--border-light, #E5DFD8);
}

.detail-category {
  font-size: 13px;
  color: var(--text-secondary, #6B6560);
}

/* SKU Matrix */
.sku-matrix {
  padding: 0 0 14px;
}

.sku-matrix-title {
  font-size: 13px;
  font-weight: 680;
  color: var(--text-primary, #2D2D2D);
  margin: 0 0 12px;
}

.sku-table-wrap {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.sku-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 12px;
}

.sku-table th,
.sku-table td {
  padding: 7px 8px;
  text-align: center;
  border: 1px solid var(--border-light, #E5DFD8);
}

.sku-corner {
  background: var(--bg-secondary, #F5F0EB);
  min-width: 60px;
}

.sku-col-header {
  background: var(--bg-secondary, #F5F0EB);
  font-weight: 600;
  color: var(--text-secondary, #6B6560);
  font-size: 12px;
  min-width: 56px;
}

.sku-row-header {
  background: var(--bg-secondary, #F5F0EB);
  font-weight: 500;
  color: var(--text-secondary, #6B6560);
  font-size: 12px;
  white-space: nowrap;
}

.sku-cell {
  font-weight: 500;
  font-size: 12px;
  white-space: nowrap;
}

.sku-in-stock {
  color: var(--accent-gold, #9B6B22);
  background: rgba(155, 107, 34, 0.06);
}

.sku-out {
  color: var(--badge-out, #9B8B8B);
  background: rgba(155,139,139,0.06);
}

.sku-na {
  color: var(--text-muted, #9E9892);
}

/* Attribute chips (non-matrix fallback) */
.detail-attrs {
  padding: 0 20px 16px;
}

.attr-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.attr-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary, #6B6560);
}

.attr-chip {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  background: var(--bg-secondary, #F5F0EB);
  color: var(--text-secondary, #6B6560);
  border: 1px solid var(--border-light, #E5DFD8);
}

@media (max-width: 900px) {
  .detail-content {
    padding: 8px 14px 18px;
    display: grid;
    grid-template-columns: minmax(120px, 38%) 1fr;
    column-gap: 12px;
    align-items: start;
  }

  .detail-carousel {
    grid-row: span 3;
    margin-bottom: 0;
  }

  .carousel-main {
    aspect-ratio: 3 / 4;
    border-radius: 8px;
  }

  .carousel-placeholder {
    aspect-ratio: 3 / 4;
    border-radius: 8px;
  }

  .carousel-thumbs {
    padding-top: 7px;
  }

  .carousel-thumb {
    width: 38px;
    height: 38px;
  }

  .carousel-dots {
    bottom: 8px;
  }

  .detail-info {
    padding: 0 0 8px;
  }

  .detail-code {
    font-size: 19px;
  }

  .detail-name {
    font-size: 14px;
    margin-bottom: 8px;
  }

  .detail-tags {
    gap: 5px;
    margin-bottom: 8px;
  }

  .detail-tag {
    padding: 3px 8px;
    font-size: 11px;
  }

  .detail-category {
    display: none;
  }

  .sku-matrix {
    padding: 0;
  }

  .sku-matrix-title {
    font-size: 12px;
    margin-bottom: 8px;
  }

  .sku-table th,
  .sku-table td {
    padding: 5px 6px;
    font-size: 10px;
  }

  .sku-col-header {
    min-width: 34px;
  }

  .sku-corner {
    min-width: 42px;
  }

  .detail-attrs {
    grid-column: 2;
    padding: 0;
  }
}

@media (max-width: 480px) {
  .detail-content {
    padding: 7px 12px 16px;
    grid-template-columns: minmax(116px, 40%) minmax(0, 1fr);
    column-gap: 10px;
  }

  .carousel-main,
  .carousel-placeholder {
    border-radius: 8px;
  }

  .carousel-arrow {
    display: none;
  }

  .carousel-dots {
    bottom: 7px;
    gap: 5px;
  }

  .dot {
    width: 6px;
    height: 6px;
  }

  .carousel-thumbs {
    gap: 5px;
    padding-top: 6px;
    scrollbar-width: none;
  }

  .carousel-thumbs::-webkit-scrollbar {
    display: none;
  }

  .carousel-thumb {
    width: 34px;
    height: 34px;
    border-radius: 6px;
  }

  .detail-code {
    font-size: 18px;
    line-height: 1.15;
  }

  .detail-name {
    font-size: 13px;
    line-height: 1.28;
    margin-bottom: 7px;
  }

  .detail-tags {
    gap: 4px;
    margin-bottom: 7px;
  }

  .detail-tag {
    padding: 3px 7px;
    border-radius: 6px;
    font-size: 10px;
  }

  .sku-matrix-title {
    font-size: 11px;
    margin-bottom: 6px;
  }

  .sku-table-wrap {
    max-width: 100%;
    border-radius: 7px;
  }

  .sku-table {
    font-size: 10px;
  }

  .sku-table th,
  .sku-table td {
    padding: 5px 6px;
    font-size: 10px;
  }

  .sku-corner {
    min-width: 38px;
  }

  .sku-col-header {
    min-width: 32px;
  }

  .sku-row-header {
    max-width: 54px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .attr-group {
    gap: 4px;
    margin-bottom: 6px;
  }

  .attr-label,
  .attr-chip {
    font-size: 11px;
  }
}
</style>
