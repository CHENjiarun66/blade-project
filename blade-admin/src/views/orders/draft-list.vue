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
      <div v-if="canDelete" class="table-toolbar">
        <span v-if="selectedDrafts.length">已选择 {{ selectedDrafts.length }} 张草稿</span>
        <span v-else>可勾选多张草稿进行批量删除</span>
        <el-button
          v-if="selectedDrafts.length"
          type="danger"
          plain
          :loading="deleting"
          @click="deleteSelectedDrafts"
        >
          批量删除
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="drafts"
        row-class-name="clickable-row"
        empty-text="暂无待处理草稿"
        @row-click="openDraft"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="canDelete" type="selection" width="52" align="center" />
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
        <el-table-column label="操作" :width="canDelete ? 176 : 100" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" class="continue-button" @click.stop="openDraft(row)">继续编辑</el-button>
              <el-button v-if="canDelete" type="danger" text class="delete-button" @click.stop="deleteDraft(row)">删除</el-button>
            </div>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  batchDeleteOrderDrafts,
  deleteOrderDraft,
  getOrderDraftBatches,
  getOrderDraftPage,
  type OrderDraftBatchSummary,
  type OrderDraftSummary,
} from '@/api/orderDraft'
import { filePreviewUrl } from '@/api/file'

const UNBATCHED = '__UNBATCHED__'
const router = useRouter()
const authStore = useAuthStore()
const canDelete = computed(() => authStore.permissions.includes('btn:order:delete'))
const drafts = ref<OrderDraftSummary[]>([])
const batches = ref<OrderDraftBatchSummary[]>([])
const keyword = ref('')
const batchFilter = ref<string>()
const entrySource = ref<'AGENT' | 'MANUAL'>()
const unresolvedOnly = ref<boolean>()
const dateRange = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const deleting = ref(false)
const selectedDrafts = ref<OrderDraftSummary[]>([])

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

function handleSelectionChange(rows: OrderDraftSummary[]) {
  selectedDrafts.value = rows
}

async function confirmDeletion(count: number, label?: string, batch = false) {
  const target = !batch && count === 1 ? `草稿“${label || ''}”` : `选中的 ${count} 张草稿`
  try {
    await ElMessageBox.confirm(
      `确定删除${target}吗？草稿及商品明细将移入逻辑删除状态；已上传的纸质单图片不会物理删除，会保留在文件中心“未绑定”中。`,
      batch ? '批量删除草稿' : '删除草稿',
      {
        type: 'warning',
        confirmButtonText: batch ? `删除 ${count} 张` : '确认删除',
        cancelButtonText: '取消',
      },
    )
    return true
  } catch {
    return false
  }
}

async function reloadAfterDelete(deletedCount: number) {
  if (drafts.value.length <= deletedCount && currentPage.value > 1) {
    currentPage.value -= 1
  }
  selectedDrafts.value = []
  await Promise.all([loadBatches(), loadDrafts()])
}

async function deleteDraft(row: OrderDraftSummary) {
  const label = row.sourceOrderNo || row.externalRefNo
  if (!await confirmDeletion(1, label)) return
  deleting.value = true
  try {
    await deleteOrderDraft(row.id)
    await reloadAfterDelete(1)
    ElMessage.success('草稿已删除')
  } catch (error: any) {
    ElMessage.error(error.message || '删除草稿失败')
  } finally {
    deleting.value = false
  }
}

async function deleteSelectedDrafts() {
  const selected = [...selectedDrafts.value]
  if (!selected.length || !await confirmDeletion(selected.length, undefined, true)) return
  deleting.value = true
  try {
    await batchDeleteOrderDrafts(selected.map(draft => draft.id))
    await reloadAfterDelete(selected.length)
    ElMessage.success(`已删除 ${selected.length} 张草稿`)
  } catch (error: any) {
    ElMessage.error(error.message || '批量删除草稿失败')
  } finally {
    deleting.value = false
  }
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
    await Promise.all([loadBatches(), loadDrafts()])
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
  grid-template-columns: minmax(240px, 1.5fr) repeat(3, minmax(150px, 0.75fr)) minmax(260px, 1.2fr) auto;
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

.table-toolbar {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #e5e7eb;
  padding: 9px 18px;
  color: #64748b;
  font-size: 13px;
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

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.delete-button {
  min-height: 40px;
  margin-left: 0;
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
