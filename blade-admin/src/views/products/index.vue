<template>
  <div class="products-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">商品列表</h2>
        <p class="text-gray-500 text-sm">管理您的服装商品信息。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3" @click="handleRefresh">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="handleCreate">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建商品
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="bg-white rounded-xl p-6 mb-6 shadow-sm flex flex-wrap items-center gap-6">
      <div class="w-[280px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">关键字搜索</label>
        <el-input
          v-model="searchQuery"
          placeholder="搜索商品名称/编码"
          class="product-search-input"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
          </template>
        </el-input>
      </div>

      <div class="flex-1 min-w-[160px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">商品分类</label>
        <el-select v-model="categoryFilter" placeholder="全部" class="product-select" clearable>
          <el-option label="全部分类" :value="undefined" />
          <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
        </el-select>
      </div>

      <div class="flex-1 min-w-[120px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">状态</label>
        <el-select v-model="statusFilter" placeholder="全部" class="product-select" clearable>
          <el-option label="全部" :value="undefined" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
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

    <!-- 商品表格 -->
    <div class="bg-white rounded-xl shadow-sm mb-6">
      <el-table :data="tableData" class="product-table" v-loading="loading" empty-text="暂无商品数据">
        <el-table-column label="商品名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-[#408aee]/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-[#408aee]">inventory_2</span>
              </div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.name }}</div>
                <div class="text-xs text-gray-400">{{ row.productCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="分类" min-width="100">
          <template #default="{ row }">
            <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-[#408aee]/10 text-[#408aee]">
              {{ row.categoryName || '未分类' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="进货价" min-width="90" align="right">
          <template #default="{ row }">
            <span class="text-sm text-gray-600">¥{{ row.costPrice || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="批发价" min-width="90" align="right">
          <template #default="{ row }">
            <span class="text-sm font-bold text-gray-900">¥{{ row.wholesalePrice || row.price || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="SKU数量" min-width="80" align="center">
          <template #default="{ row }">
            <span class="text-sm font-bold text-[#408aee]">{{ row.skus?.length || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="颜色/尺码" min-width="120">
          <template #default="{ row }">
            <div class="text-xs text-gray-500">
              <span v-if="row.colors?.length">颜色: {{ row.colors.length }}</span>
              <span v-if="row.colors?.length && row.sizes?.length"> / </span>
              <span v-if="row.sizes?.length">尺码: {{ row.sizes.length }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <span :class="row.status === 1 ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-500'" class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" min-width="100">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.createTime?.split('T')[0] }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="120" fixed="right" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-3">
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-[#408aee]" @click="handleEdit(row)">编辑</el-button>
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-red-500" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="flex items-center justify-between px-6 py-4 bg-gray-50/50">
        <p class="text-xs text-gray-500 font-medium">
          显示第 <span class="text-gray-900">{{ (currentPage - 1) * pageSize + 1 }}</span>-<span class="text-gray-900">{{ Math.min(currentPage * pageSize, total) }}</span> 条，共 <span class="text-gray-900">{{ total }}</span> 条商品
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

    <!-- 商品编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新建商品'"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px" class="product-form">
        <div class="grid grid-cols-2 gap-x-6">
          <el-form-item label="商品编码" prop="productCode">
            <el-input v-model="form.productCode" placeholder="如: P001" :disabled="isEdit" />
          </el-form-item>

          <el-form-item label="商品名称" prop="name">
            <el-input v-model="form.name" placeholder="商品名称" />
          </el-form-item>

          <el-form-item label="商品分类">
            <el-select v-model="form.categoryId" placeholder="选择分类" class="w-full" clearable>
              <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="单位">
            <el-input v-model="form.unit" placeholder="如: 件/套" />
          </el-form-item>

          <el-form-item label="进货价">
            <el-input-number v-model="form.costPrice" :min="0" :precision="2" :controls="false" class="w-full" placeholder="成本价" />
          </el-form-item>

          <el-form-item label="批发价">
            <el-input-number v-model="form.wholesalePrice" :min="0" :precision="2" :controls="false" class="w-full" placeholder="批发价" />
          </el-form-item>

          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>

        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="商品描述" />
        </el-form-item>

        <div class="grid grid-cols-2 gap-x-6">
          <el-form-item label="重量(kg)">
            <el-input-number v-model="form.weight" :min="0" :precision="2" :controls="false" class="w-full" placeholder="用于运费计算" />
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="备注信息" />
          </el-form-item>
        </div>

        <el-form-item label="颜色选择">
          <el-checkbox-group v-model="form.colorIds">
            <el-checkbox v-for="color in colorOptions" :key="color.id" :value="color.id" :label="color.id">
              {{ color.colorName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="尺码选择">
          <el-checkbox-group v-model="form.sizeIds">
            <el-checkbox v-for="size in sizeOptions" :key="size.id" :value="size.id" :label="size.id">
              {{ size.sizeCode }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <!-- SKU 预览 -->
        <el-form-item label="SKU 预览" v-if="form.colorIds.length && form.sizeIds.length">
          <div class="bg-gray-50 rounded-lg p-4 w-full">
            <p class="text-xs text-gray-500 mb-2">
              将生成 {{ form.colorIds.length * form.sizeIds.length }} 个 SKU：
              颜色({{ form.colorIds.length }}) × 尺码({{ form.sizeIds.length }})
            </p>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="(sku, index) in previewSkus"
                :key="index"
                class="inline-flex items-center px-2 py-1 bg-white border border-gray-200 rounded text-xs"
              >
                {{ sku }}
              </span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductPage, getProductById, createProduct, updateProduct, deleteProduct, getAllColors, getAllSizes, getAllCategories, type ProductVO, type ProductColor, type ProductSize, type ProductCreateDTO, type ProductCategory } from '@/api/product'

const searchQuery = ref('')
const categoryFilter = ref<number | undefined>(undefined)
const statusFilter = ref<number | undefined>(undefined)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

const tableData = ref<ProductVO[]>([])

// 弹窗相关
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const editingId = ref<number | null>(null)

// 颜色和尺码选项
const colorOptions = ref<ProductColor[]>([])
const sizeOptions = ref<ProductSize[]>([])
const categoryOptions = ref<ProductCategory[]>([])

// 表单
const form = ref<{
  productCode: string
  name: string
  categoryId: number | undefined
  unit: string
  costPrice: number | undefined
  wholesalePrice: number | undefined
  weight: number | undefined
  status: number
  description: string
  remark: string
  colorIds: number[]
  sizeIds: number[]
}>({
  productCode: '',
  name: '',
  categoryId: undefined,
  unit: '件',
  costPrice: undefined,
  wholesalePrice: undefined,
  weight: undefined,
  status: 1,
  description: '',
  remark: '',
  colorIds: [],
  sizeIds: []
})

const formRules = {
  productCode: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  colorIds: [{ required: true, message: '请至少选择一个颜色', trigger: 'change', type: 'array', min: 1 }],
  sizeIds: [{ required: true, message: '请至少选择一个尺码', trigger: 'change', type: 'array', min: 1 }]
}

// SKU 预览
const previewSkus = computed(() => {
  const skus: string[] = []
  const selectedColors = colorOptions.value.filter(c => form.value.colorIds.includes(c.id))
  const selectedSizes = sizeOptions.value.filter(s => form.value.sizeIds.includes(s.id))
  for (const color of selectedColors) {
    for (const size of selectedSizes) {
      skus.push(`${form.value.productCode || 'CODE'}-${color.colorCode}-${size.sizeCode}`)
    }
  }
  return skus.slice(0, 20) // 最多显示20个
})

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const res = await getProductPage({
      current: currentPage.value,
      size: pageSize.value,
      keyword: searchQuery.value || undefined,
      categoryId: categoryFilter.value,
      status: statusFilter.value
    })
    if (res.code === 200) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    console.error('加载商品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载颜色和尺码选项
async function loadOptions() {
  try {
    const [colorsRes, sizesRes, categoriesRes] = await Promise.all([
      getAllColors(),
      getAllSizes(),
      getAllCategories()
    ])
    if (colorsRes.code === 200) {
      colorOptions.value = colorsRes.data
    }
    if (sizesRes.code === 200) {
      sizeOptions.value = sizesRes.data.sort((a: ProductSize, b: ProductSize) => a.sort - b.sort)
    }
    if (categoriesRes.code === 200) {
      categoryOptions.value = categoriesRes.data
    }
  } catch (error) {
    console.error('加载选项失败:', error)
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
  categoryFilter.value = undefined
  statusFilter.value = undefined
  currentPage.value = 1
  loadData()
}

// 刷新
function handleRefresh() {
  loadData()
}

// 新建
function handleCreate() {
  isEdit.value = false
  editingId.value = null
  form.value = {
    productCode: '',
    name: '',
    categoryId: undefined,
    unit: '件',
    costPrice: undefined,
    wholesalePrice: undefined,
    weight: undefined,
    status: 1,
    description: '',
    remark: '',
    colorIds: [],
    sizeIds: []
  }
  dialogVisible.value = true
}

// 编辑
async function handleEdit(row: ProductVO) {
  isEdit.value = true
  editingId.value = row.id
  try {
    const res = await getProductById(row.id)
    if (res.code === 200) {
      const product = res.data
      form.value = {
        productCode: product.productCode,
        name: product.name,
        categoryId: product.categoryId,
        unit: product.unit,
        costPrice: product.costPrice,
        wholesalePrice: product.wholesalePrice,
        weight: product.weight,
        status: product.status,
        description: product.description || '',
        remark: product.remark || '',
        colorIds: product.colors?.map((c: ProductColor) => c.id) || [],
        sizeIds: product.sizes?.map((s: ProductSize) => s.id) || []
      }
      dialogVisible.value = true
    }
  } catch (error) {
    console.error('加载商品详情失败:', error)
  }
}

// 提交
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    const data: ProductCreateDTO = {
      productCode: form.value.productCode,
      name: form.value.name,
      categoryId: form.value.categoryId,
      unit: form.value.unit,
      costPrice: form.value.costPrice,
      wholesalePrice: form.value.wholesalePrice,
      weight: form.value.weight,
      status: form.value.status,
      description: form.value.description,
      remark: form.value.remark,
      colorIds: form.value.colorIds,
      sizeIds: form.value.sizeIds
    }

    let res
    if (isEdit.value && editingId.value) {
      res = await updateProduct({ ...data, id: editingId.value })
    } else {
      res = await createProduct(data)
    }

    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

// 删除
async function handleDelete(row: ProductVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品「${row.name}」吗？删除后无法恢复。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const res = await deleteProduct(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped>
.products-page {
  padding: 0;
}

.product-table {
  width: 100%;
  overflow-x: auto;
}

.product-table :deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

.product-search-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.product-search-input :deep(.el-input__inner) {
  font-size: 14px !important;
  color: #1a1a2e !important;
}

.product-select {
  width: 100%;
}

.product-select :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.product-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.product-table :deep(.el-table__body td) {
  padding: 20px 24px !important;
}

.product-table :deep(.el-table__row:hover > td) {
  background-color: rgb(249 250 251 / 30%) !important;
}

.product-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #374151;
}

.product-form :deep(.el-input__wrapper),
.product-form :deep(.el-textarea__inner) {
  background-color: rgb(255 255 255) !important;
  border-radius: 8px !important;
  border: 1px solid #e5e7eb !important;
}

.product-form :deep(.el-input__inner),
.product-form :deep(.el-textarea__inner) {
  color: #1a1a2e !important;
}

/* 分页样式 */
::deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #408aee !important;
}
</style>
