<template>
  <div class="draft-entry-page space-y-6">
    <header class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <div class="flex items-center gap-3">
          <h2 class="text-2xl font-bold tracking-tight text-gray-900">订单草稿录入</h2>
          <el-tag type="warning" effect="light">Agent 草稿</el-tag>
        </div>
        <p class="mt-1 text-sm text-gray-500">按照快速录单方式修改，纸单原图需要时再打开对照。</p>
      </div>
      <div class="flex flex-wrap gap-3">
        <el-button class="!rounded-xl !font-bold" @click="router.push('/orders/quick')">
          <span class="material-symbols-outlined mr-1 text-sm">edit_note</span>
          手工快速录单
        </el-button>
        <el-button
          v-if="current?.sourceFileId"
          class="!rounded-xl !font-bold"
          @click="imageDrawerVisible = true"
        >
          <span class="material-symbols-outlined mr-1 text-sm">image</span>
          查看纸单原图
        </el-button>
        <el-button
          v-if="current?.confirmedOrderId"
          type="success"
          plain
          class="!rounded-xl !font-bold"
          @click="router.push(`/orders/${current.confirmedOrderId}`)"
        >
          查看正式订单
        </el-button>
        <template v-if="current?.status === 'EDITING'">
          <el-button class="!rounded-xl !font-bold" :loading="saving" @click="saveDraft">
            <span class="material-symbols-outlined mr-1 text-sm">save</span>
            存为草稿
          </el-button>
          <el-button
            type="primary"
            class="!border-none !bg-[#408aee] !rounded-xl !font-bold"
            :loading="confirming"
            @click="confirmDraft"
          >
            <span class="material-symbols-outlined mr-1 text-sm">task_alt</span>
            确认并生成订单
          </el-button>
        </template>
      </div>
    </header>

    <section class="draft-switcher">
      <div class="grid grid-cols-1 items-end gap-4 lg:grid-cols-[210px_minmax(320px,1fr)_minmax(240px,0.7fr)_auto]">
        <div class="field-block">
          <span>草稿状态</span>
          <el-segmented v-model="statusFilter" :options="statusOptions" class="!w-full" @change="loadDrafts" />
        </div>
        <label class="field-block">
          <span>选择需要修改的纸单</span>
          <el-select
            v-model="selectedId"
            filterable
            :loading="listLoading"
            placeholder="选择纸单号 / 客户"
            class="!w-full"
            @change="onDraftSelect"
          >
            <el-option
              v-for="draft in drafts"
              :key="draft.id"
              :value="draft.id"
              :label="`${draft.sourceOrderNo || draft.externalRefNo} · ${draft.customerName || '散客'}`"
            >
              <div class="flex items-center justify-between gap-6">
                <span class="font-bold text-gray-900">{{ draft.sourceOrderNo || draft.externalRefNo }} · {{ draft.customerName || '散客' }}</span>
                <span class="text-xs" :class="draft.unresolvedCount ? 'text-amber-600' : 'text-emerald-600'">
                  {{ draft.unresolvedCount ? `${draft.unresolvedCount} 行待匹配` : `${draft.itemCount} 行 · ${money(draft.paperTotalAmount)}` }}
                </span>
              </div>
            </el-option>
          </el-select>
        </label>
        <label class="field-block">
          <span>搜索草稿</span>
          <el-input v-model="keyword" clearable placeholder="纸单号或客户名称" @keyup.enter="loadDrafts">
            <template #prefix><span class="material-symbols-outlined text-base text-gray-400">search</span></template>
          </el-input>
        </label>
        <div class="flex gap-2">
          <el-button aria-label="上一张草稿" :disabled="currentDraftIndex <= 0" @click="moveDraft(-1)">
            <span class="material-symbols-outlined">chevron_left</span>
          </el-button>
          <el-button aria-label="下一张草稿" :disabled="currentDraftIndex < 0 || currentDraftIndex >= drafts.length - 1" @click="moveDraft(1)">
            <span class="material-symbols-outlined">chevron_right</span>
          </el-button>
          <el-button aria-label="刷新草稿" @click="loadDrafts">
            <span class="material-symbols-outlined">refresh</span>
          </el-button>
        </div>
      </div>
    </section>

    <main v-loading="detailLoading" class="min-w-0">
      <el-empty v-if="!current" class="form-panel py-24" description="暂无符合条件的订单草稿" />

      <div v-else class="space-y-6">
        <section v-if="current.warnings.length || unresolvedCount || totalMismatch" class="review-strip">
          <div class="flex flex-wrap items-center gap-2">
            <span class="material-symbols-outlined text-amber-600">warning</span>
            <strong class="text-sm text-gray-900">录单前请检查</strong>
            <el-tag v-if="unresolvedCount" type="warning">{{ unresolvedCount }} 行 SKU 待匹配</el-tag>
            <el-tag v-if="totalMismatch" type="danger">纸单总额与计算金额不一致</el-tag>
            <el-tag v-for="warning in current.warnings" :key="warning" type="warning" effect="plain">
              {{ warningLabel(warning) }}
            </el-tag>
          </div>
        </section>

        <div class="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">receipt_long</span>
              <h3>单据信息</h3>
              <el-tag class="ml-auto" :type="current.status === 'CONFIRMED' ? 'success' : 'warning'">
                {{ current.status === 'CONFIRMED' ? '已确认' : '编辑中' }}
              </el-tag>
            </div>
            <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
              <label class="field-block">
                <span>纸质单号</span>
                <el-input v-model="current.sourceOrderNo" :disabled="readonly" placeholder="纸质订单编号" />
                <small>外部编号：{{ current.externalRefNo }}</small>
              </label>
              <label class="field-block">
                <span>订单日期</span>
                <el-date-picker v-model="current.orderDate" value-format="YYYY-MM-DD" type="date" class="!w-full" :disabled="readonly" />
                <small>识别原文：{{ current.rawOrderDate || '空' }}</small>
              </label>
              <label class="field-block">
                <span>交货日期</span>
                <el-date-picker v-model="current.deliveryDate" value-format="YYYY-MM-DD" type="date" class="!w-full" :disabled="readonly" />
              </label>
              <label class="field-block">
                <span>纸单批次</span>
                <el-input v-model="current.sourceBatchNo" :disabled="readonly" placeholder="如 42" />
              </label>
            </div>
          </section>

          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">person_search</span>
              <h3>客户信息</h3>
            </div>
            <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
              <label class="field-block">
                <span>客户名称</span>
                <el-autocomplete
                  v-model="current.customerName"
                  :fetch-suggestions="queryCustomerSuggestions"
                  value-key="name"
                  placeholder="输入客户名称搜索，未匹配可保留散客"
                  clearable
                  class="!w-full"
                  :disabled="readonly"
                  @select="onCustomerSelect"
                  @input="onCustomerNameInput"
                >
                  <template #default="{ item }">
                    <div class="flex items-center justify-between gap-5 py-1">
                      <span class="font-medium text-gray-900">{{ item.name }}</span>
                      <span class="text-xs text-gray-400">{{ item.phones?.[0] || '暂无电话' }}</span>
                    </div>
                  </template>
                </el-autocomplete>
                <small>识别原文：{{ current.rawCustomerName || '空' }}</small>
              </label>
              <label class="field-block">
                <span>客户电话</span>
                <el-input v-model="current.customerPhone" :disabled="readonly" clearable placeholder="客户联系电话" />
                <small>识别原文：{{ current.rawCustomerPhone || '空' }}</small>
              </label>
              <label class="field-block">
                <span>定金</span>
                <el-input-number v-model="current.deposit" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                <small>识别原文：{{ current.rawDeposit || '空' }}</small>
              </label>
              <label class="field-block">
                <span>纸单总金额</span>
                <el-input-number v-model="current.paperTotalAmount" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                <small :class="totalMismatch ? '!text-orange-600' : ''">系统计算：{{ money(calculatedTotal) }}</small>
              </label>
            </div>
          </section>
        </div>

        <section class="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
          <div class="flex flex-wrap items-center justify-between gap-4 border-b border-gray-100 px-6 py-5">
            <div>
              <h3 class="text-lg font-bold text-gray-900">商品明细</h3>
              <p class="mt-1 text-xs text-gray-500">和快速录单一样直接修改 SKU、数量和售价；灰色文字是 Agent 识别原文。</p>
            </div>
            <el-button v-if="!readonly" type="primary" plain class="!rounded-xl !font-bold" @click="addItem">
              <span class="material-symbols-outlined mr-1 text-sm">add</span>
              添加一行
            </el-button>
          </div>

          <div class="overflow-x-auto draft-table-wrap">
            <el-table :data="current.items" row-key="id" class="draft-table">
              <el-table-column label="#" width="48" align="center">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column label="款号 / SKU" min-width="330">
                <template #default="{ row }">
                  <el-select
                    v-model="row.skuId"
                    filterable
                    remote
                    reserve-keyword
                    clearable
                    :disabled="readonly"
                    placeholder="搜索款号、商品名、颜色或 SKU"
                    class="!w-full"
                    :remote-method="filterSku"
                    @change="onSkuSelect(row)"
                  >
                    <template v-if="row.skuId" #label><span>{{ lineSkuLabel(row) }}</span></template>
                    <el-option v-for="sku in filteredSkuOptions" :key="sku.skuId" :label="sku.label" :value="sku.skuId">
                      <div class="flex items-center justify-between gap-5">
                        <span class="font-medium text-gray-900">{{ sku.label }}</span>
                        <span class="text-xs text-gray-400">参考价 {{ money(sku.price) }}</span>
                      </div>
                    </el-option>
                  </el-select>
                  <div class="recognition-note">
                    <span class="material-symbols-outlined">document_scanner</span>
                    <span>{{ recognitionText(row) }}</span>
                  </div>
                  <div class="mt-1 flex flex-wrap gap-2">
                    <span class="match-state" :class="row.skuId ? 'matched' : 'unmatched'">{{ row.skuId ? '已匹配' : '待匹配' }}</span>
                    <span v-if="hasPriceDifference(row)" class="difference-state">纸单价不同于系统参考价</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="105">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="1" :controls="false" :disabled="readonly" class="!w-full" />
                  <p class="cell-hint">识别 {{ row.rawQuantity || '-' }}</p>
                </template>
              </el-table-column>
              <el-table-column label="销售单价" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.salePrice" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                  <p class="cell-hint">系统参考 {{ money(row.systemReferencePrice) }}</p>
                </template>
              </el-table-column>
              <el-table-column label="纸单金额" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.paperAmount" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                  <p class="cell-hint">识别 {{ row.rawAmount || '-' }}</p>
                </template>
              </el-table-column>
              <el-table-column label="计算金额" width="110" align="right">
                <template #default="{ row }">
                  <p class="pt-2 text-base font-bold" :class="hasAmountDifference(row) ? 'text-orange-600' : 'text-gray-900'">
                    {{ money(lineAmount(row)) }}
                  </p>
                  <p v-if="hasAmountDifference(row)" class="cell-hint !text-orange-600">与纸单不同</p>
                </template>
              </el-table-column>
              <el-table-column v-if="!readonly" label="操作" width="108" align="center">
                <template #default="{ row, $index }">
                  <div class="flex items-center justify-center gap-1">
                    <el-tooltip content="拆成新行">
                      <el-button circle aria-label="拆分该行" @click="splitItem(row)">
                        <span class="material-symbols-outlined text-lg">call_split</span>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="删除该行">
                      <el-button circle type="danger" plain aria-label="删除该行" @click="removeItem($index)">
                        <span class="material-symbols-outlined text-lg">delete</span>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <div class="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">notes</span>
              <h3>备注</h3>
            </div>
            <el-input v-model="current.note" type="textarea" :rows="4" :disabled="readonly" placeholder="补充订单说明、识别疑问或人工核对结果" />
          </section>

          <section class="summary-panel">
            <h3 class="mb-5 text-lg font-bold">金额汇总</h3>
            <div class="space-y-3 text-sm">
              <div class="summary-row"><span>纸单总额</span><strong>{{ money(current.paperTotalAmount) }}</strong></div>
              <div class="summary-row"><span>明细计算</span><strong :class="totalMismatch ? 'text-orange-300' : 'text-emerald-400'">{{ money(calculatedTotal) }}</strong></div>
              <div class="summary-row"><span>已收定金</span><strong class="text-blue-300">{{ money(current.deposit) }}</strong></div>
              <div class="summary-divider"></div>
              <div class="summary-row"><span>待收余额</span><strong class="text-xl text-white">{{ money(balanceAmount) }}</strong></div>
            </div>
          </section>
        </div>
      </div>
    </main>

    <el-drawer v-model="imageDrawerVisible" title="纸单原图" size="min(760px, 92vw)" destroy-on-close>
      <div v-if="current" class="paper-drawer">
        <div class="mb-3 flex items-center justify-between text-sm text-gray-500">
          <span>纸单 {{ current.sourceOrderNo || current.externalRefNo }}</span>
          <span>批次 {{ current.sourceBatchNo || '-' }}</span>
        </div>
        <el-image
          v-if="current.sourceFileId"
          :src="filePreviewUrl(current.sourceFileId)"
          :preview-src-list="[filePreviewUrl(current.sourceFileId)]"
          fit="contain"
          class="h-[calc(100vh-150px)] w-full rounded-xl bg-slate-950"
          preview-teleported
        />
        <el-empty v-else description="Agent 未上传原图" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { filePreviewUrl } from '@/api/file'
