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
          <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
        </el-select>
      </div>

      <div class="flex-1 min-w-[120px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">状态</label>
        <el-select v-model="statusFilter" placeholder="全部" class="product-select" clearable>
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
              <img
                v-if="getProductImage(row.imageUrl)"
                :src="getProductImage(row.imageUrl)"
                alt=""
                class="w-10 h-10 rounded-lg object-cover border border-gray-200"
              />
              <div v-else class="w-10 h-10 rounded-lg bg-[#408aee]/10 flex items-center justify-center">
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

    <!-- 商品编辑弹窗（v2 分区Tab） -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新建商品'"
      width="1100px"
      :close-on-click-modal="false"
      @opened="onDialogOpened"
    >
      <el-tabs v-model="activeTab" type="border-card" class="product-edit-tabs">
        <!-- Tab 1: 基础信息 -->
        <el-tab-pane label="基础信息" name="basic">
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

              <el-form-item label="重量(kg)">
                <el-input-number v-model="form.weight" :min="0" :precision="2" :controls="false" class="w-full" placeholder="用于运费计算" />
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

            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="备注信息" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 颜色尺码 -->
        <el-tab-pane label="颜色尺码" name="colorsize">
          <el-form label-width="100px" class="product-form">
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
        </el-tab-pane>

        <!-- Tab 3: SKU 明细（仅编辑时显示） -->
        <el-tab-pane label="SKU 明细" name="skus" v-if="isEdit">
          <div v-if="skuEditList.length === 0" class="text-center py-12 text-gray-400">
            <span class="material-symbols-outlined text-4xl mb-2 block">inventory_2</span>
            <p>该商品暂无 SKU</p>
          </div>
          <div v-else>
            <div class="flex items-center justify-between mb-3">
              <p class="text-xs text-gray-500">
                共 <span class="font-bold text-gray-900">{{ skuEditList.length }}</span> 个 SKU，
                修改后请点击「保存 SKU 修改」
              </p>
              <el-button size="small" type="primary" :disabled="!hasSkuChanges" :loading="skuSaving" @click="saveSkuChanges">
                保存 SKU 修改
              </el-button>
            </div>
            <div class="sku-table-wrapper">
              <el-table :data="skuEditList" class="sku-table" size="small" max-height="360">
                <el-table-column label="SKU 编码" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="text-xs font-mono text-gray-700">{{ row.skuCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="颜色" width="80">
                  <template #default="{ row }">
                    <span class="text-xs">{{ row.colorName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="尺码" width="70">
                  <template #default="{ row }">
                    <span class="text-xs">{{ row.sizeName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="售价" width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.price"
                      :min="0"
                      :precision="2"
                      :controls="false"
                      size="small"
                      class="sku-inline-input"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="成本价" width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.costPrice"
                      :min="0"
                      :precision="2"
                      :controls="false"
                      size="small"
                      class="sku-inline-input"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="条码" width="140">
                  <template #default="{ row }">
                    <el-input
                      v-model="row.barCode"
                      size="small"
                      placeholder="条形码"
                      class="sku-inline-input"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80" align="center">
                  <template #default="{ row }">
                    <el-switch
                      v-model="row.status"
                      :active-value="1"
                      :inactive-value="0"
                      size="small"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 4: 商品素材（仅编辑时显示） -->
        <el-tab-pane label="商品素材" name="materials" v-if="isEdit">
          <div v-loading="fileBindingsLoading" class="materials-content">
            <!-- 商品主图 -->
            <div class="mb-6">
              <h4 class="text-sm font-bold text-gray-700 mb-3">商品主图</h4>
              <div class="flex items-start gap-4">
                <img
                  v-if="mainImagePreview"
                  :src="mainImagePreview"
                  alt="商品主图"
                  class="w-24 h-24 rounded-lg object-cover border border-gray-200"
                />
                <div v-else class="w-24 h-24 rounded-lg bg-gray-100 flex items-center justify-center">
                  <span class="material-symbols-outlined text-gray-400 text-2xl">image</span>
                </div>
                <div class="flex-1 space-y-2">
                  <div class="flex items-center gap-2">
                    <input type="file" accept="image/*" class="text-xs text-gray-500" @change="handleMainImageUpload" />
                  </div>
                  <p class="text-xs text-gray-400">上传自动绑定为主图，或下方输入已有 fileId</p>
                  <div class="flex items-center gap-2">
                    <el-input v-model="mainFileIdInput" size="small" placeholder="输入 fileId" class="!w-40" />
                    <el-button size="small" @click="applyMainFileId">设置</el-button>
                    <el-button v-if="mainImagePreview" size="small" type="danger" plain @click="clearMainImage">移除</el-button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 商品图集 -->
            <div class="mb-6">
              <h4 class="text-sm font-bold text-gray-700 mb-3">
                商品图集
                <span class="text-xs text-gray-400 font-normal ml-2">（共 {{ galleryImages.length }} 张）</span>
              </h4>
              <div class="flex flex-wrap gap-3 mb-3">
                <div
                  v-for="(img, idx) in galleryImages"
                  :key="idx"
                  class="relative w-20 h-20 rounded-lg border border-gray-200 overflow-hidden group"
                >
                  <img :src="img.previewUrl" alt="" class="w-full h-full object-cover" />
                  <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                    <el-button type="danger" size="small" circle @click="removeGalleryImage(idx)">
                      <span class="material-symbols-outlined text-sm">close</span>
                    </el-button>
                  </div>
                </div>
                <div v-if="galleryImages.length === 0" class="text-xs text-gray-400 py-4">暂无图集图片</div>
              </div>
              <div class="flex items-center gap-2">
                <input type="file" accept="image/*" multiple class="text-xs text-gray-500" @change="handleGalleryUpload" />
                <span class="text-xs text-gray-400">或</span>
                <el-input v-model="galleryFileIdInput" size="small" placeholder="输入 fileId" class="!w-40" />
                <el-button size="small" @click="addGalleryFileId">添加</el-button>
              </div>
            </div>

            <!-- SKU 图片 -->
            <div class="mb-6">
              <h4 class="text-sm font-bold text-gray-700 mb-3">SKU 图片</h4>
              <div v-if="skuEditList.length === 0" class="text-xs text-gray-400 py-4">暂无 SKU</div>
              <div v-else class="space-y-2">
                <div v-for="sku in skuEditList" :key="sku.id" class="flex items-center gap-3 p-2 bg-gray-50 rounded-lg">
                  <div class="w-28 flex-shrink-0">
                    <span class="text-xs font-bold text-gray-700 block truncate">{{ sku.skuCode }}</span>
                    <span class="text-[10px] text-gray-400">{{ sku.colorName }}/{{ sku.sizeName }}</span>
                  </div>
                  <div class="flex items-center gap-1 flex-1 min-w-0 overflow-x-auto">
                    <img
                      v-for="(f, idx) in (skuImageFiles[sku.id] || [])"
                      :key="f.fileId"
                      :src="f.previewUrl"
                      alt=""
                      class="w-10 h-10 rounded object-cover border border-gray-200 flex-shrink-0 cursor-pointer hover:opacity-70 hover:border-red-300"
                      :title="'fileId: ' + f.fileId + '（点击移除）'"
                      @click="removeSkuImage(sku.id, idx)"
                    />
                    <span v-if="!(skuImageFiles[sku.id] || []).length" class="text-[10px] text-gray-400 flex-shrink-0">无图</span>
                  </div>
                  <div class="flex items-center gap-1.5 flex-shrink-0">
                    <input
                      type="file"
                      accept="image/*"
                      class="hidden"
                      :id="'sku-img-upload-' + sku.id"
                      @change="e => handleSkuImageUpload(sku.id, e)"
                    />
                    <label
                      :for="'sku-img-upload-' + sku.id"
                      class="cursor-pointer text-[10px] px-2 py-1 bg-white border border-gray-200 rounded text-gray-500 hover:text-[#408aee] hover:border-[#408aee] inline-block leading-none"
                    >
                      上传
                    </label>
                    <el-input
                      v-model="skuFileIdInputs[sku.id]"
                      size="small"
                      placeholder="fileId"
                      class="!w-20"
                      @keyup.enter="addSkuImageByFileId(sku.id)"
                    />
                    <el-button size="small" class="!text-[10px] !px-2" @click="addSkuImageByFileId(sku.id)">添加</el-button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 素材保存按钮 -->
            <div class="border-t pt-4">
              <el-button type="primary" :loading="materialSaving" @click="saveFileBindings">保存素材绑定</el-button>
              <span class="text-xs text-gray-400 ml-3">素材修改独立保存，不影响基础信息和颜色尺码</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

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
import { getProductPage, getProductById, createProduct, updateProduct, deleteProduct, getAllColors, getAllSizes, getAllCategories, getProductFileBindings, setProductFileBindings, batchUpdateSkus, type ProductVO, type ProductColor, type ProductSize, type ProductSku, type ProductCreateDTO, type ProductCategory, type FileBindingItem, type SkuImageBindingDTO, type SkuUpdateDTO } from '@/api/product'
import { parseImageSources, uploadFile, filePreviewUrl } from '@/api/file'

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
  imageUrl: string
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
  imageUrl: '',
  remark: '',
  colorIds: [],
  sizeIds: []
})

// v2 编辑 Tab 状态
const activeTab = ref('basic')

// SKU 明细状态
const skuEditList = ref<(ProductSku & { _original: string })[]>([])
const skuDirtyIds = ref(new Set<number>())
const skuSaving = ref(false)
const hasSkuChanges = ref(false)

// 商品素材状态
const fileBindingsLoading = ref(false)
const materialSaving = ref(false)
const mainImagePreview = ref('')
const mainFileIdInput = ref('')
const galleryImages = ref<FileBindingItem[]>([])
const galleryFileIdInput = ref('')
const skuImageFiles = ref<Record<number, FileBindingItem[]>>({})
const skuFileIdInputs = ref<Record<number, string>>({})

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
  activeTab.value = 'basic'
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
    imageUrl: '',
    remark: '',
    colorIds: [],
    sizeIds: []
  }
  // 重置 v2 状态
  skuEditList.value = []
  skuDirtyIds.value = new Set()
  hasSkuChanges.value = false
  mainImagePreview.value = ''
  mainFileIdInput.value = ''
  galleryImages.value = []
  skuImageFiles.value = {}
  skuFileIdInputs.value = {}
  dialogVisible.value = true
}

// 编辑
async function handleEdit(row: ProductVO) {
  isEdit.value = true
  editingId.value = row.id
  activeTab.value = 'basic'
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
        imageUrl: product.imageUrl || '',
        remark: product.remark || '',
        colorIds: product.colors?.map((c: ProductColor) => c.id) || [],
        sizeIds: product.sizes?.map((s: ProductSize) => s.id) || []
      }
      // 初始化 SKU 明细编辑数据
      initSkuEditList(product.skus || [])
      dialogVisible.value = true
    }
  } catch (error) {
    console.error('加载商品详情失败:', error)
  }
}

