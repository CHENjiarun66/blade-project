<template>
  <div class="orders-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">订单管理</h2>
        <p class="text-gray-500 text-sm">实时查看、追踪并管理您的服装店订单。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="router.push('/orders/new')">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建订单
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="bg-white rounded-xl p-6 mb-6 shadow-sm flex flex-wrap items-center gap-6">
      <!-- 关键字搜索 -->
      <div class="w-[280px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">关键字搜索</label>
        <el-input
          v-model="searchQuery"
          placeholder="搜索订单号、客户名称"
          class="order-search-input"
          clearable
        >
          <template #prefix>
            <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
          </template>
        </el-input>
      </div>

      <!-- 订单状态 -->
      <div class="flex-1 min-w-[160px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">订单状态</label>
        <el-select v-model="statusFilter" placeholder="全部" class="order-select">
          <el-option label="全部" :value="null" />
          <el-option label="创建" :value="0" />
          <el-option label="已付款" :value="1" />
          <el-option label="配货中" :value="2" />
          <el-option label="待发货" :value="3" />
          <el-option label="已发货" :value="4" />
          <el-option label="已完成" :value="5" />
          <el-option label="已取消" :value="6" />
          <el-option label="退货中" :value="7" />
          <el-option label="已退货" :value="8" />
        </el-select>
      </div>

      <!-- 支付状态 -->
      <div class="flex-1 min-w-[160px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">支付状态</label>
        <el-select v-model="paymentStatusFilter" placeholder="全部" class="order-select">
          <el-option label="全部" :value="null" />
          <el-option label="未付款" :value="0" />
          <el-option label="已付定金" :value="1" />
          <el-option label="已付全款" :value="2" />
        </el-select>
      </div>

      <!-- 日期范围 -->
      <div class="w-[220px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">日期范围</label>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="order-date-picker !w-full"
        />
      </div>

      <!-- 重置按钮 -->
      <div class="ml-auto flex items-end">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200" @click="handleReset">
          <span class="material-symbols-outlined text-sm mr-1">filter_list</span>
          重置筛选
        </el-button>
      </div>
    </div>

    <!-- 订单表格 -->
    <div class="bg-white rounded-xl shadow-sm mb-6">
      <div class="order-table-wrapper">
        <el-table
          :data="tableData"
          class="order-table"
          @row-click="handleRowClick"
        >
          <el-table-column label="订单编号" min-width="150">
            <template #default="{ row }">
              <span class="text-[#408aee] font-bold text-sm">{{ row.orderNo }}</span>
              <p class="text-[10px] text-gray-400 mt-0.5">{{ formatDate(row.createTime) }}</p>
            </template>
          </el-table-column>

          <el-table-column label="客户" min-width="180">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <div v-if="row.customerAvatar" class="w-9 h-9 rounded-full overflow-hidden flex-shrink-0">
                  <img :src="row.customerAvatar" class="w-full h-full object-cover" />
                </div>
                <div v-else class="w-9 h-9 rounded-full bg-[#408aee]/10 flex items-center justify-center text-[#408aee] font-bold text-xs flex-shrink-0">
                  {{ getCustomerInitials(row.customerName) }}
                </div>
                <div class="overflow-hidden">
                  <p class="text-sm font-semibold text-gray-900 truncate">{{ row.customerName }}</p>
                  <p class="text-[10px] text-gray-400">{{ row.customerPhone || '无电话' }}</p>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="订单金额" min-width="110" align="right">
            <template #default="{ row }">
              <span class="text-sm font-bold text-gray-900">{{ formatMoney(row.totalAmount) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="已付金额" min-width="110" align="right">
            <template #default="{ row }">
              <span class="text-sm font-bold text-gray-900">{{ formatMoney(row.paidAmount) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="订单状态" min-width="100">
            <template #default="{ row }">
              <span :class="getOrderStatusClass(row.status)">
                <span :class="getOrderStatusDotClass(row.status)"></span>
                {{ row.statusName }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="支付状态" min-width="100">
            <template #default="{ row }">
              <span :class="getPaymentStatusClass(row.paymentStatus)">
                <span :class="getPaymentStatusDotClass(row.paymentStatus)"></span>
                {{ getPaymentStatusName(row.paymentStatus) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="配送" min-width="90">
            <template #default="{ row }">
              <span class="text-xs text-gray-500">
                {{ row.needDelivery ? (row.isDelivered ? '已送货' : '需要送货') : '自取' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="备注" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-xs text-gray-500 truncate">{{ row.remark || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="开单人员" min-width="100">
            <template #default="{ row }">
              <span class="text-xs text-gray-500">{{ row.salesmanName || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="图片" min-width="70" align="center">
            <template #default="{ row }">
              <span v-if="row.images" class="material-symbols-outlined text-[#408aee] cursor-pointer">image</span>
              <span v-else class="material-symbols-outlined text-gray-300">image</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" min-width="100" align="center" fixed="right" @click.stop>
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                size="small"
                @click.stop="handleEdit(row)"
              >
                <span class="material-symbols-outlined text-sm mr-0.5">edit</span>
                编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between px-6 py-4 bg-gray-50/50">
        <p class="text-xs text-gray-500 font-medium">
          显示第 <span class="text-gray-900">{{ (currentPage - 1) * pageSize + 1 }}</span>-<span class="text-gray-900">{{ Math.min(currentPage * pageSize, total) }}</span> 条，共 <span class="text-gray-900">{{ total }}</span> 条订单
        </p>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadData"
        />
      </div>
    </div>
  <!-- 编辑订单弹窗 -->
  <el-dialog
    v-model="showEditDialog"
    title="编辑订单"
    width="600px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 订单上下文摘要 -->
    <div class="mb-4 p-3 bg-gray-50 rounded-lg text-sm text-gray-600 flex gap-6">
      <span>订单号：<b class="text-gray-900">{{ editingOrder?.orderNo }}</b></span>
      <span>状态：<el-tag size="small">{{ editingOrder?.statusName }}</el-tag></span>
      <span>金额：<b class="text-gray-900">{{ formatMoney(editingOrder?.totalAmount ?? 0) }}</b></span>
    </div>
    <el-form
      ref="editFormRef"
      :model="editForm"
      :rules="editRules"
      label-width="100px"
      class="pt-2"
    >
      <el-form-item label="客户名称" prop="customerName">
        <el-input v-model="editForm.customerName" placeholder="请输入客户名称" />
      </el-form-item>
      <el-form-item label="客户电话" prop="customerPhone">
        <el-input v-model="editForm.customerPhone" placeholder="请输入客户电话" />
      </el-form-item>
      <el-form-item label="客户地址">
        <el-input v-model="editForm.customerAddress" placeholder="请输入客户地址" />
      </el-form-item>
      <el-form-item label="送货方式">
        <el-radio-group v-model="editForm.needDelivery">
          <el-radio :value="0">自取</el-radio>
          <el-radio :value="1">需要送货</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="editForm.needDelivery === 1" label="送货地址">
        <el-input v-model="editForm.deliveryAddress" placeholder="请输入送货地址" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="editForm.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入备注"
        />
      </el-form-item>
      <el-form-item label="图片链接">
        <el-input
          v-model="editForm.images"
          type="textarea"
          :rows="2"
          placeholder="图片URL，多个用英文逗号分隔"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEditDialog = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="handleEditSave">保存</el-button>
    </template>
  </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderPage, updateOrder, type OrderVO } from '@/api/order'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const router = useRouter()

// 筛选条件
const searchQuery = ref('')
const statusFilter = ref<number | null>(null)
const paymentStatusFilter = ref<number | null>(null)
const dateRange = ref<Date[]>([])

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 表格数据
const tableData = ref<OrderVO[]>([])

// 加载数据
async function loadData() {
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      orderNo: searchQuery.value || undefined,
      customerName: searchQuery.value || undefined,
      status: statusFilter.value !== null ? statusFilter.value : undefined,
      paymentStatus: paymentStatusFilter.value !== null ? paymentStatusFilter.value : undefined
    }
    const res = await getOrderPage(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (error: any) {
    ElMessage.error(error.message || '加载订单列表失败')
  }
}

// 重置筛选
function handleReset() {
  searchQuery.value = ''
  statusFilter.value = null
  paymentStatusFilter.value = null
  dateRange.value = []
  currentPage.value = 1
  loadData()
}

// 编辑弹窗
const showEditDialog = ref(false)
const editSaving = ref(false)
const editFormRef = ref<FormInstance>()
const editingOrder = ref<OrderVO | null>(null)
const editForm = reactive({
  customerName: '',
  customerPhone: '',
  customerAddress: '',
  needDelivery: 0,
  deliveryAddress: '',
  remark: '',
  images: '',
})
const editRules: FormRules = {
  customerName: [{ required: true, message: '客户名称不能为空', trigger: 'blur' }],
}

function handleEdit(row: OrderVO) {
  editingOrder.value = row
  editForm.customerName = row.customerName || ''
  editForm.customerPhone = row.customerPhone || ''
  editForm.customerAddress = row.customerAddress || ''
  editForm.needDelivery = row.needDelivery ?? 0
  editForm.deliveryAddress = row.deliveryAddress || ''
  editForm.remark = row.remark || ''
  editForm.images = row.images || ''
  showEditDialog.value = true
}

async function handleEditSave() {
  if (!editFormRef.value || !editingOrder.value) return
  await editFormRef.value.validate()
  editSaving.value = true
  try {
    await updateOrder(editingOrder.value.id, { ...editForm })
    ElMessage.success('订单信息已更新')
    showEditDialog.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    editSaving.value = false
  }
}

// 行点击
function handleRowClick(row: OrderVO) {
  router.push(`/orders/${row.id}`)
}

// 格式化日期
function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}年${month}月${day}日`
}

// 格式化金额
function formatMoney(amount: number): string {
  return `¥${Number(amount || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`
}

// 获取客户首字母
function getCustomerInitials(name: string): string {
  if (!name) return '?'
  return name.slice(0, 2).toUpperCase()
}

// 订单状态样式
function getOrderStatusClass(status: number): string {
  const classMap: Record<number, string> = {
    0: 'bg-gray-100 text-gray-500',
    1: 'bg-[#408aee]/10 text-[#408aee]',
    2: 'bg-orange-100 text-orange-600',
    3: 'bg-green-100 text-green-600',
    4: 'bg-gray-100 text-gray-400',
    5: 'bg-red-100 text-red-600',
    6: 'bg-yellow-100 text-yellow-600',
    7: 'bg-purple-100 text-purple-600'
  }
  return `inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${classMap[status] || 'bg-gray-100 text-gray-500'}`
}

function getOrderStatusDotClass(status: number): string {
  const classMap: Record<number, string> = {
    0: 'bg-gray-400',
    1: 'bg-[#408aee]',
    2: 'bg-orange-500',
    3: 'bg-green-500',
    4: 'bg-gray-400',
    5: 'bg-red-500',
    6: 'bg-yellow-500',
    7: 'bg-purple-500'
  }
  return `w-1.5 h-1.5 rounded-full ${classMap[status] || 'bg-gray-400'}`
}

// 支付状态名称
function getPaymentStatusName(status: number): string {
  const nameMap: Record<number, string> = {
    0: '未付款',
    1: '已付定金',
    2: '已付全款'
  }
  return nameMap[status] || '未知'
}

// 支付状态样式
function getPaymentStatusClass(status: number): string {
  const classMap: Record<number, string> = {
    0: 'bg-red-100 text-red-600',
    1: 'bg-[#408aee]/10 text-[#408aee]',
    2: 'bg-green-100 text-green-600'
  }
  return `inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${classMap[status] || 'bg-gray-100 text-gray-500'}`
}

function getPaymentStatusDotClass(status: number): string {
  const classMap: Record<number, string> = {
    0: 'bg-red-500',
    1: 'bg-[#408aee]',
    2: 'bg-green-500'
  }
  return `w-1.5 h-1.5 rounded-full ${classMap[status] || 'bg-gray-400'}`
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.orders-page {
  padding: 0;
}

/* 搜索输入框 */
.order-search-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.order-search-input :deep(.el-input__inner) {
  font-size: 14px !important;
  color: #1a1a2e !important;
}

/* 下拉选择框 */
.order-select {
  width: 100%;
}

.order-select :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

/* 日期选择器 */
.order-date-picker :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

/* 表格容器 - 支持水平滚动 */
.order-table-wrapper {
  overflow-x: auto;
}

.order-table :deep(.el-table__row) {
  cursor: pointer !important;
}

.order-table :deep(.el-table__row:hover > td) {
  background-color: rgb(249 250 251 / 30%) !important;
}

/* 分页样式 */
:deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #408aee !important;
}
</style>