import { getCustomerPage, type CustomerVO } from '@/api/customer'
import { getProductPage, type ProductVO } from '@/api/product'
import { hasFriendlySkuName, skuFriendlyName } from '@/utils/skuDisplay'
import {
  confirmOrderDraft,
  getOrderDraft,
  getOrderDraftPage,
  saveOrderDraft,
  type DraftSaveRequest,
  type OrderDraftItem,
  type OrderDraftSummary,
  type OrderDraftView,
} from '@/api/orderDraft'

interface SkuOption {
  skuId: number
  productId: number
  productCode: string
  skuCode: string
  skuType: 'NORMAL' | 'DEFAULT' | 'PLACEHOLDER'
  placeholder: boolean
  productName: string
  colorName: string
  sizeName: string
  label: string
  price: number
}

const router = useRouter()
const drafts = ref<OrderDraftSummary[]>([])
const current = ref<OrderDraftView | null>(null)
const selectedId = ref<number>()
const keyword = ref('')
const statusFilter = ref<'EDITING' | 'CONFIRMED'>('EDITING')
const listLoading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const confirming = ref(false)
const imageDrawerVisible = ref(false)
const skuOptions = ref<SkuOption[]>([])
const filteredSkuOptions = ref<SkuOption[]>([])
const statusOptions = [
  { label: '待处理', value: 'EDITING' },
  { label: '已确认', value: 'CONFIRMED' },
]

