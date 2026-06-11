<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="文件绑定到商品"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="space-y-5">
      <!-- 绑定文件列表摘要 -->
      <div class="bg-gray-50 rounded-lg p-3 text-xs text-gray-600">
        <span class="font-medium text-gray-800">绑定文件：</span>
        <span>{{ fileIds.length }} 个文件 (ID: {{ fileIds.join(', ') }})</span>
      </div>

      <!-- Step 1: 搜索商品 -->
      <div>
        <label class="block text-xs font-bold text-gray-700 mb-2">选择商品</label>
        <el-select
          v-model="selectedProductId"
          filterable
          remote
          :remote-method="searchProducts"
          :loading="productSearchLoading"
          placeholder="输入商品名称或编号搜索"
          class="w-full"
          clearable
          @change="onProductChange"
        >
          <el-option
            v-for="p in productOptions"
            :key="p.id"
            :label="`${p.productCode} - ${p.name}`"
            :value="p.id"
          >
            <div class="flex items-center justify-between">
              <span class="font-medium">{{ p.productCode }}</span>
              <span class="text-xs text-gray-400 ml-2">{{ p.name }}</span>
            </div>
          </el-option>
        </el-select>
      </div>

      <!-- Step 2: 选择绑定角色 -->
      <div v-if="selectedProductId">
        <label class="block text-xs font-bold text-gray-700 mb-2">绑定角色</label>
        <el-radio-group v-model="bindRole" class="flex flex-col gap-2">
          <el-radio value="main" class="!mr-0">
            <div>
              <span class="text-sm font-medium">商品主图</span>
              <span class="text-[11px] text-gray-400 ml-2">仅第一个文件生效</span>
            </div>
          </el-radio>
          <el-radio value="gallery" class="!mr-0">
            <div>
              <span class="text-sm font-medium">商品图集</span>
              <span class="text-[11px] text-gray-400 ml-2">所有文件加入图集</span>
            </div>
          </el-radio>
          <el-radio value="sku_image" class="!mr-0">
            <div>
              <span class="text-sm font-medium">SKU 图片</span>
              <span class="text-[11px] text-gray-400 ml-2">指定每个文件归属的 SKU</span>
            </div>
          </el-radio>
        </el-radio-group>
      </div>

      <!-- Step 3: SKU 选择（仅 sku_image 角色） -->
      <div v-if="bindRole === 'sku_image' && selectedProductId">
        <label class="block text-xs font-bold text-gray-700 mb-2">选择目标 SKU</label>
        <div v-if="productSkus.length === 0" class="text-xs text-gray-400 py-3">
          该商品暂无 SKU
        </div>
        <div v-else class="space-y-2 max-h-[200px] overflow-y-auto">
          <div
            v-for="sku in productSkus"
            :key="sku.id"
            class="flex items-center gap-3 bg-gray-50 rounded-lg px-3 py-2"
          >
            <el-checkbox
              :model-value="selectedSkuIds.includes(sku.id)"
              @change="(val: boolean) => toggleSku(sku.id, val)"
            />
            <div class="flex-1">
              <span class="text-sm font-medium text-gray-800">{{ sku.skuCode }}</span>
              <span class="text-xs text-gray-400 ml-2">{{ sku.colorName }} / {{ sku.sizeName }}</span>
            </div>
            <span class="text-xs text-gray-400">¥{{ sku.price }}</span>
          </div>
        </div>

        <!-- 已选 SKU 的文件分配提示 -->
        <div v-if="selectedSkuIds.length > 0" class="mt-3 bg-blue-50 border border-blue-100 rounded-lg p-3 text-xs text-blue-700">
          <span class="material-symbols-outlined text-sm mr-1 align-middle">info</span>
          {{ fileIds.length }} 个文件将被绑定到 {{ selectedSkuIds.length }} 个已选 SKU
          （每个 SKU 获得全部 {{ fileIds.length }} 个文件）
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :disabled="!canConfirm || binding"
        @click="handleBind"
      >
        {{ binding ? '绑定中...' : '确认绑定' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  getProductPage,
  getProductById,
  setProductFileBindings,
  type ProductVO,
  type ProductSku,
  type ProductFileBindingDTO,
} from '@/api/product'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  fileIds: number[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

// 商品搜索
const productSearchLoading = ref(false)
const productOptions = ref<ProductVO[]>([])
const selectedProductId = ref<number | null>(null)
const productSkus = ref<ProductSku[]>([])

// 绑定角色
const bindRole = ref<'main' | 'gallery' | 'sku_image'>('main')
const selectedSkuIds = ref<number[]>([])

// 提交状态
const binding = ref(false)

const canConfirm = computed(() => {
  if (!selectedProductId.value) return false
  if (bindRole.value === 'sku_image' && selectedSkuIds.value.length === 0) return false
  return true
})

async function searchProducts(query: string) {
  if (!query || query.length < 1) {
    productOptions.value = []
    return
  }
  productSearchLoading.value = true
  try {
    const res = await getProductPage({ keyword: query, size: 20 })
    productOptions.value = (res as any).data?.records || []
  } catch {
    productOptions.value = []
  } finally {
    productSearchLoading.value = false
  }
}

async function onProductChange(productId: number | null) {
  selectedSkuIds.value = []
  productSkus.value = []

  if (!productId) return

  try {
    const res = await getProductById(productId)
    const product = (res as any).data
    if (product && product.skus) {
      productSkus.value = product.skus.filter((s: ProductSku) => s.status === 1)
    }
  } catch {
    productSkus.value = []
  }
}

function toggleSku(skuId: number, checked: boolean) {
  if (checked) {
    selectedSkuIds.value = [...selectedSkuIds.value, skuId]
  } else {
    selectedSkuIds.value = selectedSkuIds.value.filter(id => id !== skuId)
  }
}

async function handleBind() {
  if (!canConfirm.value || !selectedProductId.value) return

  binding.value = true
  try {
    const dto: ProductFileBindingDTO = {}

    if (bindRole.value === 'main') {
      dto.mainFileId = props.fileIds[0] || null
    } else if (bindRole.value === 'gallery') {
      dto.galleryFileIds = [...props.fileIds]
    } else if (bindRole.value === 'sku_image') {
      dto.skuImageBindings = selectedSkuIds.value.map(skuId => ({
        skuId,
        fileIds: [...props.fileIds],
      }))
    }

    await setProductFileBindings(selectedProductId.value, dto)
    ElMessage.success('文件绑定成功')
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '绑定失败')
  } finally {
    binding.value = false
  }
}
</script>
