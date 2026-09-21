<template>
  <div class="draft-list-page">
    <header class="page-header">
      <div>
        <div class="flex items-center gap-3">
          <h2 class="text-2xl font-bold tracking-tight text-gray-900">草稿订单列表</h2>
          <span class="draft-count-tag">{{ total }} 张待处理</span>
        </div>
        <p class="mt-1 text-sm text-gray-500">集中查找尚未生成正式订单的草稿，点击任意一行继续录入。</p>
      </div>
      <el-button type="primary" class="primary-action" @click="router.push('/orders/quick')">
        <span class="material-symbols-outlined mr-1 text-base">edit_note</span>
        快速录单
      </el-button>
    </header>

    <section class="filter-panel" aria-label="草稿筛选">
      <label class="field-block field-keyword">
        <span>搜索草稿</span>
        <el-input v-model="keyword" clearable placeholder="纸质单号、客户名称或外部编号" @keyup.enter="handleSearch">
          <template #prefix><span class="material-symbols-outlined text-base text-gray-400">search</span></template>
        </el-input>
      </label>
      <label class="field-block">
        <span>单据批次</span>
        <el-select v-model="batchFilter" clearable filterable placeholder="全部批次">
          <el-option
            v-for="batch in batches"
            :key="batchKey(batch.sourceBatchNo)"
            :value="batchKey(batch.sourceBatchNo)"
            :label="`${batchLabel(batch.sourceBatchNo)}（${batch.draftCount}）`"
          />
        </el-select>
      </label>
      <label class="field-block">
        <span>草稿来源</span>
        <el-select v-model="entrySource" clearable placeholder="全部来源">
          <el-option label="Agent 导入" value="AGENT" />
          <el-option label="手工暂存" value="MANUAL" />
        </el-select>
      </label>
      <label class="field-block">
        <span>档口</span>
        <el-select v-model="outletFilter" clearable placeholder="全部档口" data-testid="draft-outlet-filter">
          <el-option label="全部档口" :value="null" />
          <el-option
            v-for="outlet in outletFilterOptions"
            :key="outlet.id"
            :label="outletFilterLabel(outlet)"
            :value="outlet.id"
          />
          <el-option v-if="canUseUnassigned" label="待归档档口" value="UNASSIGNED" />
        </el-select>
      </label>
      <label class="field-block">
        <span>匹配情况</span>
        <el-select v-model="unresolvedOnly" clearable placeholder="全部草稿">
          <el-option label="仅看有待匹配商品" :value="true" />
        </el-select>
      </label>
      <label class="field-block field-date">
        <span>订单日期</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-full"
        />
      </label>
      <div class="filter-actions">
        <el-button type="primary" class="filter-button" @click="handleSearch">查询</el-button>
        <el-button class="filter-button" @click="handleReset">重置</el-button>
      </div>
    </section>

    <section class="table-panel" aria-label="待处理草稿列表">
      <el-table
        v-loading="loading"
        :data="drafts"
        row-class-name="clickable-row"
        empty-text="暂无待处理草稿"
        @row-click="openDraft"
      >
        <el-table-column label="单据批次" width="120">
          <template #default="{ row }">
            <span class="batch-chip">{{ batchLabel(row.sourceBatchNo) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="纸质单号" min-width="140">
          <template #default="{ row }">
            <button class="draft-link" type="button" @click.stop="openDraft(row)">
              {{ row.sourceOrderNo || row.externalRefNo }}
            </button>
            <p class="cell-secondary">{{ row.externalRefNo }}</p>
          </template>
        </el-table-column>
        <el-table-column label="客户" min-width="120">
          <template #default="{ row }"><span class="cell-primary">{{ row.customerName || '散客' }}</span></template>
        </el-table-column>
        <el-table-column label="档口" min-width="130">
          <template #default="{ row }">
            <el-tag
              v-if="row.sourceOutletId == null"
              size="small"
              type="info"
              effect="plain"
              data-testid="draft-pending-outlet-tag"
            >
              待归档档口
            </el-tag>
            <span v-else class="cell-primary">{{ row.sourceShop || row.sourceOutletCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单日期" width="110">
          <template #default="{ row }">{{ row.orderDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="商品情况" min-width="130">
          <template #default="{ row }">
            <span class="cell-primary">{{ row.itemCount }} 行商品</span>
            <p v-if="row.unresolvedCount" class="cell-warning">{{ row.unresolvedCount }} 行待匹配</p>
            <p v-else class="cell-success">商品已匹配</p>
          </template>
        </el-table-column>
        <el-table-column label="草稿金额" width="120" align="right">
          <template #default="{ row }"><strong class="amount-text">{{ money(row.paperTotalAmount) }}</strong></template>
        </el-table-column>
        <el-table-column label="图片" width="75" align="center">
          <template #default="{ row }">
            <div v-if="row.sourceFileId" class="paper-thumb">
              <img :src="filePreviewUrl(row.sourceFileId)" :alt="`${row.sourceOrderNo || '草稿'}纸单缩略图`" loading="lazy">
              <span v-if="row.sourceFileCount > 1">{{ row.sourceFileCount }}</span>
            </div>
            <span v-else class="text-gray-400">无</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <span class="source-chip" :class="{ manual: row.entrySource === 'MANUAL' }">
              {{ row.entrySource === 'MANUAL' ? '手工暂存' : 'Agent 导入' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最后修改" width="140">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" class="continue-button" @click.stop="openDraft(row)">继续编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <footer class="pagination-footer">
        <span>共 {{ total }} 张待处理草稿</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          background
          @current-change="loadDrafts"
          @size-change="handleSizeChange"
        />
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getOrderDraftBatches,
  getOrderDraftPage,
  type OrderDraftBatchSummary,
  type OrderDraftSummary,
} from '@/api/orderDraft'
import type { OutletOptionVO } from '@/api/outlet'
import { filePreviewUrl } from '@/api/file'
import { useAuthStore } from '@/stores/auth'
import { loadOutletOptions } from '@/utils/outletOptions'

const UNBATCHED = '__UNBATCHED__'
const router = useRouter()
const authStore = useAuthStore()
const drafts = ref<OrderDraftSummary[]>([])
const batches = ref<OrderDraftBatchSummary[]>([])
const keyword = ref('')
const batchFilter = ref<string>()
const entrySource = ref<'AGENT' | 'MANUAL'>()
const unresolvedOnly = ref<boolean>()
const outletFilter = ref<number | 'UNASSIGNED' | null>(null)
const outletFilterOptions = ref<OutletOptionVO[]>([])
const canUseUnassigned = computed(() => authStore.permissions.includes('data:outlet:unassigned'))
const dateRange = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

function outletFilterLabel(outlet: OutletOptionVO) {
  return outlet.outletCode ? `${outlet.outletName}（${outlet.outletCode}）` : outlet.outletName
}

async function loadOutletFilterOptions() {
  try {
    const options = await loadOutletOptions()
    outletFilterOptions.value = options.items || []
  } catch {
    outletFilterOptions.value = []
  }
}

function batchKey(value?: string) {
  return value?.trim() || UNBATCHED
}

function batchLabel(value?: string) {
  const normalized = value?.trim()
  if (!normalized) return '未分批'
  return /^\d+$/.test(normalized) ? `第 ${normalized} 单` : normalized
}

async function loadBatches() {
  const response = await getOrderDraftBatches()
  batches.value = response.data || []
}

async function loadDrafts() {
  loading.value = true
  try {
    const response = await getOrderDraftPage({
      current: currentPage.value,
      size: pageSize.value,
      status: 'EDITING',
      keyword: keyword.value.trim() || undefined,
      sourceBatchNo: batchFilter.value,
      entrySource: entrySource.value,
      unresolvedOnly: unresolvedOnly.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      sourceOutletId: typeof outletFilter.value === 'number' ? outletFilter.value : undefined,
      unassignedOnly: outletFilter.value === 'UNASSIGNED' ? true : undefined,
    })
    drafts.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载草稿订单列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadDrafts()
}

function handleReset() {
  keyword.value = ''
  batchFilter.value = undefined
  entrySource.value = undefined
  unresolvedOnly.value = undefined
  outletFilter.value = null
  dateRange.value = []
  currentPage.value = 1
  loadDrafts()
}

function handleSizeChange() {
  currentPage.value = 1
  loadDrafts()
}

function openDraft(row: OrderDraftSummary) {
  router.push(`/orders/drafts/${row.id}`)
}

function money(value?: number) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  try {
    await Promise.all([loadBatches(), loadDrafts(), loadOutletFilterOptions()])
  } catch (error: any) {
    ElMessage.error(error.message || '加载草稿数据失败')
  }
})
</script>

<style scoped>
.draft-list-page {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.primary-action,
.filter-button {
  min-height: 44px;
  border-radius: 10px;
  font-weight: 700;
}

.primary-action {
  border: 0;
  padding-right: 22px;
  padding-left: 22px;
  background: #408aee;
}

.draft-count-tag {
  flex: 0 0 auto;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  padding: 4px 10px;
  color: #b45309;
  background: #fff7ed;
  font-size: 12px;
  font-weight: 700;
}

.filter-panel {
  display: grid;
  grid-template-columns: minmax(240px, 1.5fr) repeat(4, minmax(150px, 0.75fr)) minmax(260px, 1.2fr) auto;
  align-items: end;
  gap: 16px;
  margin-bottom: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  background: #fff;
}

.field-block {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.field-block > span {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.table-panel {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.table-panel :deep(.clickable-row) {
  cursor: pointer;
}

.table-panel :deep(.clickable-row:hover > td.el-table__cell) {
  background: #f8fbff;
}

.batch-chip {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  color: #1e40af;
  background: #eff6ff;
  font-size: 12px;
  font-weight: 800;
}

.draft-link {
  border: 0;
  padding: 4px 0;
  color: #2563eb;
  background: transparent;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}

.draft-link:focus-visible {
  border-radius: 4px;
  outline: 3px solid rgb(64 138 238 / 28%);
  outline-offset: 2px;
}

.cell-primary,
.amount-text {
  color: #0f172a;
  font-weight: 700;
}

.cell-secondary,
.cell-success,
.cell-warning {
  margin-top: 3px;
  font-size: 11px;
}

.cell-secondary {
  overflow: hidden;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-success { color: #047857; }
.cell-warning { color: #b45309; font-weight: 700; }

.paper-thumb {
  position: relative;
  width: 46px;
  height: 54px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 7px;
  background: #f8fafc;
}

.paper-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.paper-thumb span {
  position: absolute;
  right: 2px;
  bottom: 2px;
  min-width: 18px;
  border-radius: 999px;
  padding: 1px 4px;
  color: #fff;
  background: rgb(15 23 42 / 82%);
  font-size: 10px;
  line-height: 16px;
}

.source-chip {
  display: inline-flex;
  border-radius: 999px;
  padding: 4px 8px;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.source-chip.manual {
  color: #475569;
  background: #f1f5f9;
}

.continue-button {
  min-height: 40px;
  border: 0;
  border-radius: 8px;
  background: #408aee;
  font-weight: 700;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-top: 1px solid #e5e7eb;
  padding: 18px 20px;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 1536px) {
  .filter-panel {
    grid-template-columns: repeat(4, minmax(150px, 1fr));
  }

  .field-keyword,
  .field-date {
    grid-column: span 2;
  }
}

@media (max-width: 1100px) {
  .filter-panel {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 768px) {
  .page-header,
  .pagination-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-panel {
    grid-template-columns: 1fr;
  }

  .field-keyword,
  .field-date {
    grid-column: auto;
  }

  .filter-actions > * {
    flex: 1;
  }
}
</style>