const readonly = computed(() => current.value?.status !== 'EDITING')
const currentDraftIndex = computed(() => drafts.value.findIndex(draft => draft.id === selectedId.value))
const unresolvedCount = computed(() => current.value?.items.filter(item => !item.skuId).length || 0)
const calculatedTotal = computed(() =>
  (current.value?.items || []).reduce((sum, item) => sum + lineAmount(item), 0)
)
const totalMismatch = computed(() =>
  current.value?.paperTotalAmount != null
  && Math.abs(calculatedTotal.value - Number(current.value.paperTotalAmount)) > 0.01
)
const balanceAmount = computed(() => Math.max(
  Number(current.value?.paperTotalAmount || 0) - Number(current.value?.deposit || 0),
  0,
))

async function loadDrafts() {
  listLoading.value = true
  try {
    const response = await getOrderDraftPage({
      current: 1,
      size: 100,
      status: statusFilter.value,
      keyword: keyword.value || undefined,
    })
    drafts.value = response.data.records
    if (!drafts.value.length) {
      selectedId.value = undefined
      current.value = null
      return
    }
    if (!selectedId.value || !drafts.value.some(draft => draft.id === selectedId.value)) {
      await selectDraft(drafts.value[0].id)
    }
  } finally {
    listLoading.value = false
  }
}

