<template>
  <div ref="pageEl" class="catalog-page app" :class="{ 'panel-open': !!panel, 'wholesale-open': panel === 'cart' }">
    <header class="topbar">
      <a class="brand" href="/catalog" aria-label="返回所有款式" @click.prevent="browseAll"><span class="wordmark">JIAJIA</span><span class="brand-cn">嘉嘉服饰</span></a>
      <label class="search"><CatalogIcon name="search" :size="19" /><input v-model="filters.keyword" type="search" placeholder="搜索款号或商品名称" aria-label="搜索款号或商品名称" autocomplete="off" @input="debounceSearch"></label>
      <button class="cart-trigger" :aria-label="`打开批发清单，共${lines.length}款`" @click="openCart"><CatalogIcon name="bag" :size="22" /><span class="cart-label">批发清单</span><span class="bag-count">{{ lines.length }}</span></button>
    </header>
    <div class="workspace" :class="{ 'has-panel': !!panel }">
      <main ref="browseEl" class="browse grid-area" :inert="panel === 'cart' || (panel === 'detail' && windowWidth <= 900)" @scroll.passive="onBrowseScroll">
        <section class="browse-heading"><div><h1>现货选款</h1></div><button class="text-button new-session" @click="confirmNewSession"><CatalogIcon name="rotate" :size="14" />新一轮选款</button></section>
        <div class="browse-controls"><nav class="categories" aria-label="商品分类"><button class="category" :class="{ active: !filters.categoryId }" :aria-pressed="!filters.categoryId" @click="chooseCategory()">全部款式</button><button v-for="category in options.categories" :key="category.id!" class="category" :class="{ active: filters.categoryId === category.id }" :aria-pressed="filters.categoryId === category.id" @click="chooseCategory(category.id!)">{{ category.name }}</button></nav><div class="filter-actions"><button class="filter-button" :class="{ active: onlySelected }" :aria-pressed="onlySelected" :aria-label="`只看已选${lines.length}款`" @click="toggleSelected"><CatalogIcon name="heart" :size="19" /><span>只看已选</span></button><button class="icon-button filter-more" aria-label="更多筛选" @click="openFilters"><CatalogIcon name="sliders" :size="20" /></button></div></div>
        <div class="results-heading"><p>{{ onlySelected ? `已选 ${lines.length} 款 · 当前显示 ${displayProducts.length} 款` : `${total} 款商品` }}</p><label class="stock-switch"><input type="checkbox" :checked="filters.stockMode === 'in_stock'" @change="setStock(($event.target as HTMLInputElement).checked)"><span class="switch-track"></span>仅看现货</label></div>
        <p v-if="storageError" class="catalog-notice" role="status">清单暂未保存到本机，请保留当前页面。</p>
        <p v-if="error || filterError" class="catalog-notice" role="alert">{{ error || '筛选项加载失败' }} <button @click="retryLoad">重试</button></p>
        <div v-if="loading && !displayProducts.length" class="product-grid" aria-label="正在加载商品"><div v-for="n in 6" :key="n" class="product-photo skeleton-photo" /></div>
        <div v-else class="product-grid">
          <article v-for="product in displayProducts" :key="product.id" class="product-card" :data-product="product.id">
            <div class="product-photo card-image">
              <PhotoGallery v-model="imageIndexes[product.id]" :images="productImages(product)" :product-id="product.id" :code="product.productCode" :name="product.name" @open="openLightbox(product)" />
              <button class="heart-button" :class="{ selected: selectedIds.has(product.id) }" :aria-pressed="selectedIds.has(product.id)" :aria-label="selectedIds.has(product.id) ? `移除${product.name}` : `加入${product.name}到选款清单`" @click="toggleHeart(product)"><CatalogIcon name="heart" :size="19" /></button>
              <span class="product-stock" :class="{ out: !product.hasStock }">{{ stockLabel(product) }}</span>
            </div>
            <button class="product-info" :data-detail="product.id" :aria-label="`查看${product.name}详情`" @click="showDetail(product)"><span class="product-info-top"><span class="product-code">{{ product.productCode }}</span><span class="swatches"><i v-for="color in product.colors.slice(0, 5)" :key="color.id" class="swatch" :title="color.name" :style="{ '--swatch': colorValue(color.code) }" /></span></span><h2 class="product-name">{{ product.name }}</h2></button>
          </article>
        </div>
        <div v-if="!loading && !displayProducts.length && !error" class="empty-state"><CatalogIcon name="heart" :size="38" /><h2>{{ onlySelected && !lines.length ? '还没有选款' : '没有匹配的商品' }}</h2><p>{{ onlySelected && !lines.length ? '看到喜欢的款式，轻点爱心加入清单。' : '试试其他分类或搜索词，也可以查看全部款式。' }}</p><button class="secondary-button" @click="browseAll">查看全部款式</button></div>
        <div v-if="!onlySelected && hasMore" class="load-more-state"><button :disabled="loadingMore || loading" @click="fetchProducts(false)">{{ loadingMore ? '正在加载更多…' : error ? '重试加载更多' : '加载更多款式' }}</button></div>
      </main>
      <aside v-if="panel" ref="panelEl" class="side-panel" :class="{ 'wholesale-sheet': panel === 'cart' }" :aria-label="panel === 'cart' ? '批发选款单' : '款式详情'">
        <WholesaleSheet v-if="panel === 'cart'" :lines="lines" :lookup-busy="lookupBusy" :refreshing="refreshingSelection" :refresh-error="selectionError" @close="closePanel" @remove="removeLine" @clear="confirmClear" @lookup="lookupStyle" @refresh="refreshSelection" @handoff="startHandoff" />
        <template v-else-if="detailLine">
          <div class="panel-header detail-header"><div class="panel-title-group"><button class="icon-button" aria-label="返回商品列表" @click="closePanel"><CatalogIcon name="back" :size="20" /></button><h2>款式详情</h2></div><button class="icon-button" aria-label="查看批发清单" @click="openCart"><CatalogIcon name="bag" :size="21" /></button></div>
          <div class="panel-scroll">
            <div class="detail-image"><PhotoGallery v-model="imageIndexes[detailLine.product.id]" :images="productImages(detailLine.product)" :product-id="detailLine.product.id" :code="detailLine.product.productCode" :name="detailLine.product.name" context="detail" @open="openLightbox(detailLine.product)" /></div>
            <div class="detail-heading"><strong class="detail-style-code">{{ detailLine.product.productCode }}</strong><h2>{{ detailLine.product.name }}</h2><span class="detail-stock">{{ stockLabel(detailLine.product) }}</span></div>
            <section v-if="detailLine.product.hasStock && detailLine.product.availableQty !== undefined" class="detail-stock-summary" aria-label="可用现货">
              <div class="detail-stock-amount"><span>可选现货</span><strong>{{ detailLine.product.availableQty.toLocaleString('zh-CN') }}<small>件</small></strong></div>
              <div class="detail-stock-variants"><span v-for="sku in detailLine.product.skus.filter(s => (s.availableQty || 0) > 0)" :key="sku.id">{{ sku.colorName }} · {{ sku.sizeCode }} {{ sku.availableQty }} 件</span></div>
              <p>已扣除预留数量，库存会随业务变化，最终配货由店员确认。</p>
            </section>
            <div class="detail-wholesale">
              <label>颜色说明<input v-model="detailLine.colorNote" maxlength="100" class="color-note" list="detail-color-options" :aria-label="`${detailLine.product.productCode}颜色说明`" placeholder="混色 / 3色 / 具体颜色"></label><datalist id="detail-color-options"><option value="混色"/><option value="2色"/><option value="3色"/><option v-for="color in detailLine.product.colors" :key="color.id" :value="color.name" /></datalist>
              <div class="detail-order-fields"><label>总数量<NumericInput v-model="detailLine.qty" mode="quantity" :label="`${detailLine.product.productCode}总数量`" placeholder="总数量" :aria-invalid="!!detailLine.qty && quantity(detailLine.qty) === null" /></label><label>本次单价<NumericInput v-model="detailLine.unitPrice" mode="price" :label="`${detailLine.product.productCode}本次单价`" placeholder="待报价" :aria-invalid="!!detailLine.unitPrice && priceCents(detailLine.unitPrice) === null" @change="formatDetailPrice" /></label></div>
              <div class="detail-line-total"><span>金额 <small>数量 × 单价</small></span><strong>{{ money(lineAmount(detailLine)) }}</strong></div><p>可填写混色或颜色数量；不需要逐个颜色、尺码拆单。</p><p v-if="quantity(detailLine.qty) && detailLine.product.availableQty !== undefined && quantity(detailLine.qty)! > detailLine.product.availableQty" class="stock-order-note">本次数量高于当前现货，店员将确认配货与交期。</p>
            </div>
          </div>
          <div class="panel-footer"><button id="addDetail" class="primary-button" :disabled="detailLoading" @click="saveDetail">{{ selectedIds.has(detailLine.product.id) ? '保存到批发清单' : '加入批发清单' }}<CatalogIcon name="arrow" :size="20" /></button><p>本次单价可调整，报价由店员最终确认</p></div>
        </template>
      </aside>
    </div>
    <nav class="mobile-nav" aria-label="底部导航"><button class="mobile-nav-button" :class="{ active: panel !== 'cart' }" @click="closePanel"><CatalogIcon name="grid" :size="20" />逛逛款式</button><button class="mobile-nav-button" :class="{ active: panel === 'cart' }" @click="openCart"><span class="mobile-bag"><CatalogIcon name="bag" :size="20" /><b>{{ lines.length }}</b></span>批发清单</button></nav>
    <div class="toast" :class="{ show: !!toastMessage }" role="status" aria-live="polite"><span>{{ toastMessage }}</span><button v-if="undoAction" @click="undo">撤销</button></div>
    <dialog ref="filterDialog" class="modal filter-modal" aria-label="筛选款式"><form @submit.prevent="applyFilters"><div class="modal-top"><h2>筛选款式</h2><button class="icon-button" type="button" aria-label="关闭筛选" @click="filterDialog?.close()"><CatalogIcon name="close" :size="20" /></button></div><label class="field-label">商品分类<select v-model="filterDraft.categoryId" aria-label="筛选商品分类"><option :value="undefined">全部款式</option><option v-for="c in options.categories" :key="c.id!" :value="c.id">{{ c.name }}</option></select></label><label class="field-label">颜色<select v-model="filterDraft.colorId" aria-label="筛选颜色"><option :value="undefined">全部颜色</option><option v-for="c in options.colors" :key="c.id!" :value="c.id">{{ c.name }}</option></select></label><label class="field-label">尺码<select v-model="filterDraft.sizeId" aria-label="筛选尺码"><option :value="undefined">全部尺码</option><option v-for="s in options.sizes" :key="s.id!" :value="s.id">{{ s.name }}</option></select></label><label class="filter-check"><input v-model="filterDraft.inStock" type="checkbox">仅显示有现货的款式</label><label class="filter-check"><input v-model="filterDraft.hasImage" type="checkbox">仅显示有图片的款式</label><div class="modal-actions"><button class="secondary-button" type="button" @click="resetFilterDraft">重置</button><button class="primary-button">查看结果<CatalogIcon name="arrow" :size="18" /></button></div></form></dialog>
    <dialog ref="confirmDialog" class="modal confirm-modal" :aria-label="confirmTitle"><h2>{{ confirmTitle }}</h2><p>{{ confirmText }}</p><div class="modal-actions"><button class="secondary-button" @click="confirmDialog?.close()">先保留</button><button class="primary-button" @click="acceptConfirm">确认</button></div></dialog>
    <dialog ref="handoffDialog" class="modal handoff-modal" aria-label="确认批发清单" @cancel="sending && $event.preventDefault()">
      <template v-if="handoffReceipt"><div class="success-state"><div class="success-circle"><CatalogIcon name="check" :size="30" /></div><h2>批发清单已移交</h2><p>已进入订单草稿，等待店员核对客户、颜色、数量和本次报价后开单。</p><div class="handoff-code">{{ handoffReceipt.reference }}</div><router-link v-if="canReadDraft" class="primary-button" :to="`/orders/drafts/${handoffReceipt.draftId}`">店员打开草稿<CatalogIcon name="arrow" :size="18" /></router-link><button class="secondary-button" @click="handoffDialog?.close()">返回批发清单</button></div></template>
      <template v-else><div class="modal-top"><h2>确认批发清单</h2><button class="icon-button" :disabled="sending" aria-label="关闭确认" @click="handoffDialog?.close()"><CatalogIcon name="close" :size="20" /></button></div><p class="handoff-intro">店员将按款号核对颜色说明、总数量和本次报价，确认后开单。</p><div class="handoff-wholesale"><div v-for="line in chosenLines" :key="line.product.id" class="handoff-wholesale-line"><div><strong>{{ line.product.productCode }}</strong><span>{{ line.colorNote || '颜色待确认' }} · {{ quantity(line.qty) ?? '待定' }} 件</span></div><div><strong>{{ money(lineAmount(line)) }}</strong><span>单价 {{ priceCents(line.unitPrice) === null ? '待报价' : money(priceCents(line.unitPrice)) }}</span></div></div></div><div v-if="totals.pending" class="handoff-pending">{{ totals.pending }} 款数量或报价待补充，未计入完整金额；可交店员补充确认。</div><div class="handoff-finance"><span>{{ totals.styles }} 款 · {{ totals.qty }} 件</span><strong>{{ totals.pending ? '已填金额' : '合计金额' }} {{ totals.pending === totals.styles ? '待报价' : money(totals.amount) }}</strong></div><label v-if="canHandoff" class="field-label">开单档口<select v-model="handoffOutletId" :disabled="sending || outletLoading || outletOptions.length === 1" aria-label="开单档口"><option :value="undefined">请选择档口</option><option v-for="outlet in outletOptions" :key="outlet.id" :value="outlet.id">{{ outlet.outletName }}</option></select></label><p v-if="!canHandoff" class="catalog-notice">请店员使用具有开单权限的账号确认，当前清单已保存在本机。</p><p v-if="handoffError" class="catalog-notice" role="alert">{{ handoffError }}</p><button v-if="canHandoff" class="primary-button" :disabled="sending || outletLoading || !handoffOutletId" @click="sendHandoff">{{ sending ? '正在移交…' : '交给店员确认' }}<CatalogIcon name="arrow" :size="20" /></button><p class="preview-only">仅生成订单草稿，不创建正式订单或扣减库存</p></template>
    </dialog>
    <CatalogNumberPad ref="numberPad" />
    <CatalogLightbox v-if="lightboxProduct" ref="lightbox" v-model="imageIndexes[lightboxProduct.id]" :images="productImages(lightboxProduct)" :product-id="lightboxProduct.id" :code="lightboxProduct.productCode" :name="lightboxProduct.name" :background="pageEl" />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, shallowRef, provide, nextTick, onMounted, onBeforeUnmount } from 'vue'
