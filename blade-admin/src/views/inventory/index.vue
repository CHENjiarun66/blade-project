<template>
  <div class="inventory-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">库存管理</h2>
        <p class="text-gray-500 text-sm">实时查看、追踪并管理您的服装店库存。</p>
      </div>
      <div class="flex gap-2">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200" @click="handleRefresh">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button class="!bg-[#408aee] !text-white !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="showStockInDialog = true">
          <span class="material-symbols-outlined text-sm mr-1">upload</span>
          入库
        </el-button>
        <el-button class="!bg-[#ff9800] !text-white !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#e68900] shadow-lg shadow-orange-500/20" @click="showStockOutDialog = true">
          <span class="material-symbols-outlined text-sm mr-1">download</span>
          出库
        </el-button>
        <el-button class="!bg-[#4caf50] !text-white !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#3d8b40] shadow-lg shadow-green-500/20" @click="showAdjustDialog = true">
          <span class="material-symbols-outlined text-sm mr-1">tune</span>
          调整
        </el-button>
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200" @click="openLogDialog">
          <span class="material-symbols-outlined text-sm mr-1">history</span>
          记录
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="bg-white rounded-xl p-6 mb-6 shadow-sm flex flex-wrap items-center gap-6">
      <div class="w-[280px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">关键字搜索</label>
        <el-input
          v-model="searchQuery"
          placeholder="搜索SKU编码、商品名称"
          class="inventory-search-input"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
          </template>
        </el-input>
      </div>

      <div class="flex-1 min-w-[160px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">仓库</label>
        <el-select v-model="warehouseFilter" placeholder="全部" class="inventory-select" clearable>
          <el-option label="全部仓库" :value="undefined" />
          <el-option v-for="w in warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id" />
        </el-select>
      </div>

      <div class="flex-1 min-w-[160px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">库存状态</label>
        <el-select v-model="statusFilter" placeholder="全部" class="inventory-select">
          <el-option label="全部" :value="undefined" />
          <el-option label="全部" :value="undefined" />
          <el-option label="正常" value="normal" />
          <el-option label="预警" value="below" />
          <el-option label="危险" value="danger" />
        </el-select>
      </div>

      <div class="ml-auto flex items-end">
        <el-button class="!bg-[#408aee] !text-white !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0]" @click="handleSearch">
          <span class="material-symbols-outlined text-sm mr-1">search</span>
          搜索
        </el-button>
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 ml-3" @click="handleReset">
          <span class="material-symbols-outlined text-sm mr-1">filter_list</span>
          重置筛选
        </el-button>
      </div>
    </div>

    <!-- 库存表格 -->
    <div class="bg-white rounded-xl overflow-hidden shadow-sm mb-6">
      <el-table :data="tableData" class="inventory-table" v-loading="loading" empty-text="暂无库存数据">
        <el-table-column label="SKU信息" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-[#408aee]/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-[#408aee]">inventory_2</span>
              </div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.productName }}</div>
                <div class="text-xs text-[#408aee] font-medium">{{ row.skuCode }}</div>
                <div class="text-xs text-gray-400">{{ row.colorName }} / {{ row.sizeName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="仓库" min-width="120">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.warehouseName }}</span>
          </template>
        </el-table-column>

        <el-table-column label="库存数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="text-sm font-bold text-gray-900">{{ row.quantity }}</span>
          </template>
        </el-table-column>

        <el-table-column label="预占数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="text-sm text-orange-500">{{ (row.reservedQty || 0) + (row.globalReservedQty || 0) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="可用数量" min-width="100" align="right">
          <template #default="{ row }">
            <span class="text-sm font-bold text-gray-900">{{ row.availableQty || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="单价" min-width="100" align="right">
          <template #default="{ row }">
            <span class="text-sm font-bold text-gray-900">¥{{ row.price }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <span :class="getStatusClass(row.alertStatus)" class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider">
              <span :class="getStatusDotClass(row.alertStatus)" class="w-1.5 h-1.5 rounded-full"></span>
              {{ getStatusText(row.alertStatus) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="120" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-2">
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-[#408aee]" @click="handleViewLog(row)">明细</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="flex justify-between items-center p-4 border-t border-gray-100">
        <span class="text-sm text-gray-500">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </div>

    <!-- 入库弹窗 -->
    <el-dialog v-model="showStockInDialog" title="入库" width="700px" :close-on-click-modal="false">
      <el-form ref="stockInFormRef" :model="stockInForm" :rules="stockInRules" label-width="100px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="stockInForm.warehouseId" placeholder="请选择仓库" class="w-full">
            <el-option v-for="w in warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="入库商品" prop="items">
          <div class="w-full">
            <div v-for="(item, index) in stockInForm.items" :key="index" class="flex items-center gap-2 mb-2">
              <el-select v-model="item.skuId" placeholder="选择商品" filterable class="flex-1" @change="handleSkuSelect(item)">
                <el-option v-for="sku in skuOptions" :key="sku.id" :label="`${sku.skuCode} - ${sku.productName}`" :value="sku.id" />
              </el-select>
              <el-input-number v-model="item.quantity" :min="1" :controls="false" class="w-28" placeholder="数量" />
              <el-input v-model="item.remark" placeholder="备注" class="w-32" />
              <el-button type="danger" link @click="removeStockInItem(index)" :disabled="stockInForm.items.length <= 1">
                <span class="material-symbols-outlined text-sm">delete</span>
              </el-button>
            </div>
            <el-button type="primary" link @click="addStockInItem">
              <span class="material-symbols-outlined text-sm mr-1">add</span>添加商品
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="stockInForm.remark" type="textarea" :rows="2" placeholder="入库备注" />
        </el-form-item>

        <el-form-item label="入库凭证">
          <input type="file" multiple accept="image/*" class="text-xs text-gray-500" @change="handleStockInImagesChange" />
          <p v-if="stockInImages.length" class="text-xs text-gray-400 mt-2">已选择 {{ stockInImages.length }} 张图片</p>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showStockInDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStockIn" :loading="submitLoading">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- 出库弹窗 -->
    <el-dialog v-model="showStockOutDialog" title="出库" width="700px" :close-on-click-modal="false">
      <el-form ref="stockOutFormRef" :model="stockOutForm" :rules="stockOutRules" label-width="100px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="stockOutForm.warehouseId" placeholder="请选择仓库" class="w-full">
            <el-option v-for="w in warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="出库商品" prop="items">
          <div class="w-full">
            <div v-for="(item, index) in stockOutForm.items" :key="index" class="flex items-center gap-2 mb-2">
              <el-select v-model="item.skuId" placeholder="选择商品" filterable class="flex-1" @change="handleSkuSelect(item)">
                <el-option v-for="sku in skuOptions" :key="sku.id" :label="`${sku.skuCode} - ${sku.productName}`" :value="sku.id" />
              </el-select>
              <el-input-number v-model="item.quantity" :min="1" :controls="false" class="w-28" placeholder="数量" />
              <el-input v-model="item.reason" placeholder="出库原因" class="w-32" />
              <el-button type="danger" link @click="removeStockOutItem(index)" :disabled="stockOutForm.items.length <= 1">
                <span class="material-symbols-outlined text-sm">delete</span>
              </el-button>
            </div>
            <el-button type="primary" link @click="addStockOutItem">
              <span class="material-symbols-outlined text-sm mr-1">add</span>添加商品
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="stockOutForm.remark" type="textarea" :rows="2" placeholder="出库备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showStockOutDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStockOut" :loading="submitLoading">确认出库</el-button>
      </template>
    </el-dialog>

    <!-- 调整弹窗 -->
    <el-dialog v-model="showAdjustDialog" title="库存调整" width="700px" :close-on-click-modal="false">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="adjustForm.warehouseId" placeholder="请选择仓库" class="w-full">
            <el-option v-for="w in warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="adjustForm.reason" placeholder="如：月度盘点" />
        </el-form-item>

        <el-form-item label="调整商品" prop="items">
          <div class="w-full">
            <div v-for="(item, index) in adjustForm.items" :key="index" class="flex items-center gap-2 mb-2">
              <el-select v-model="item.skuId" placeholder="选择商品" filterable class="flex-1">
                <el-option v-for="sku in skuOptions" :key="sku.id" :label="`${sku.skuCode} - ${sku.productName}`" :value="sku.id" />
              </el-select>
              <el-input-number v-model="item.quantity" :min="-9999" :controls="false" class="w-28" placeholder="调整数量" />
              <el-button type="danger" link @click="removeAdjustItem(index)" :disabled="adjustForm.items.length <= 1">
                <span class="material-symbols-outlined text-sm">delete</span>
              </el-button>
            </div>
            <el-button type="primary" link @click="addAdjustItem">
              <span class="material-symbols-outlined text-sm mr-1">add</span>添加商品
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" placeholder="调整备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdjust" :loading="submitLoading">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 库存记录弹窗 -->
    <el-dialog v-model="showLogDialog" title="库存记录" width="900px" :close-on-click-modal="false">
      <div class="mb-4 flex items-center gap-4">
        <el-select v-model="logFilter.skuId" placeholder="选择商品" filterable clearable class="w-60">
          <el-option v-for="sku in skuOptions" :key="sku.id" :label="`${sku.skuCode} - ${sku.productName}`" :value="sku.id" />
        </el-select>
        <el-select v-model="logFilter.changeType" placeholder="变动类型" clearable class="w-40">
          <el-option label="入库" value="PURCHASE_IN" />
          <el-option label="出库" value="SALE_OUT" />
          <el-option label="调整" value="ADJUST" />
          <el-option label="订单取消" value="SALE_CANCEL" />
        </el-select>
        <el-button type="primary" @click="loadLogData">
          <span class="material-symbols-outlined text-sm mr-1">search</span>
          查询
        </el-button>
      </div>

      <el-table :data="logTableData" v-loading="logLoading" empty-text="暂无记录">
        <el-table-column label="SKU" min-width="150">
          <template #default="{ row }">
            <span class="text-[#408aee] font-medium text-sm">{{ row.skuCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="仓库" min-width="100">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.warehouseName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动类型" min-width="100">
          <template #default="{ row }">
            <span :class="getChangeTypeClass(row.changeType)" class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold uppercase">
              {{ row.changeTypeName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动数量" min-width="100" align="center">
          <template #default="{ row }">
            <span :class="row.changeQty > 0 ? 'text-green-600' : 'text-red-600'" class="text-sm font-bold">
              {{ row.changeQty > 0 ? '+' : '' }}{{ row.changeQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前" min-width="80" align="center">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.beforeQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后" min-width="80" align="center">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.afterQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" min-width="100">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.operatorName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ formatDate(row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          v-model:current-page="logCurrentPage"
          v-model:page-size="logPageSize"
          :page-sizes="[10, 20, 50]"
          :total="logTotal"
          layout="sizes, prev, pager, next"
          @current-change="loadLogData"
          @size-change="loadLogData"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getInventoryPage,
  getWarehousePage,
  getAllSkus,
  stockIn,
  stockOut,
  adjustInventory,
  getInventoryLogPage,
  type InventoryVO,
  type WarehouseVO
} from '@/api/inventory'
import { uploadFile } from '@/api/file'
import { formatDate } from '@/utils/format'

// 搜索相关
const searchQuery = ref('')
const warehouseFilter = ref<number | undefined>(undefined)
const statusFilter = ref<string | undefined>(undefined)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

const tableData = ref<InventoryVO[]>([])
const warehouseList = ref<WarehouseVO[]>([])
const skuOptions = ref<{ id: number; skuCode: string; productName: string; colorName?: string; sizeName?: string; price: number }[]>([])

// 入库弹窗
const showStockInDialog = ref(false)
const stockInFormRef = ref()
const stockInForm = ref({
  warehouseId: undefined as number | undefined,
  items: [{ skuId: undefined as number | undefined, quantity: 1, remark: '' }],
  remark: ''
})
const stockInImages = ref<File[]>([])
const stockInRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  items: [{ required: true, message: '请添加入库商品', trigger: 'change' }]
}

// 出库弹窗
const showStockOutDialog = ref(false)
const stockOutFormRef = ref()
const stockOutForm = ref({
  warehouseId: undefined as number | undefined,
  source: 'OTHER',
  items: [{ skuId: undefined as number | undefined, quantity: 1, reason: '' }],
  remark: ''
})
const stockOutRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  items: [{ required: true, message: '请添加出库商品', trigger: 'change' }]
}

// 调整弹窗
const showAdjustDialog = ref(false)
const adjustFormRef = ref()
const adjustForm = ref({
  warehouseId: undefined as number | undefined,
  reason: '',
  items: [{ skuId: undefined as number | undefined, quantity: 0, reason: '' }],
  remark: ''
})
const adjustRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  items: [{ required: true, message: '请添加调整商品', trigger: 'change' }]
}

// 记录弹窗
const showLogDialog = ref(false)
const logLoading = ref(false)
const logTableData = ref<any[]>([])
const logCurrentPage = ref(1)
const logPageSize = ref(20)
const logTotal = ref(0)
const logFilter = ref({
  skuId: undefined as number | undefined,
  changeType: undefined as string | undefined
})

// 提交状态
const submitLoading = ref(false)

// 加载库存数据
async function loadData() {
  loading.value = true
  try {
    const res = await getInventoryPage({
      current: currentPage.value,
      size: pageSize.value,
      keyword: searchQuery.value || undefined,
      warehouseId: warehouseFilter.value,
      alertStatus: statusFilter.value
    })
    if (res.code === 200) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    console.error('加载库存列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载仓库列表
async function loadWarehouses() {
  try {
    const res = await getWarehousePage({ current: 1, size: 100 })
    if (res.code === 200) {
      warehouseList.value = res.data.records
    }
  } catch (error) {
    console.error('加载仓库列表失败:', error)
  }
}

// 加载SKU选项
async function loadSkuOptions() {
  try {
    const res = await getAllSkus()
    if (res.code === 200) {
      skuOptions.value = res.data
    }
  } catch (error) {
    console.error('加载SKU列表失败:', error)
  }
}

// 搜索
function handleSearch() {
  currentPage.value = 1
  loadData()
}

// 重置
function handleReset() {
  searchQuery.value = ''
  warehouseFilter.value = undefined
  statusFilter.value = undefined
  currentPage.value = 1
  loadData()
}

// 刷新
function handleRefresh() {
  loadData()
}

// 状态样式
function getStatusClass(status: string) {
  switch (status) {
    case 'normal':
      return 'bg-green-100 text-green-600'
    case 'warning':
      return 'bg-orange-100 text-orange-600'
    case 'danger':
      return 'bg-red-100 text-red-600'
    default:
      return 'bg-gray-100 text-gray-500'
  }
}

function getStatusDotClass(status: string) {
  switch (status) {
    case 'normal':
      return 'bg-green-500'
    case 'warning':
      return 'bg-orange-500'
    case 'danger':
      return 'bg-red-500'
    default:
      return 'bg-gray-400'
  }
}

function getStatusText(status: string) {
  switch (status) {
    case 'normal':
      return '正常'
    case 'warning':
      return '预警'
    case 'danger':
      return '危险'
    default:
      return '未知'
  }
}

// 入库操作
function addStockInItem() {
  stockInForm.value.items.push({ skuId: undefined, quantity: 1, remark: '' })
}

function removeStockInItem(index: number) {
  stockInForm.value.items.splice(index, 1)
}

function handleSkuSelect(_item: any) {
  // 可以根据选择的SKU填充一些信息
}

function handleStockInImagesChange(e: Event) {
  const target = e.target as HTMLInputElement
  stockInImages.value = target.files ? Array.from(target.files) : []
}

async function handleStockIn() {
  if (!stockInFormRef.value) return
  try {
    await stockInFormRef.value.validate()
  } catch {
    return
  }

  const validItems = stockInForm.value.items.filter(i => i.skuId && i.quantity > 0)
  if (validItems.length === 0) {
    ElMessage.warning('请添加有效的入库商品')
    return
  }

  submitLoading.value = true
  try {
    const imageIds: string[] = []
    for (const image of stockInImages.value) {
      const uploadRes = await uploadFile(image, 'inventory')
      imageIds.push(String(uploadRes.data.id))
    }
    const res = await stockIn({
      warehouseId: stockInForm.value.warehouseId!,
      items: validItems.map(i => ({
        skuId: i.skuId!,
        quantity: i.quantity,
        remark: i.remark
      })),
      remark: stockInForm.value.remark,
      images: imageIds
    })
    if (res.code === 200) {
      ElMessage.success('入库成功')
      showStockInDialog.value = false
      loadData()
      // 重置表单
      stockInForm.value = {
        warehouseId: undefined,
        items: [{ skuId: undefined, quantity: 1, remark: '' }],
        remark: ''
      }
      stockInImages.value = []
    } else {
      ElMessage.error(res.data.message || '入库失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '入库失败')
  } finally {
    submitLoading.value = false
  }
}

// 出库操作
function addStockOutItem() {
  stockOutForm.value.items.push({ skuId: undefined, quantity: 1, reason: '' })
}

function removeStockOutItem(index: number) {
  stockOutForm.value.items.splice(index, 1)
}

async function handleStockOut() {
  if (!stockOutFormRef.value) return
  try {
    await stockOutFormRef.value.validate()
  } catch {
    return
  }

  const validItems = stockOutForm.value.items.filter(i => i.skuId && i.quantity > 0)
  if (validItems.length === 0) {
    ElMessage.warning('请添加有效的出库商品')
    return
  }

  submitLoading.value = true
  try {
    const res = await stockOut({
      warehouseId: stockOutForm.value.warehouseId!,
      source: stockOutForm.value.source,
      items: validItems.map(i => ({
        skuId: i.skuId!,
        quantity: i.quantity,
        reason: i.reason
      })),
      remark: stockOutForm.value.remark
    })
    if (res.code === 200) {
      ElMessage.success('出库成功')
      showStockOutDialog.value = false
      loadData()
      // 重置表单
      stockOutForm.value = {
        warehouseId: undefined,
        source: 'OTHER',
        items: [{ skuId: undefined, quantity: 1, reason: '' }],
        remark: ''
      }
    } else {
      ElMessage.error(res.data.message || '出库失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '出库失败')
  } finally {
    submitLoading.value = false
  }
}

// 调整操作
function addAdjustItem() {
  adjustForm.value.items.push({ skuId: undefined, quantity: 0, reason: '' })
}

function removeAdjustItem(index: number) {
  adjustForm.value.items.splice(index, 1)
}

async function handleAdjust() {
  if (!adjustFormRef.value) return
  try {
    await adjustFormRef.value.validate()
  } catch {
    return
  }

  const validItems = adjustForm.value.items.filter(i => i.skuId && i.quantity !== 0)
  if (validItems.length === 0) {
    ElMessage.warning('请添加有效的调整商品')
    return
  }

  submitLoading.value = true
  try {
    const res = await adjustInventory({
      warehouseId: adjustForm.value.warehouseId!,
      reason: adjustForm.value.reason,
      items: validItems.map(i => ({
        skuId: i.skuId!,
        quantity: i.quantity
      })),
      remark: adjustForm.value.remark
    })
    if (res.code === 200) {
      ElMessage.success('调整成功')
      showAdjustDialog.value = false
      loadData()
      // 重置表单
      adjustForm.value = {
        warehouseId: undefined,
        reason: '',
        items: [{ skuId: undefined, quantity: 0, reason: '' }],
        remark: ''
      }
    } else {
      ElMessage.error(res.data.message || '调整失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '调整失败')
  } finally {
    submitLoading.value = false
  }
}

// 打开库存记录弹窗（全部记录）
function openLogDialog() {
  logFilter.value.skuId = undefined
  logFilter.value.changeType = undefined
  logCurrentPage.value = 1
  showLogDialog.value = true
  loadLogData()
}

// 查看库存记录
async function handleViewLog(row: InventoryVO) {
  logFilter.value.skuId = row.skuId
  logFilter.value.changeType = undefined
  showLogDialog.value = true
  loadLogData()
}

async function loadLogData() {
  logLoading.value = true
  try {
    const res = await getInventoryLogPage({
      current: logCurrentPage.value,
      size: logPageSize.value,
      skuId: logFilter.value.skuId,
      changeType: logFilter.value.changeType
    })
    if (res.code === 200) {
      logTableData.value = res.data.records
      logTotal.value = res.data.total
    }
  } catch (error) {
    console.error('加载库存记录失败:', error)
  } finally {
    logLoading.value = false
  }
}

// 变动类型样式
function getChangeTypeClass(type: string) {
  switch (type) {
    case 'PURCHASE_IN':
      return 'bg-green-100 text-green-600'
    case 'SALE_OUT':
      return 'bg-red-100 text-red-600'
    case 'ADJUST':
      return 'bg-blue-100 text-blue-600'
    case 'SALE_CANCEL':
      return 'bg-orange-100 text-orange-600'
    default:
      return 'bg-gray-100 text-gray-500'
  }
}

onMounted(() => {
  loadData()
  loadWarehouses()
  loadSkuOptions()
})
</script>

<style scoped>
.inventory-page {
  padding: 0;
}

.inventory-search-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.inventory-search-input :deep(.el-input__inner) {
  font-size: 14px !important;
  color: #1a1a2e !important;
}

.inventory-select {
  width: 100%;
}

.inventory-select :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.inventory-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.inventory-table :deep(.el-table__body td) {
  padding: 20px 24px !important;
}

.inventory-table :deep(.el-table__row:hover > td) {
  background-color: rgb(249 250 251 / 30%) !important;
}
</style>