// 弹窗打开后加载素材
function onDialogOpened() {
  if (isEdit.value && editingId.value) {
    loadFileBindings()
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
      imageUrl: form.value.imageUrl || undefined,
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

function getProductImage(imageUrl?: string) {
  return parseImageSources(imageUrl)[0] || ''
}

onMounted(() => {
  loadData()
  loadOptions()
})

// 删除
async function handleDelete(row: ProductVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品「${row.name}」吗？若商品存在订单、库存等业务引用则无法删除，建议改为禁用。`,
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
      // 显示后端引用保护消息，并建议禁用
      ElMessageBox.alert(
        res.message || '删除失败，可能存在业务引用',
        '无法删除',
        {
          confirmButtonText: '知道了',
          type: 'warning'
        }
      )
    }
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessageBox.alert(
        error?.message || error?.response?.data?.message || '删除失败，可能存在业务引用',
        '无法删除',
        { confirmButtonText: '知道了', type: 'warning' }
      )
    }
  }
}

// ==================== v2: SKU 明细 ====================

function initSkuEditList(skus: ProductSku[]) {
  skuEditList.value = skus.map(s => ({
    ...s,
    _original: JSON.stringify({ price: s.price, costPrice: s.costPrice, barCode: s.barCode, status: s.status })
  }))
  skuDirtyIds.value = new Set()
  hasSkuChanges.value = false
}

function markSkuDirty(skuId: number) {
  const sku = skuEditList.value.find(s => s.id === skuId)
  if (!sku) return
  const original = JSON.parse(sku._original)
  const changed =
    original.price !== sku.price ||
    original.costPrice !== sku.costPrice ||
    original.barCode !== sku.barCode ||
    original.status !== sku.status
  if (changed) {
    skuDirtyIds.value.add(skuId)
  } else {
    skuDirtyIds.value.delete(skuId)
  }
  hasSkuChanges.value = skuDirtyIds.value.size > 0
}

async function saveSkuChanges() {
  if (skuDirtyIds.value.size === 0) return
  skuSaving.value = true
  try {
    const updates: SkuUpdateDTO[] = []
    for (const sku of skuEditList.value) {
      if (skuDirtyIds.value.has(sku.id)) {
        updates.push({
          id: sku.id,
          price: sku.price,
          costPrice: sku.costPrice,
          barCode: sku.barCode || undefined,
          status: sku.status
        })
      }
    }
    const res = await batchUpdateSkus(updates)
    if (res.code === 200) {
      ElMessage.success(`已保存 ${updates.length} 个 SKU 修改`)
      // 更新原始快照
      for (const sku of skuEditList.value) {
        if (skuDirtyIds.value.has(sku.id)) {
          sku._original = JSON.stringify({ price: sku.price, costPrice: sku.costPrice, barCode: sku.barCode, status: sku.status })
        }
      }
      skuDirtyIds.value = new Set()
      hasSkuChanges.value = false
    } else {
      ElMessage.error(res.message || 'SKU 保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || 'SKU 保存失败')
  } finally {
    skuSaving.value = false
  }
}

// ==================== v2: 商品素材 ====================

async function loadFileBindings() {
  if (!editingId.value) return
  fileBindingsLoading.value = true
  try {
    // Initialize per-SKU structures for all SKUs in skuEditList
    const files: Record<number, FileBindingItem[]> = {}
    const inputs: Record<number, string> = {}
    for (const sku of skuEditList.value) {
      files[sku.id] = []
      inputs[sku.id] = ''
    }

    const res = await getProductFileBindings(editingId.value)
    if (res.code === 200) {
      const data = res.data
      mainImagePreview.value = data.main?.previewUrl || ''
      mainFileIdInput.value = data.main?.fileId ? String(data.main.fileId) : ''
      galleryImages.value = data.gallery || []
      // Fill SKU image files from backend response
      for (const group of (data.skuImages || [])) {
        files[group.skuId] = group.files || []
      }
    }
    skuImageFiles.value = files
    skuFileIdInputs.value = inputs
  } catch (error) {
    console.error('加载素材绑定失败:', error)
  } finally {
    fileBindingsLoading.value = false
  }
}

async function handleMainImageUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !editingId.value) return
  try {
    const res = await uploadFile(file, 'product', editingId.value)
    const fileId = res.data.id as number
    // 直接设置为主图
    mainFileIdInput.value = String(fileId)
    mainImagePreview.value = filePreviewUrl(fileId)
    ElMessage.success('主图上传成功，请点击「保存素材绑定」生效')
  } catch (error: any) {
    ElMessage.error(error?.message || '图片上传失败')
  } finally {
    target.value = ''
  }
}

async function applyMainFileId() {
  const id = parseInt(mainFileIdInput.value)
  if (!id || isNaN(id)) {
    ElMessage.warning('请输入有效的 fileId')
    return
  }
  mainImagePreview.value = filePreviewUrl(id)
  ElMessage.success('主图已设置，请点击「保存素材绑定」生效')
}

function clearMainImage() {
  mainImagePreview.value = ''
  mainFileIdInput.value = ''
}

async function handleGalleryUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files
  if (!files || !editingId.value) return
  try {
    for (const file of Array.from(files)) {
      const res = await uploadFile(file, 'product', editingId.value)
      const fileId = res.data.id as number
      galleryImages.value.push({
        fileId,
        previewUrl: filePreviewUrl(fileId),
        sort: galleryImages.value.length,
        isPrimary: 0
      })
    }
    ElMessage.success(`已上传 ${files.length} 张图集图片，请点击「保存素材绑定」生效`)
  } catch (error: any) {
    ElMessage.error(error?.message || '图集上传失败')
  } finally {
    target.value = ''
  }
}

async function addGalleryFileId() {
  const id = parseInt(galleryFileIdInput.value)
  if (!id || isNaN(id)) {
    ElMessage.warning('请输入有效的 fileId')
    return
  }
  // 避免重复
  if (galleryImages.value.some(g => g.fileId === id)) {
    ElMessage.warning('该 fileId 已在图集中')
    return
  }
  galleryImages.value.push({
    fileId: id,
    previewUrl: filePreviewUrl(id),
    sort: galleryImages.value.length,
    isPrimary: 0
  })
  galleryFileIdInput.value = ''
}

function removeGalleryImage(index: number) {
  galleryImages.value.splice(index, 1)
}

async function saveFileBindings() {
  if (!editingId.value) return
  materialSaving.value = true
  try {
    const mainId = mainFileIdInput.value ? parseInt(mainFileIdInput.value) : null
    const skuImageBindings: SkuImageBindingDTO[] = skuEditList.value.map(sku => ({
      skuId: sku.id,
      fileIds: (skuImageFiles.value[sku.id] || []).map(f => f.fileId)
    }))
    const res = await setProductFileBindings(editingId.value, {
      mainFileId: mainId,
      galleryFileIds: galleryImages.value.map(g => g.fileId),
      skuImageBindings
    })
    if (res.code === 200) {
      ElMessage.success('素材绑定保存成功')
      // 同步更新 form.imageUrl
      if (mainId) {
        form.value.imageUrl = String(mainId)
      }
      loadFileBindings()
    } else {
      ElMessage.error(res.message || '素材保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '素材保存失败')
  } finally {
    materialSaving.value = false
  }
}

// SKU 图片管理辅助函数
async function handleSkuImageUpload(skuId: number, e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !editingId.value) return
  try {
    const res = await uploadFile(file, 'product', editingId.value)
    const fileId = res.data.id as number
    if (!skuImageFiles.value[skuId]) {
      skuImageFiles.value[skuId] = []
    }
    skuImageFiles.value[skuId].push({
      fileId,
      previewUrl: filePreviewUrl(fileId),
      sort: skuImageFiles.value[skuId].length,
      isPrimary: 0
    })
    ElMessage.success('SKU 图片上传成功，请点击「保存素材绑定」生效')
  } catch (error: any) {
    ElMessage.error(error?.message || 'SKU 图片上传失败')
  } finally {
    target.value = ''
  }
}

async function addSkuImageByFileId(skuId: number) {
  const inputVal = skuFileIdInputs.value[skuId]
  if (!inputVal) return
  const id = parseInt(inputVal)
  if (!id || isNaN(id)) {
    ElMessage.warning('请输入有效的 fileId')
    return
  }
  if (!skuImageFiles.value[skuId]) {
    skuImageFiles.value[skuId] = []
  }
  if (skuImageFiles.value[skuId].some(f => f.fileId === id)) {
    ElMessage.warning('该 fileId 已在该 SKU 图片中')
    return
  }
  skuImageFiles.value[skuId].push({
    fileId: id,
    previewUrl: filePreviewUrl(id),
    sort: skuImageFiles.value[skuId].length,
    isPrimary: 0
  })
  skuFileIdInputs.value[skuId] = ''
}

function removeSkuImage(skuId: number, index: number) {
  if (skuImageFiles.value[skuId]) {
    skuImageFiles.value[skuId].splice(index, 1)
  }
}
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

/* v2 Tabs 编辑页样式 */
.product-edit-tabs {
  min-height: 420px;
}

.product-edit-tabs :deep(.el-tabs__content) {
  padding: 16px 0 0;
  max-height: 480px;
  overflow-y: auto;
}

.product-edit-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

/* SKU 明细表格 */
.sku-table-wrapper {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.sku-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251) !important;
  font-size: 11px !important;
  font-weight: 700 !important;
  color: rgb(107 114 128) !important;
  padding: 10px 12px !important;
}

.sku-table :deep(.el-table__body td) {
  padding: 6px 12px !important;
}

.sku-inline-input {
  width: 100%;
}

.sku-inline-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 6px !important;
}

/* 素材内容区 */
.materials-content {
  min-height: 300px;
}
</style>
