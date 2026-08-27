<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <div class="flex items-center gap-3">
          <h2 class="text-2xl font-black text-slate-900">订单草稿工作台</h2>
          <el-tag type="warning" effect="light">Agent 批量录入</el-tag>
        </div>
        <p class="mt-1 text-sm text-slate-500">核对纸单原图与识别结果，补齐 SKU 后再生成正式订单。</p>
      </div>
      <div class="flex gap-2">
        <el-button @click="loadDrafts">
          <span class="material-symbols-outlined mr-1 text-base">refresh</span>
          刷新
        </el-button>
        <el-button @click="router.push('/orders/quick')">手工快速录单</el-button>
      </div>
    </header>

    <div class="grid min-h-[720px] grid-cols-1 gap-5 xl:grid-cols-[340px_minmax(0,1fr)]">
      <aside class="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div class="border-b border-slate-100 p-4">
          <el-input v-model="keyword" clearable placeholder="搜索纸单号或客户" @keyup.enter="loadDrafts">
            <template #prefix>
              <span class="material-symbols-outlined text-base text-slate-400">search</span>
            </template>
          </el-input>
          <el-segmented v-model="statusFilter" :options="statusOptions" class="mt-3 !w-full" @change="loadDrafts" />
        </div>

        <div v-loading="listLoading" class="max-h-[680px] overflow-y-auto p-2">
          <button
            v-for="draft in drafts"
            :key="draft.id"
            type="button"
            class="mb-2 w-full rounded-xl border p-4 text-left transition"
            :class="selectedId === draft.id
              ? 'border-blue-400 bg-blue-50 shadow-sm'
              : 'border-transparent bg-slate-50 hover:border-slate-200 hover:bg-white'"
            @click="selectDraft(draft.id)"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="font-black text-slate-900">{{ draft.sourceOrderNo || draft.externalRefNo }}</p>
                <p class="mt-1 text-xs text-slate-500">{{ draft.customerName || '散客' }}</p>
              </div>
              <el-tag :type="draft.status === 'CONFIRMED' ? 'success' : 'warning'" size="small">
                {{ draft.status === 'CONFIRMED' ? '已确认' : '待处理' }}
              </el-tag>
            </div>
            <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
              <span>{{ draft.itemCount }} 行 · {{ money(draft.paperTotalAmount) }}</span>
              <span v-if="draft.unresolvedCount" class="font-bold text-amber-600">
                {{ draft.unresolvedCount }} 行待匹配
              </span>
              <span v-else-if="draft.warningCount" class="font-bold text-orange-600">
                {{ draft.warningCount }} 项警告
              </span>
              <span v-else class="font-bold text-emerald-600">可确认</span>
            </div>
          </button>
          <el-empty v-if="!listLoading && drafts.length === 0" description="暂无订单草稿" />
        </div>
      </aside>

      <main v-loading="detailLoading" class="min-w-0">
        <el-empty v-if="!current" class="rounded-2xl border border-slate-200 bg-white py-24" description="从左侧选择一张草稿" />

        <div v-else class="space-y-5">
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div>
                <div class="flex items-center gap-3">
                  <h3 class="text-xl font-black text-slate-900">{{ current.sourceOrderNo || current.externalRefNo }}</h3>
                  <el-tag :type="current.status === 'CONFIRMED' ? 'success' : 'warning'">
                    {{ current.status === 'CONFIRMED' ? '已生成正式订单' : '编辑中' }}
                  </el-tag>
                </div>
                <p class="mt-1 text-xs text-slate-400">幂等编号 {{ current.externalRefNo }}</p>
              </div>
              <div class="flex gap-2">
                <el-button
                  v-if="current.confirmedOrderId"
                  type="success"
                  plain
                  @click="router.push(`/orders/${current.confirmedOrderId}`)"
                >
                  查看正式订单
                </el-button>
                <template v-if="current.status === 'EDITING'">
                  <el-button :loading="saving" @click="saveDraft">存为草稿</el-button>
                  <el-button type="primary" :loading="confirming" @click="confirmDraft">人工确认并生成订单</el-button>
                </template>
              </div>
            </div>

            <el-alert
              v-if="current.warnings.length"
              class="mt-4"
              type="warning"
              :closable="false"
              show-icon
              :title="`当前有 ${current.warnings.length} 项识别警告`"
            >
              <div class="mt-1 flex flex-wrap gap-2">
                <el-tag v-for="warning in current.warnings" :key="warning" type="warning" size="small">
                  {{ warningLabel(warning) }}
                </el-tag>
              </div>
            </el-alert>
          </section>

          <div class="grid grid-cols-1 gap-5 2xl:grid-cols-[360px_minmax(0,1fr)]">
            <section class="rounded-2xl border border-slate-200 bg-slate-950 p-4 shadow-sm">
              <div class="mb-3 flex items-center justify-between text-white">
                <h3 class="font-bold">纸单原图</h3>
                <span class="text-xs text-slate-400">批次 {{ current.sourceBatchNo || '-' }}</span>
              </div>
              <div class="flex min-h-[520px] items-center justify-center overflow-hidden rounded-xl bg-slate-900">
                <el-image
                  v-if="current.sourceFileId"
                  :src="filePreviewUrl(current.sourceFileId)"
                  :preview-src-list="[filePreviewUrl(current.sourceFileId)]"
                  fit="contain"
                  class="max-h-[640px] w-full"
                  preview-teleported
                />
                <div v-else class="text-center text-slate-500">
                  <span class="material-symbols-outlined text-5xl">image_not_supported</span>
                  <p class="mt-2 text-sm">Agent 未上传原图</p>
                </div>
              </div>
            </section>

            <div class="space-y-5">
              <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <h3 class="mb-4 font-black text-slate-900">订单信息</h3>
                <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                  <label class="field">
                    <span>客户</span>
                    <el-input v-model="current.customerName" :disabled="readonly" />
                    <small>识别原文：{{ current.rawCustomerName || '空' }}</small>
                  </label>
                  <label class="field">
                    <span>客户电话</span>
                    <el-input v-model="current.customerPhone" :disabled="readonly" />
                    <small>识别原文：{{ current.rawCustomerPhone || '空' }}</small>
                  </label>
                  <label class="field">
                    <span>订单日期</span>
                    <el-date-picker v-model="current.orderDate" value-format="YYYY-MM-DD" type="date" class="!w-full" :disabled="readonly" />
                    <small>识别原文：{{ current.rawOrderDate || '空' }}</small>
                  </label>
                  <label class="field">
                    <span>交货日期</span>
                    <el-date-picker v-model="current.deliveryDate" value-format="YYYY-MM-DD" type="date" class="!w-full" :disabled="readonly" />
                  </label>
                  <label class="field">
                    <span>定金</span>
                    <el-input-number v-model="current.deposit" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                    <small>识别原文：{{ current.rawDeposit || '空' }}</small>
                  </label>
                  <label class="field">
                    <span>纸单总金额</span>
                    <el-input-number v-model="current.paperTotalAmount" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                  </label>
                  <label class="field md:col-span-2">
                    <span>备注</span>
                    <el-input v-model="current.note" :disabled="readonly" />
                  </label>
                </div>
              </section>

              <section class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4">
                  <div>
                    <h3 class="font-black text-slate-900">商品校对</h3>
                    <p class="mt-1 text-xs text-slate-500">销售单价以纸单为准，系统价只显示差异。</p>
                  </div>
                  <el-button v-if="!readonly" @click="addItem">添加明细</el-button>
                </div>

                <div class="overflow-x-auto">
                  <el-table :data="current.items" row-key="id" class="draft-table">
                    <el-table-column label="识别原文" min-width="180">
                      <template #default="{ row }">
                        <p class="font-bold text-slate-900">{{ row.rawProductCode || '未识别货号' }}</p>
                        <p class="mt-1 text-xs text-slate-500">{{ row.rawDescription || row.rawColor || '无品名/颜色原文' }}</p>
                        <p class="mt-1 text-[11px] text-slate-400">
                          数量 {{ row.rawQuantity || '-' }} · 单价 {{ row.rawSalePrice || '-' }} · 金额 {{ row.rawAmount || '-' }}
                        </p>
                      </template>
                    </el-table-column>
                    <el-table-column label="匹配商品 / SKU" min-width="300">
                      <template #default="{ row }">
                        <el-select
                          v-model="row.skuId"
                          filterable
                          clearable
                          :disabled="readonly"
                          placeholder="选择正确的 SKU"
                          class="!w-full"
                          @change="onSkuSelect(row)"
                        >
                          <el-option
                            v-for="sku in skuOptions"
                            :key="sku.skuId"
                            :label="sku.label"
                            :value="sku.skuId"
                          />
                        </el-select>
                        <div class="mt-2 flex gap-2">
                          <el-tag :type="row.skuId ? 'success' : 'warning'" size="small">
                            {{ row.skuId ? '已匹配' : '待匹配' }}
                          </el-tag>
                          <el-tag
                            v-if="hasPriceDifference(row)"
                            type="danger"
                            size="small"
                          >
                            与系统价不同
                          </el-tag>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="数量" width="105">
                      <template #default="{ row }">
                        <el-input-number v-model="row.quantity" :min="1" :controls="false" :disabled="readonly" class="!w-full" />
                      </template>
                    </el-table-column>
                    <el-table-column label="纸单销售价" width="135">
                      <template #default="{ row }">
                        <el-input-number v-model="row.salePrice" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                      </template>
                    </el-table-column>
                    <el-table-column label="系统参考价" width="125" align="right">
                      <template #default="{ row }">
                        <span class="text-slate-500">{{ money(row.systemReferencePrice) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="纸单金额" width="125">
                      <template #default="{ row }">
                        <el-input-number v-model="row.paperAmount" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                      </template>
                    </el-table-column>
                    <el-table-column label="计算金额" width="115" align="right">
                      <template #default="{ row }">
                        <span :class="hasAmountDifference(row) ? 'font-bold text-orange-600' : 'text-slate-700'">
                          {{ money(lineAmount(row)) }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column v-if="!readonly" label="操作" width="105" fixed="right">
                      <template #default="{ row, $index }">
                        <el-button link @click="splitItem(row)">拆分</el-button>
                        <el-button link type="danger" @click="removeItem($index)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <div class="grid grid-cols-2 gap-3 border-t border-slate-100 bg-slate-50 px-5 py-4 text-sm md:grid-cols-4">
                  <div>
                    <p class="text-xs text-slate-400">纸单总额</p>
                    <p class="mt-1 text-lg font-black text-slate-900">{{ money(current.paperTotalAmount) }}</p>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400">系统计算</p>
                    <p class="mt-1 text-lg font-black" :class="totalMismatch ? 'text-orange-600' : 'text-emerald-600'">
                      {{ money(calculatedTotal) }}
                    </p>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400">定金</p>
                    <p class="mt-1 text-lg font-black text-blue-600">{{ money(current.deposit) }}</p>
                  </div>
                  <div>
                    <p class="text-xs text-slate-400">余额</p>
                    <p class="mt-1 text-lg font-black text-slate-900">{{ money(Math.max(Number(current.paperTotalAmount || 0) - Number(current.deposit || 0), 0)) }}</p>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { filePreviewUrl } from '@/api/file'
import { getProductPage, type ProductVO } from '@/api/product'
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
const skuOptions = ref<SkuOption[]>([])
const statusOptions = [
  { label: '待处理', value: 'EDITING' },
  { label: '已确认', value: 'CONFIRMED' },
]

const readonly = computed(() => current.value?.status !== 'EDITING')
const calculatedTotal = computed(() =>
  (current.value?.items || []).reduce((sum, item) => sum + lineAmount(item), 0)
)
const totalMismatch = computed(() =>
  current.value?.paperTotalAmount != null
  && Math.abs(calculatedTotal.value - Number(current.value.paperTotalAmount)) > 0.01
)

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
    if (!selectedId.value && drafts.value.length) {
      await selectDraft(drafts.value[0].id)
    }
  } finally {
    listLoading.value = false
  }
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
        label: `${product.productCode} / ${product.name} · ${sku.colorName || '-'} · ${sku.sizeName || '-'}`,
        price: Number(sku.price || product.wholesalePrice || 0),
      }))
  )
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
    warnings: [],
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
.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field > span {
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

.field > small {
  color: #94a3b8;
  font-size: 11px;
}

:deep(.draft-table .el-table__cell) {
  padding: 14px 0;
}
</style>