function onDraftSelect(id: number) {
  if (id) selectDraft(id)
}

function moveDraft(offset: number) {
  const target = drafts.value[currentDraftIndex.value + offset]
  if (target) selectDraft(target.id)
}

async function selectDraft(id: number) {
  selectedId.value = id
  detailLoading.value = true
  try {
    const response = await getOrderDraft(id)
    current.value = response.data
  } finally {
    detailLoading.value = false
  }
}

async function loadProducts() {
  const response = await getProductPage({ current: 1, size: 1000, status: 1 })
  const products: ProductVO[] = response.data?.records || response.data?.data?.records || []
  skuOptions.value = products.flatMap(product =>
    (product.skus || [])
      .filter(sku => sku.status === 1)
      .map(sku => ({
        skuId: sku.id,
        productId: product.id,
        productCode: product.productCode,
        skuCode: sku.skuCode,
        productName: product.name,
        skuType: sku.skuType || 'NORMAL',
        placeholder: sku.placeholder || sku.skuType === 'PLACEHOLDER',
        colorName: sku.colorName || '',
        sizeName: sku.sizeName || '',
        label: `${product.productCode} / ${product.name} · ${hasFriendlySkuName(sku)
          ? skuFriendlyName(sku)
          : [sku.colorName, sku.sizeName].filter(Boolean).join(' · ') || sku.skuCode}`,
        price: Number(sku.price || product.wholesalePrice || 0),
      }))
  ).sort((a, b) => Number(b.placeholder) - Number(a.placeholder))
  filteredSkuOptions.value = skuOptions.value.slice(0, 50)
}