import CatalogIcon from './CatalogIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { getCatalogProducts, getCatalogProductById, handoffCatalogSelection, type CatalogProductVO, type CatalogPageParams, type CatalogHandoffResponse } from '@/api/catalog'
import { getOutletOptions, type OutletOptionVO } from '@/api/outlet'
import { useCatalogProducts } from './useCatalogProducts'
import { useCatalogSelection } from './useCatalogSelection'
import { productImages, snapshotProduct } from './catalogImages'
import { blankLine, money, quantity, priceCents, decimalPrice, lineAmount, selectionTotals, type SelectionLine } from './selectionModel'
import PhotoGallery from './PhotoGallery.vue'
import WholesaleSheet from './WholesaleSheet.vue'
import NumericInput from './NumericInput.vue'
import CatalogNumberPad from './CatalogNumberPad.vue'
import { numberEntryKey } from './numericEntry'
import { useCatalogViewport } from './useCatalogViewport'
import CatalogLightbox from './CatalogLightbox.vue'
import { stockLabel } from './stockDisplay'
const pageEl = ref<HTMLElement>(), numberPad = ref<InstanceType<typeof CatalogNumberPad>>()
provide(numberEntryKey, request => numberPad.value?.open(request))
useCatalogViewport(pageEl)
const auth = useAuthStore()
const { filters, options, products, total, loading, loadingMore, error, filterError, hasMore, search, fetchProducts, loadFilters, applyCache, destroy } = useCatalogProducts()
const selection = useCatalogSelection()
const { lines, selectedIds, storageError } = selection
const panel = ref<'detail' | 'cart' | null>(null), detailLine = ref<SelectionLine | null>(null), detailLoading = ref(false)
const panelEl = ref<HTMLElement>(), browseEl = ref<HTMLElement>(), windowWidth = ref(window.innerWidth)
const imageIndexes = reactive<Record<number, number>>({})
const onlySelected = ref(false), chosenLines = computed(() => lines.value.filter(line => line.checked)), totals = computed(() => selectionTotals(lines.value))
const filterDialog = ref<HTMLDialogElement>(), confirmDialog = ref<HTMLDialogElement>(), handoffDialog = ref<HTMLDialogElement>()
const lightboxProduct = ref<CatalogProductVO | null>(null), lightbox = ref<InstanceType<typeof CatalogLightbox>>()
const filterDraft = reactive({ categoryId: undefined as number | undefined, colorId: undefined as number | undefined, sizeId: undefined as number | undefined, inStock: false, hasImage: false })
const toastMessage = ref(''), undoAction = shallowRef<(() => void) | null>(null)
let toastTimer: ReturnType<typeof setTimeout>, searchTimer: ReturnType<typeof setTimeout> | undefined, detailRequest = 0, scrollBeforeDetail = 0
let priorFilters: CatalogPageParams | null = null, priorScroll = 0
const displayProducts = computed(() => onlySelected.value ? lines.value.map(line => line.product).filter(p =>
  (!filters.keyword || `${p.productCode} ${p.name}`.toLowerCase().includes(filters.keyword.toLowerCase())) &&
  (!filters.categoryId || p.categoryId === filters.categoryId) && (!filters.colorId || p.colors.some(c => c.id === filters.colorId)) &&
  (!filters.sizeId || p.sizes.some(s => s.id === filters.sizeId)) && (filters.stockMode !== 'in_stock' || p.hasStock) && (!filters.hasImage || p.hasImage)) : products.value)
function notify(message: string, action?: () => void) { clearTimeout(toastTimer); toastMessage.value = message; undoAction.value = action || null; toastTimer = setTimeout(() => { toastMessage.value = ''; undoAction.value = null }, 4000) }
function undo() { undoAction.value?.(); undoAction.value = null; toastMessage.value = '' }
function searchChanged() { clearTimeout(searchTimer); searchTimer = undefined; if (!onlySelected.value) search(); if (browseEl.value) browseEl.value.scrollTop = 0 }
function debounceSearch() { clearTimeout(searchTimer); searchTimer = setTimeout(searchChanged, 250) }
function chooseCategory(id?: number) { filters.categoryId = id; searchChanged() }
function setStock(inStock: boolean) { filters.stockMode = inStock ? 'in_stock' : 'all'; searchChanged() }
function emptyFilters() { Object.assign(filters, { keyword: '', stockMode: 'all', hasImage: undefined, categoryId: undefined, colorId: undefined, sizeId: undefined }) }
function browseAll() { closePanel(); onlySelected.value = false; priorFilters = null; emptyFilters(); searchChanged() }
function toggleSelected() {
  if (!onlySelected.value && searchTimer !== undefined) searchChanged()
  clearTimeout(searchTimer); searchTimer = undefined
  if (!onlySelected.value) { priorFilters = { ...filters }; priorScroll = browseEl.value?.scrollTop || 0; emptyFilters(); onlySelected.value = true; if (browseEl.value) browseEl.value.scrollTop = 0 }
  else { onlySelected.value = false; if (priorFilters) Object.assign(filters, priorFilters); nextTick(() => { if (browseEl.value) browseEl.value.scrollTop = priorScroll }) }
}
function openFilters() { Object.assign(filterDraft, { ...filters, inStock: filters.stockMode === 'in_stock', hasImage: !!filters.hasImage }); filterDialog.value?.showModal() }
function resetFilterDraft() { Object.assign(filterDraft, { categoryId: undefined, colorId: undefined, sizeId: undefined, inStock: false, hasImage: false }) }
function applyFilters() { Object.assign(filters, { categoryId: filterDraft.categoryId, colorId: filterDraft.colorId, sizeId: filterDraft.sizeId, stockMode: filterDraft.inStock ? 'in_stock' : 'all', hasImage: filterDraft.hasImage || undefined }); filterDialog.value?.close(); searchChanged() }
function retryLoad() { void loadFilters(); if (!onlySelected.value) void fetchProducts() }
function onBrowseScroll(event: Event) { const el = event.currentTarget as HTMLElement; if (!onlySelected.value && !error.value && el.scrollHeight - el.scrollTop - el.clientHeight < 600) void fetchProducts(false) }
function colorValue(code?: string) { const colors: Record<string, string> = { BLACK: '#333b36', WHITE: '#e8e5dc', BLUE: '#9eb8c7', RED: '#a9474d', GREEN: '#929b78', PINK: '#cba995', BROWN: '#a27858' }; return code && /^#[0-9a-f]{3,6}$/i.test(code) ? code : colors[code?.toUpperCase() || ''] || '#b2aa96' }
function removeLine(id: number) { const undo = selection.remove(id); notify('已从批发清单移除', undo) }
function toggleHeart(product: CatalogProductVO) { try { if (selectedIds.value.has(product.id)) removeLine(product.id); else { selection.add(product); notify('已加入批发清单') } } catch (e) { notify((e as Error).message) } }
async function showDetail(product: CatalogProductVO) {
  if (panel.value !== 'detail') scrollBeforeDetail = browseEl.value?.scrollTop || 0
  const request = ++detailRequest; panel.value = 'detail'; detailLoading.value = true
  const existing = lines.value.find(line => line.product.id === product.id)
  detailLine.value = existing ? { ...existing, product } : blankLine(product)
  try {
    const response = await getCatalogProductById(product.id)
    if (request === detailRequest && detailLine.value) detailLine.value.product = response.data
  } catch { if (request === detailRequest) notify('详情刷新失败，保留已加载的款式；移交时会重新核对') }
  finally { if (request === detailRequest) detailLoading.value = false }
}
function closePanel() { const wasDetail = panel.value === 'detail'; ++detailRequest; panel.value = null; detailLoading.value = false; if (wasDetail) nextTick(() => { if (browseEl.value) browseEl.value.scrollTop = scrollBeforeDetail }) }
function formatDetailPrice() { if (!detailLine.value) return; const price = priceCents(detailLine.value.unitPrice); if (price !== null) detailLine.value.unitPrice = decimalPrice(price) }
function invalidLine(line: SelectionLine) { return (!!line.qty && quantity(line.qty) === null) || (!!line.unitPrice && priceCents(line.unitPrice) === null) }
async function saveDetail() {
  const line = detailLine.value
  if (!line) return
  if (invalidLine(line)) { notify('请填写合法的整款数量和本次单价，也可以留空待店员确认'); return }
  try {
    const updated = selection.save(line), id = line.product.id
    closePanel(); await nextTick()
    if (browseEl.value) browseEl.value.scrollTop = scrollBeforeDetail
    browseEl.value?.querySelector<HTMLButtonElement>(`[data-detail="${id}"]`)?.focus({ preventScroll: true })
    notify(updated ? '已更新批发清单，可以继续选款' : '已加入批发清单，可以继续选款')
  } catch (e) { notify((e as Error).message) }
}
function openLightbox(product: CatalogProductVO) { if (!productImages(product).length) { void showDetail(product); return }; lightboxProduct.value = product; nextTick(() => lightbox.value?.open()) }
const refreshingSelection = ref(false), selectionError = ref(''), lookupBusy = ref(false)
async function refreshSelection(): Promise<boolean> {
  if (refreshingSelection.value) return false
  refreshingSelection.value = true; selectionError.value = ''
  try {
    const ids = lines.value.map(line => line.product.id), found = new Map<number, CatalogProductVO>()
    for (let start = 0; start < ids.length; start += 100) { const res = await getCatalogProducts({ productIds: ids.slice(start, start + 100).join(','), current: 1, size: 100 }); res.data.records.forEach(p => found.set(p.id, p)) }
    for (const line of lines.value) { const product = found.get(line.product.id); line.unavailable = !product; if (product) line.product = snapshotProduct(product) }
    return true
  } catch (e) { selectionError.value = (e as Error).message || '款式状态核对失败，请重试'; return false }
  finally { refreshingSelection.value = false }
}
function openCart() { ++detailRequest; panel.value = 'cart'; detailLoading.value = false; void refreshSelection() }
async function lookupStyle(code: string) {
  if (!code.trim() || lookupBusy.value) return
  lookupBusy.value = true
  try {
    const res = await getCatalogProducts({ productCode: code.trim(), current: 1, size: 1 })
    const product = res.data.records.find(p => p.productCode.toLowerCase() === code.trim().toLowerCase())
    if (!product) { notify('没有找到可选款号，请检查款号或返回浏览'); return }
    const existed = selectedIds.value.has(product.id); selection.add(product); await nextTick()
    const input = panelEl.value?.querySelector<HTMLInputElement>(`[data-item="${product.id}"] .td-qty input`)
    if (input?.readOnly) input.parentElement?.querySelector<HTMLButtonElement>('.number-pad-trigger')?.click()
    else input?.focus()
    notify(existed ? '该款已在清单，可直接修改数量和单价' : '款号已加入批发清单')
  } catch (e) { notify((e as Error).message) }
  finally { lookupBusy.value = false }
}
const confirmTitle = ref(''), confirmText = ref(''); let confirmAction: (() => void) | null = null
function askConfirm(title: string, text: string, action: () => void) { confirmTitle.value = title; confirmText.value = text; confirmAction = action; confirmDialog.value?.showModal() }
function acceptConfirm() { confirmDialog.value?.close(); confirmAction?.(); confirmAction = null }
function confirmClear() { askConfirm('清空批发清单？', '本次填写的颜色说明、数量和报价也会清空。', () => { selection.clear(); notify('已清空批发清单') }) }
function confirmNewSession() { askConfirm('开始新一轮选款？', '当前款号、颜色说明、数量和本次报价都会清空。', () => { selection.clear(); browseAll(); notify('新的选款已开始') }) }
const canHandoff = computed(() => auth.permissions.includes('btn:order:create'))
const canReadDraft = computed(() => auth.permissions.includes('menu:order') && auth.permissions.includes('btn:order:view'))
const outletOptions = ref<OutletOptionVO[]>([]), handoffOutletId = ref<number>(), outletLoading = ref(false), sending = ref(false), handoffError = ref('')
const handoffReceipt = ref<CatalogHandoffResponse | null>(null)
function handoffItems() { return chosenLines.value.map(line => ({ productId: line.product.id, colorNote: line.colorNote.trim(), quantity: quantity(line.qty), unitPrice: priceCents(line.unitPrice) === null ? null : decimalPrice(priceCents(line.unitPrice)!) })) }
async function startHandoff() {
  if (!chosenLines.value.length) return
  if (chosenLines.value.some(invalidLine)) { notify('请先修正标记的数量或单价格式'); return }
  if (totals.value.amount > 999999999999n) { notify('已填金额超出当前订单范围，请调整数量或拆分清单'); return }
  if (!(await refreshSelection())) return
  if (chosenLines.value.some(line => line.unavailable)) { notify('请先移除已下架或不可用的款式'); return }
  handoffError.value = ''; handoffReceipt.value = null; handoffDialog.value?.showModal()
  if (!canHandoff.value) return
  outletLoading.value = true
  try {
    const res = await getOutletOptions(); const data = res.data
    outletOptions.value = data.items || []
    handoffOutletId.value = data.defaultOutletId || (data.items.length === 1 ? data.items[0]!.id : undefined)
    if (!data.items.length) handoffError.value = '当前账号没有可用档口，请店员核对档口授权后重试'
  } catch (e) { handoffError.value = (e as Error).message || '档口加载失败，请关闭后重试' }
  finally { outletLoading.value = false }
}
async function sendHandoff() {
  if (sending.value || !handoffOutletId.value || !canHandoff.value) return
  const items = handoffItems(), signature = JSON.stringify({ sourceOutletId: handoffOutletId.value, items })
  if (selection.receipt.value?.signature === signature) { handoffReceipt.value = selection.receipt.value.data; return }
  sending.value = true; handoffError.value = ''
  try {
    const res = await handoffCatalogSelection({ requestId: selection.idFor(signature), sourceOutletId: handoffOutletId.value, items })
    selection.remember(signature, res.data); handoffReceipt.value = res.data
  } catch (e) { handoffError.value = (e as Error).message || '移交失败，清单已保留，请重试' }
  finally { sending.value = false }
}
function resize() { windowWidth.value = window.innerWidth }
onMounted(() => { applyCache(); void fetchProducts(); void loadFilters(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { destroy(); ++detailRequest; clearTimeout(toastTimer); clearTimeout(searchTimer); selection.persist(); window.removeEventListener('resize', resize) })
</script>
<style src="./catalog.css"></style>