function filterSku(keyword: string) {
  const normalized = keyword.trim().toLowerCase()
  if (!normalized) {
    filteredSkuOptions.value = skuOptions.value.slice(0, 50)
    return
  }
  filteredSkuOptions.value = skuOptions.value
    .filter(sku => [sku.productCode, sku.productName, sku.skuCode, sku.colorName, sku.sizeName]
      .some(value => value.toLowerCase().includes(normalized)))
    .slice(0, 80)
}

function lineSkuLabel(row: OrderDraftItem) {
  return skuOptions.value.find(option => option.skuId === row.skuId)?.label || `SKU ${row.skuId}`
}

function recognitionText(row: OrderDraftItem) {
  const product = row.rawProductCode || '未识别货号'
  const detail = row.rawDescription || row.rawColor || '无品名/颜色原文'
  return `识别：${product} · ${detail}`
}

function onSkuSelect(row: OrderDraftItem) {
  const sku = skuOptions.value.find(option => option.skuId === row.skuId)
  if (!sku) {
    row.productId = undefined
    row.matchStatus = 'UNMATCHED'
    return
  }
  row.productId = sku.productId
  row.systemReferencePrice = sku.price
  row.matchStatus = 'MATCHED'
}

async function queryCustomerSuggestions(query: string, callback: (items: CustomerVO[]) => void) {
  const keyword = query.trim()
  if (!keyword) {
    callback([])
    return
  }
  try {
    const response = await getCustomerPage({ current: 1, size: 10, keyword })
    callback(response.data.records || [])
  } catch {
    callback([])
  }
}

function onCustomerSelect(customer: CustomerVO) {
  if (!current.value) return
  current.value.customerId = customer.id
  current.value.customerName = customer.name
  current.value.customerPhone = customer.phones?.[0] || current.value.customerPhone
}

function onCustomerNameInput() {
  if (current.value) current.value.customerId = undefined
}

function addItem() {
  if (!current.value) return
  const nextRow = Math.max(0, ...current.value.items.map(item => Number(item.sourceRowNo || 0))) + 1
  current.value.items.push({
    sourceRowNo: nextRow,
    matchStatus: 'UNMATCHED',
    matchCandidates: [],
    warnings: [],
  })
}

function splitItem(row: OrderDraftItem) {
  if (!current.value) return
  current.value.items.push({
    ...row,
    id: undefined,
    skuId: undefined,
    productId: undefined,
    matchStatus: 'UNMATCHED',
    quantity: undefined,
    paperAmount: undefined,
  })
}

function removeItem(index: number) {
  current.value?.items.splice(index, 1)
}

function toSaveRequest(draft: OrderDraftView): DraftSaveRequest {
  return {
    externalRefNo: draft.externalRefNo,
    sourceBatchNo: draft.sourceBatchNo,
    sourceOrderNo: draft.sourceOrderNo,
    sourceFileId: draft.sourceFileId,
    rawCustomerName: draft.rawCustomerName,
    rawCustomerPhone: draft.rawCustomerPhone,
    customerId: draft.customerId,
    customerName: draft.customerName || '散客',
    customerPhone: draft.customerPhone,
    rawOrderDate: draft.rawOrderDate,
    orderDate: draft.orderDate,
    deliveryDate: draft.deliveryDate,
    rawDeposit: draft.rawDeposit,
    deposit: draft.deposit,
    paperTotalAmount: draft.paperTotalAmount,
    note: draft.note,
    warnings: draft.warnings,
    items: draft.items,
  }
}

async function saveDraft(showMessage = true) {
  if (!current.value) return
  if (!current.value.items.length) {
    ElMessage.warning('请至少保留一行商品明细')
    return
  }
  saving.value = true
  try {
    await saveOrderDraft(current.value.id, toSaveRequest(current.value))
    await selectDraft(current.value.id)
    await loadDrafts()
    if (showMessage) ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function confirmDraft() {
  if (!current.value) return
  const unresolved = current.value.items.filter(item => !item.skuId)
  if (unresolved.length) {
    ElMessage.warning(`还有 ${unresolved.length} 行没有选择 SKU`)
    return
  }
  const invalid = current.value.items.filter(item => !item.quantity || !item.salePrice)
  if (invalid.length) {
    ElMessage.warning('每行都需要填写数量和纸单销售价')
    return
  }
  await saveDraft(false)
  if (!current.value) return

  const warningText = totalMismatch.value || current.value.warnings.length
    ? '当前仍有金额或识别警告。确认后将使用纸单销售价和人工确认的总金额生成正式订单。'
    : '确认后将生成正式订单，草稿不能继续修改。'
  await ElMessageBox.confirm(warningText, '确认订单', {
    type: totalMismatch.value ? 'warning' : 'info',
    confirmButtonText: '确认生成正式订单',
    cancelButtonText: '继续检查',
  })

  confirming.value = true
  try {
    const response = await confirmOrderDraft(current.value.id, true)
    ElMessage.success('正式订单已生成')
    await selectDraft(current.value.id)
    await loadDrafts()
    router.push(`/orders/${response.data.orderId}`)
  } finally {
    confirming.value = false
  }
}

function lineAmount(item: OrderDraftItem) {
  return Number(item.quantity || 0) * Number(item.salePrice || 0)
}

function hasPriceDifference(item: OrderDraftItem) {
  return item.salePrice != null
    && item.systemReferencePrice != null
    && Math.abs(Number(item.salePrice) - Number(item.systemReferencePrice)) > 0.01
}

function hasAmountDifference(item: OrderDraftItem) {
  return item.paperAmount != null
    && Math.abs(lineAmount(item) - Number(item.paperAmount)) > 0.01
}

function money(value?: number) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function warningLabel(value: string) {
  if (value === 'SOURCE_IMAGE_MISSING') return '缺少纸单原图'
  if (value === 'ORDER_DATE_UNPARSED') return '日期待确认'
  if (value === 'DEPOSIT_UNPARSED') return '定金待确认'
  if (value === 'ORDER_TOTAL_MISMATCH') return '总金额不一致'
  if (value.includes('SKU_UNMATCHED')) return 'SKU待匹配'
  if (value.includes('QUANTITY_MISSING')) return '数量待补'
  if (value.includes('SALE_PRICE_MISSING')) return '销售价待补'
  if (value.includes('AMOUNT_MISMATCH')) return '明细金额不一致'
  return value
}

onMounted(async () => {
  await Promise.all([loadProducts(), loadDrafts()])
})
</script>

<style scoped>
.draft-entry-page {
  min-width: 1040px;
}

.draft-switcher,
.form-panel {
  border: 1px solid #eef0f3;
  border-radius: 12px;
  background: #fff;
  padding: 24px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.draft-switcher {
  padding-top: 18px;
  padding-bottom: 18px;
}

.review-strip {
  border: 1px solid #fde68a;
  border-radius: 12px;
  background: #fffbeb;
  padding: 14px 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding-left: 12px;
  border-left: 4px solid #408aee;
}

.panel-title h3 {
  color: #111827;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.2;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-block > span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.field-block > small {
  color: #94a3b8;
  font-size: 11px;
}

.draft-entry-page :deep(.el-input__wrapper),
.draft-entry-page :deep(.el-select__wrapper),
.draft-entry-page :deep(.el-input-number .el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
}

.draft-entry-page :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.draft-table-wrap {
  min-height: 360px;
}

.draft-table :deep(.el-table__cell) {
  padding-top: 14px;
  padding-bottom: 14px;
  vertical-align: top;
}

.recognition-note {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 7px;
  color: #6b7280;
  font-size: 11px;
  line-height: 1.4;
}

.recognition-note .material-symbols-outlined {
  color: #9ca3af;
  font-size: 15px;
}

.match-state,
.difference-state {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 800;
}

.match-state.matched {
  color: #047857;
  background: #ecfdf5;
}

.match-state.unmatched {
  color: #b45309;
  background: #fffbeb;
}

.difference-state {
  color: #c2410c;
  background: #fff7ed;
}

.cell-hint {
  margin-top: 6px;
  color: #9ca3af;
  font-size: 10px;
  white-space: nowrap;
}

.summary-panel {
  min-height: 100%;
  border-radius: 12px;
  padding: 24px;
  color: #fff;
  background: #1a1c1e;
  box-shadow: 0 14px 32px rgb(15 23 42 / 16%);
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.summary-row span {
  color: #94a3b8;
}

.summary-row strong {
  font-weight: 800;
}

.summary-divider {
  margin: 14px 0;
  border-top: 1px solid #334155;
}

.paper-drawer {
  min-height: calc(100vh - 100px);
}

@media (prefers-reduced-motion: reduce) {
  .draft-entry-page * {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
