<template>
  <div class="quick-order-page space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">快速录单</h2>
        <p class="text-sm text-gray-500">按纸质单据逐张录入，保存后进入标准订单流程。</p>
      </div>
      <div class="flex flex-wrap gap-3">
        <el-button class="!rounded-xl !font-bold" @click="router.push('/orders')">
          <span class="material-symbols-outlined text-sm mr-1">arrow_back</span>
          返回订单
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !rounded-xl !font-bold" :loading="saving" @click="submit(false)">
          <span class="material-symbols-outlined text-sm mr-1">save</span>
          保存
        </el-button>
        <el-button type="success" class="!rounded-xl !font-bold" :loading="saving" @click="submit(true)">
          <span class="material-symbols-outlined text-sm mr-1">playlist_add</span>
          保存并录下一单
        </el-button>
      </div>
    </div>

    <div class="grid grid-cols-12 gap-6 items-start">
      <section class="col-span-12 space-y-6">
        <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">receipt_long</span>
              <h3>单据信息</h3>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
              <label class="field-block">
                <span>纸质单号</span>
                <el-input v-model="form.sourceDocNo" placeholder="如 6月-001" />
              </label>
              <label class="field-block">
                <span>订单日期</span>
                <el-date-picker v-model="form.orderDate" value-format="YYYY-MM-DD" type="date" class="!w-full" />
              </label>
              <label class="field-block">
                <span>订单类型</span>
                <el-segmented v-model="form.orderType" :options="orderTypeOptions" class="quick-segmented" />
              </label>
              <label class="field-block">
                <span>来源档口/店铺</span>
                <el-input v-model="form.sourceShop" placeholder="如 杭州四季青A档、线上店铺" clearable />
              </label>
            </div>
          </section>

          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">person_search</span>
              <h3>客户信息</h3>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
              <div class="field-block">
                <span>国家区号</span>
                <CountryCodeSelect v-model="form.countryCode" placeholder="选择区号" class="!w-full" />
              </div>
              <label class="field-block">
                <span>客户电话</span>
                <el-input v-model="form.customerPhone" placeholder="输入后自动匹配客户" clearable @blur="searchCustomer" />
              </label>
              <label class="field-block">
                <span>客户名称</span>
                <el-autocomplete
                  v-model="form.customerName"
                  :fetch-suggestions="queryCustomerSuggestions"
                  value-key="name"
                  placeholder="输入客户名称筛选"
                  clearable
                  class="!w-full"
                  @select="onCustomerSelect"
                  @input="onCustomerNameInput"
                >
                  <template #default="{ item }">
                    <div class="flex flex-col py-1">
                      <span class="font-medium text-gray-900">{{ item.name }}</span>
                      <span class="text-xs text-gray-400">{{ formatCustomerMeta(item) }}</span>
                    </div>
                  </template>
                </el-autocomplete>
              </label>
              <label class="field-block md:col-span-3">
                <span>客户地址</span>
                <el-input v-model="form.customerAddress" placeholder="客户地址" />
              </label>
            </div>
          </section>
        </div>

        <div class="bg-white rounded-xl shadow-sm overflow-hidden border border-gray-100">
          <div class="px-6 py-5 border-b border-gray-100 flex flex-wrap items-center justify-between gap-4">
            <div>
              <h3 class="text-lg font-bold text-gray-900">商品明细</h3>
              <p class="text-xs text-gray-500 mt-1">输入款号/商品名选择 SKU，数量、单价、成本会实时计算。</p>
            </div>
            <el-button type="primary" plain class="!rounded-xl !font-bold" @click="addLine">
              <span class="material-symbols-outlined text-sm mr-1">add</span>
              添加一行
            </el-button>
          </div>

          <!-- 按商品批量添加 SKU -->
          <div class="px-6 py-5 border-b border-gray-100">
            <h3 class="text-base font-bold text-gray-800 mb-4 flex items-center gap-2">
              <span class="material-symbols-outlined text-[#408aee] text-lg">inventory_2</span>
              按商品批量添加
            </h3>
            <div class="batch-product-entry-row flex flex-wrap items-end gap-5">
              <div style="min-width: 340px">
                <span class="text-xs font-bold text-gray-500 mb-2 block">搜索款号 / 商品名</span>
                <el-select
                  v-model="selectedProductId"
                  filterable
                  remote
                  reserve-keyword
                  placeholder="输入款号或商品名搜索"
                  :remote-method="searchProducts"
                  :loading="productSearchLoading"
                  clearable
                  class="!w-full"
                  @change="onProductSelect"
                >
                  <el-option
                    v-for="product in productSearchOptions"
                    :key="product.id"
                    :label="`${product.productCode} / ${product.name}`"
                    :value="product.id"
                    @mouseenter="hoveredProduct = product"
                    @mousemove="hoveredProduct = product"
                    @mouseleave="hoveredProduct = null"
                  >
                    <div class="flex items-center justify-between gap-4">
                      <div
                        class="flex flex-1 items-center justify-between gap-4"
                        @mouseenter="hoveredProduct = product"
                        @mousemove="hoveredProduct = product"
                        @mouseleave="hoveredProduct = null"
                      >
                        <span class="font-bold text-gray-900">{{ product.productCode }}</span>
                        <span class="text-gray-500">{{ product.name }}</span>
                        <span class="text-xs text-gray-400">SKU {{ product.skus?.length || 0 }}</span>
                      </div>
                    </div>
                  </el-option>
                </el-select>
              </div>
              <template v-if="selectedProduct">
                <label class="batch-price-field">
                  <span>默认单价</span>
                  <el-input v-model="batchDefaultPriceText" inputmode="decimal" class="!w-full" @input="onBatchPriceInput('price')" />
                </label>
                <label class="batch-price-field">
                  <span>默认成本</span>
                  <el-input v-model="batchDefaultCostPriceText" inputmode="decimal" class="!w-full" @input="onBatchPriceInput('cost')" />
                </label>
              </template>
              <div
                class="batch-product-preview"
              >
                <template v-if="selectedProductMainImage">
                  <img :src="selectedProductMainImage" alt="" />
                  <span>当前商品</span>
                </template>
                <template v-else>
                  <span class="material-symbols-outlined">image</span>
                  <span>悬停商品查看图片</span>
                </template>
              </div>
              <div
                v-if="hoveredProduct && currentProductPreview"
                class="batch-product-hover-preview"
              >
                <img :src="currentProductPreview" alt="" />
                <span>{{ hoveredProduct.productCode }} / {{ hoveredProduct.name }}</span>
              </div>
            </div>

            <!-- SKU 颜色 × 尺码矩阵 -->
            <div v-if="selectedProduct && matrixColors.length && matrixSizes.length" class="sku-matrix-wrap mt-5">
              <div class="overflow-x-auto">
                <table class="sku-matrix-table">
                  <thead>
                    <tr>
                      <th class="sku-matrix-th sku-matrix-corner">颜色 \ 尺码</th>
                      <th
                        v-for="size in matrixSizes"
                        :key="size.id"
                        class="sku-matrix-th sku-matrix-col-hdr"
                      >{{ size.name }}</th>
                      <th class="sku-matrix-th sku-matrix-image-hdr">图片</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="color in matrixColors" :key="color.id">
                      <td class="sku-matrix-td sku-matrix-row-hdr">{{ color.name }}</td>
                      <td
                        v-for="size in matrixSizes"
                        :key="size.id"
                        class="sku-matrix-td"
                      >
                        <template v-if="findSku(color.id, size.id)">
                          <el-input
                            v-model="skuQuantityMap[findSku(color.id, size.id)!.id]"
                            inputmode="numeric"
                            placeholder=""
                            class="!w-full sku-qty-cell"
                            @input="onSkuQtyInput(findSku(color.id, size.id)!.id)"
                          />
                        </template>
                        <template v-else>
                          <span class="text-gray-300 text-xs">—</span>
                        </template>
                      </td>
                      <td class="sku-matrix-td sku-matrix-image-cell">
                        <el-popover
                          v-if="skuImageForColor(color.id)"
                          trigger="hover"
                          placement="right"
                          :width="260"
                        >
                          <template #reference>
                            <button type="button" class="sku-image-button">
                              <img :src="skuImageForColor(color.id)" alt="" />
                            </button>
                          </template>
                          <img :src="skuImageForColor(color.id)" alt="" class="sku-image-preview-large" />
                        </el-popover>
                        <span v-else class="sku-image-empty">
                          <span class="material-symbols-outlined">image</span>
                        </span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div class="flex flex-wrap items-center gap-3 mt-4">
                <el-button class="!rounded-xl !font-bold" @click="clearSkuQuantities">
                  <span class="material-symbols-outlined text-sm mr-1">clear_all</span>
                  清空数量
                </el-button>
                <el-button type="primary" class="!bg-[#408aee] !border-none !rounded-xl !font-bold" @click="addBatchToOrder">
                  <span class="material-symbols-outlined text-sm mr-1">playlist_add</span>
                  添加到订单
                </el-button>
              </div>
            </div>
            <div v-else-if="selectedProduct && activeSkus.length === 0" class="mt-4 p-4 bg-gray-50 rounded-lg text-sm text-gray-400 text-center">
              该商品暂无可用 SKU
            </div>
          </div>

          <div class="overflow-x-auto quick-table-wrap">
            <el-table :data="form.items" class="quick-table">
              <el-table-column label="#" width="48" align="center">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column label="款号 / SKU" min-width="360">
                <template #default="{ row }">
                  <el-select
                    v-model="row.skuId"
                    filterable
                    remote
                    reserve-keyword
                    placeholder="搜索款号、商品名、SKU"
                    class="!w-full"
                    :remote-method="filterSku"
                    @change="onSkuChange(row)"
                  >
                    <template v-if="row.skuId" #label>
                      <span>{{ lineSkuLabel(row) }}</span>
                    </template>
                    <el-option
                      v-for="sku in filteredSkuOptions"
                      :key="sku.skuId"
                      :label="formatSkuDisplay(sku)"
                      :value="sku.skuId"
                    >
                      <div class="flex items-center justify-between gap-4">
                        <span class="font-medium text-gray-900">{{ formatSkuDisplay(sku) }}</span>
                        <span class="text-gray-400">{{ sku.skuCode }} · 成本 {{ formatMoney(sku.costPrice) }}</span>
                      </div>
                    </el-option>
                  </el-select>
                  <p v-if="row.skuCode" class="text-[11px] text-gray-400 mt-1">
                    {{ row.productName }} · {{ row.colorName || '-' }} · {{ row.sizeName || '-' }} · 进货价 {{ formatMoney(row.costPrice) }}
                  </p>
                </template>
              </el-table-column>
              <el-table-column label="图片" width="86" align="center">
                <template #default="{ row }">
                  <el-popover
                    v-if="row.imageUrl"
                    trigger="hover"
                    placement="right"
                    :width="260"
                  >
                    <template #reference>
                      <button type="button" class="sku-image-button quick-line-image-button">
                        <img :src="row.imageUrl" alt="" />
                      </button>
                    </template>
                    <img :src="row.imageUrl" alt="" class="sku-image-preview-large" />
                  </el-popover>
                  <span v-else class="sku-image-empty quick-line-image-empty">
                    <span class="material-symbols-outlined">image</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="110">
                <template #default="{ row }">
                  <el-input
                    v-model="row.quantityText"
                    inputmode="numeric"
                    placeholder=""
                    class="!w-full"
                    @input="onQuantityInput(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="单价" width="130">
                <template #default="{ row }">
                  <el-input
                    v-model="row.priceText"
                    inputmode="decimal"
                    class="!w-full"
                    @input="onLineAmountInput(row, 'price')"
                  />
                </template>
              </el-table-column>
              <el-table-column label="成本价" width="130">
                <template #default="{ row }">
                  <el-input
                    v-model="row.costPriceText"
                    inputmode="decimal"
                    class="!w-full"
                    @input="onLineAmountInput(row, 'cost')"
                  />
                </template>
              </el-table-column>
              <el-table-column label="小计" width="120" align="right">
                <template #default="{ row }">{{ formatMoney(lineSubtotal(row)) }}</template>
              </el-table-column>
              <el-table-column label="成本" width="120" align="right">
                <template #default="{ row }">{{ formatMoney(lineCost(row)) }}</template>
              </el-table-column>
              <el-table-column label="毛利" width="120" align="right">
                <template #default="{ row }">
                  <span :class="lineProfit(row) >= 0 ? 'text-emerald-600' : 'text-red-600'">{{ formatMoney(lineProfit(row)) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeLine($index)">
                    <span class="material-symbols-outlined text-base">delete</span>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div class="grid grid-cols-1 xl:grid-cols-[minmax(0,1.25fr)_minmax(360px,0.75fr)] gap-6 items-start">
          <div class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">payments</span>
              <h3>结算与配送</h3>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
              <label class="field-block">
                <span>实收金额</span>
                <el-input-number v-model="form.paidAmount" :min="0" :max="totalAmount" :precision="2" :controls="false" class="!w-full" />
              </label>
              <label class="field-block">
                <span>客户运费收入</span>
                <el-input-number v-model="form.freightAmount" :min="0" :precision="2" :controls="false" class="!w-full" />
              </label>
              <label class="field-block">
                <span>实际运费成本</span>
                <el-input-number v-model="form.freightCost" :min="0" :precision="2" :controls="false" class="!w-full" />
              </label>
              <div class="md:col-span-3 flex items-center justify-between rounded-lg bg-gray-50 px-4 py-3">
                <span class="text-sm font-bold text-gray-600">配送方式</span>
                <el-switch v-model="needDelivery" active-text="需要送货" inactive-text="自取" />
              </div>
              <label v-if="needDelivery" class="field-block md:col-span-3">
                <span>送货地址</span>
                <el-input v-model="form.deliveryAddress" type="textarea" :rows="2" />
              </label>
              <label class="field-block md:col-span-3">
                <span>备注</span>
                <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="纸单备注、特殊说明" />
              </label>
              <div class="field-block md:col-span-3">
                <span>订单图片</span>
                <div class="flex flex-wrap gap-4">
                  <div
                    v-for="(image, index) in imageSources"
                    :key="image"
                    class="quick-image-tile group"
                  >
                    <img :src="image" alt="" class="h-full w-full object-cover" />
                    <button
                      type="button"
                      class="quick-image-remove"
                      aria-label="移除订单图片"
                      @click="removeImage(index)"
                    >
                      <span class="material-symbols-outlined text-[14px]">close</span>
                    </button>
                  </div>
                  <label class="quick-image-upload">
                    <span class="material-symbols-outlined text-2xl text-gray-400">add_photo_alternate</span>
                    <span class="text-[10px] font-bold text-gray-500">上传图片</span>
                    <input type="file" multiple accept="image/*" class="hidden" @change="handleImageUpload" />
                  </label>
                </div>
                <p class="text-xs text-gray-400">支持 JPG、PNG、GIF，可多选上传。</p>
              </div>
            </div>
          </div>

          <div class="summary-panel">
            <h3 class="text-lg font-bold mb-5">金额汇总</h3>
            <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-1 gap-x-8 gap-y-3 text-sm">
              <SummaryRow label="商品应收" :value="formatMoney(productAmount)" />
              <SummaryRow label="客户运费收入" :value="formatMoney(form.freightAmount)" />
              <SummaryRow label="订单应收" :value="formatMoney(totalAmount)" strong />
              <SummaryRow label="实收金额" :value="formatMoney(form.paidAmount)" accent />
              <SummaryRow label="尾款" :value="formatMoney(balanceAmount)" />
              <div class="hidden xl:block border-t border-slate-700 pt-3 mt-3"></div>
              <SummaryRow label="商品成本" :value="formatMoney(productCost)" />
              <SummaryRow label="实际运费成本" :value="formatMoney(form.freightCost)" />
              <SummaryRow label="总成本" :value="formatMoney(totalCost)" />
              <SummaryRow label="毛利" :value="formatMoney(grossProfit)" :positive="grossProfit >= 0" />
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder } from '@/api/order'
import { createCustomer, getCustomerPage, searchCustomerByPhone, type CustomerVO } from '@/api/customer'
import { fileVariantUrl, parseImageSources, uploadFile } from '@/api/file'
import { getProductFileBindings, getProductPage, type ProductVO, type ProductSku, type ProductFileBindingsVO } from '@/api/product'
import CountryCodeSelect from '@/components/CountryCodeSelect.vue'

interface QuickLine {
  skuId?: number
  skuCode?: string
  productName?: string
  productCode?: string
  colorName?: string
  sizeName?: string
  imageUrl?: string
  quantityText?: string
  quantity?: number
  price: number
  priceText?: string
  costPrice: number
  costPriceText?: string
}

interface SkuOption {
  skuId: number
  productId: number
  skuCode: string
  productCode: string
  productName: string
  colorName: string
  sizeName: string
  imageUrl: string
  price: number
  costPrice: number
}

type ValidQuickLine = QuickLine & { skuId: number; quantity: number }

const SummaryRow = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
    strong: Boolean,
    accent: Boolean,
    positive: Boolean,
  },
  setup(props) {
    return () => h('div', { class: 'flex items-center justify-between' }, [
      h('span', { class: 'text-slate-400' }, props.label),
      h('span', {
        class: [
          props.strong ? 'text-xl font-black text-white' : 'font-bold',
          props.accent ? 'text-emerald-400' : '',
          props.positive ? 'text-emerald-400' : '',
        ],
      }, props.value),
    ])
  },
})

const router = useRouter()
const today = new Date().toISOString().slice(0, 10)
const defaultSourceShop = '御龙'
const walkInCustomerName = '散客用户'
const walkInCustomerPhone = '88888888'
const saving = ref(false)
const needDelivery = ref(false)
const skuOptions = ref<SkuOption[]>([])
const filteredSkuOptions = ref<SkuOption[]>([])
const imageSources = ref<string[]>([])
const imageFileIds = ref<string[]>([])

// 按商品批量添加 SKU
const selectedProductId = ref<number | undefined>(undefined)
const productSearchLoading = ref(false)
const productSearchOptions = ref<ProductVO[]>([])
const selectedProduct = ref<ProductVO | null>(null)
const hoveredProduct = ref<ProductVO | null>(null)
const selectedProductBindings = ref<ProductFileBindingsVO | null>(null)
const productBindingsCache = new Map<number, ProductFileBindingsVO | null>()
const skuQuantityMap = reactive<Record<number, string>>({})
const batchDefaultPriceText = ref('')
const batchDefaultCostPriceText = ref('')

const activeSkus = computed(() =>
  (selectedProduct.value?.skus || []).filter(sku => sku.status === 1)
)

const matrixColors = computed(() => {
  const seen = new Map<number, { id: number; name: string }>()
  for (const sku of activeSkus.value) {
    if (!seen.has(sku.colorId)) {
      seen.set(sku.colorId, { id: sku.colorId, name: sku.colorName })
    }
  }
  return Array.from(seen.values())
})

const matrixSizes = computed(() => {
  const seen = new Map<number, { id: number; name: string }>()
  for (const sku of activeSkus.value) {
    if (!seen.has(sku.sizeId)) {
      seen.set(sku.sizeId, { id: sku.sizeId, name: sku.sizeName })
    }
  }
  return Array.from(seen.values()).sort((a, b) => a.id - b.id)
})

const selectedProductMainImage = computed(() => {
  const boundImage = bindingMainImage(selectedProductBindings.value)
  if (boundImage) return boundImage
  return productMainImage(selectedProduct.value)
})

const currentProductPreview = computed(() =>
  productMainImage(hoveredProduct.value) || selectedProductMainImage.value
)

function findSku(colorId: number, sizeId: number): ProductSku | undefined {
  return activeSkus.value.find(sku => sku.colorId === colorId && sku.sizeId === sizeId)
}

function productMainImage(product?: ProductVO | null) {
  return parseImageSources(product?.imageUrl)[0] || ''
}

function bindingMainImage(bindings?: ProductFileBindingsVO | null) {
  const fileId = bindings?.main?.fileId
  return fileId ? fileVariantUrl(fileId, 'thumb') : ''
}

function bindingSkuImage(bindings: ProductFileBindingsVO | null | undefined, skuId: number) {
  const group = bindings?.skuImages?.find(item => item.skuId === skuId)
  const fileId = group?.files?.[0]?.fileId
  return fileId ? fileVariantUrl(fileId, 'thumb') : ''
}

function skuImage(skuId: number) {
  return bindingSkuImage(selectedProductBindings.value, skuId)
}

function skuImageForColor(colorId: number) {
  const rowSkus = activeSkus.value.filter(sku => sku.colorId === colorId)
  for (const sku of rowSkus) {
    const image = skuImage(sku.id)
    if (image) return image
  }
  return selectedProductMainImage.value
}

async function getCachedProductBindings(productId: number) {
  if (productBindingsCache.has(productId)) {
    return productBindingsCache.get(productId) || null
  }

  try {
    const res = await getProductFileBindings(productId)
    const bindings = res.data || null
    productBindingsCache.set(productId, bindings)
    return bindings
  } catch {
    productBindingsCache.set(productId, null)
    return null
  }
}

async function resolveSkuOptionImage(sku: SkuOption) {
  const bindings = await getCachedProductBindings(sku.productId)
  return bindingSkuImage(bindings, sku.skuId) || bindingMainImage(bindings) || sku.imageUrl || ''
}

const orderTypeOptions = [
  { label: '现货订单', value: 'SPOT' },
  { label: '订货订单', value: 'PREORDER' },
]

const form = reactive({
  sourceDocNo: '',
  sourceShop: defaultSourceShop,
  orderDate: today,
  orderType: 'SPOT',
  customerId: undefined as number | undefined,
  countryCode: '+86',
  customerPhone: '',
  customerName: '',
  customerAddress: '',
  paidAmount: 0,
  freightAmount: 0,
  freightCost: 0,
  deliveryAddress: '',
  remark: '',
  items: [] as QuickLine[],
})

const productAmount = computed(() => form.items.reduce((sum, item) => sum + lineSubtotal(item), 0))
const productCost = computed(() => form.items.reduce((sum, item) => sum + lineCost(item), 0))
const totalAmount = computed(() => productAmount.value + Number(form.freightAmount || 0))
const totalCost = computed(() => productCost.value + Number(form.freightCost || 0))
const grossProfit = computed(() => totalAmount.value - totalCost.value)
const balanceAmount = computed(() => Math.max(totalAmount.value - Number(form.paidAmount || 0), 0))

function lineSubtotal(item: QuickLine) {
  return getLineQuantity(item) * Number(item.price || 0)
}

function lineCost(item: QuickLine) {
  return getLineQuantity(item) * Number(item.costPrice || 0)
}

function lineProfit(item: QuickLine) {
  return lineSubtotal(item) - lineCost(item)
}

function formatMoney(amount?: number) {
  return `¥${Number(amount || 0).toFixed(2)}`
}

function formatPlainAmount(amount?: number) {
  const value = Number(amount || 0)
  if (!Number.isFinite(value) || value <= 0) return ''
  return Number.isInteger(value) ? String(value) : String(value).replace(/0+$/, '').replace(/\.$/, '')
}

function parsePlainAmount(value: string) {
  const amount = Number(value || 0)
  return Number.isFinite(amount) && amount > 0 ? amount : 0
}

function sanitizeMoneyText(value: string) {
  return String(value || '')
    .replace(/[^\d.]/g, '')
    .replace(/^(\d*\.?\d{0,2}).*$/, '$1')
    .replace(/(\..*)\./g, '$1')
}

function formatSkuDisplay(sku: Pick<SkuOption, 'productName' | 'productCode' | 'colorName' | 'sizeName'>) {
  return `${sku.productName} · ${sku.colorName || '-'} · ${sku.sizeName || '-'}`
}

function lineSkuLabel(row: QuickLine) {
  return row.productName
    ? `${row.productName} · ${row.colorName || '-'} · ${row.sizeName || '-'}`
    : ''
}

function formatCustomerMeta(customer: CustomerVO) {
  const phone = customer.phones?.[0] || '暂无电话'
  const address = customer.address || '暂无地址'
  return `${phone} · ${address}`
}

function incrementSourceDocNo(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return ''
  const match = trimmed.match(/^(.*?)(\d+)$/)
  if (!match) return trimmed
  const [, prefix, numberPart] = match
  const nextNumber = String(Number(numberPart) + 1).padStart(numberPart.length, '0')
  return `${prefix}${nextNumber}`
}

function addLine() {
  form.items.push({ quantityText: '', quantity: undefined, price: 0, priceText: '', costPrice: 0, costPriceText: '' })
}

function isEmptyLine(item: QuickLine) {
  return !item.skuId
    && !item.skuCode
    && !item.quantityText
    && !item.quantity
    && !item.priceText
    && !item.costPriceText
    && Number(item.price || 0) === 0
    && Number(item.costPrice || 0) === 0
}

function removePlaceholderLines() {
  form.items = form.items.filter(item => !isEmptyLine(item))
}

function getLineQuantity(item: QuickLine) {
  return Number(item.quantity || 0)
}

function onQuantityInput(row: QuickLine) {
  const value = String(row.quantityText || '').replace(/[^\d]/g, '')
  row.quantityText = value
  row.quantity = value ? Number(value) : undefined
}

function setLineAmountText(row: QuickLine, price: number, costPrice: number) {
  row.price = Number(price || 0)
  row.costPrice = Number(costPrice || 0)
  row.priceText = formatPlainAmount(row.price)
  row.costPriceText = formatPlainAmount(row.costPrice)
}

function onLineAmountInput(row: QuickLine, type: 'price' | 'cost') {
  if (type === 'price') {
    row.priceText = sanitizeMoneyText(row.priceText || '')
    row.price = parsePlainAmount(row.priceText)
    return
  }

  row.costPriceText = sanitizeMoneyText(row.costPriceText || '')
  row.costPrice = parsePlainAmount(row.costPriceText)
}

function removeLine(index: number) {
  form.items.splice(index, 1)
  if (form.items.length === 0) addLine()
}

function filterSku(query: string) {
  const keyword = query.trim().toLowerCase()
  if (!keyword) {
    filteredSkuOptions.value = skuOptions.value.slice(0, 50)
    return
  }
  filteredSkuOptions.value = skuOptions.value
    .filter(sku =>
      sku.productCode.toLowerCase().includes(keyword)
      || sku.productName.toLowerCase().includes(keyword)
      || sku.skuCode.toLowerCase().includes(keyword)
    )
    .slice(0, 80)
}

async function onSkuChange(row: QuickLine) {
  const sku = skuOptions.value.find(item => item.skuId === row.skuId)
  if (!sku) return
  row.skuCode = sku.skuCode
  row.productCode = sku.productCode
  row.productName = sku.productName
  row.colorName = sku.colorName
  row.sizeName = sku.sizeName
  row.imageUrl = sku.imageUrl
  setLineAmountText(row, sku.price || 0, sku.costPrice || 0)
  const currentSkuId = sku.skuId
  const imageUrl = await resolveSkuOptionImage(sku)
  if (row.skuId === currentSkuId) {
    row.imageUrl = imageUrl
  }
  if (!row.costPrice) {
    ElMessage.warning(`${sku.productCode} 未维护进货价，成本价暂为 0`)
  }
}

function ensureSkuOption(product: ProductVO, sku: ProductSku) {
  if (skuOptions.value.some(item => item.skuId === sku.id)) return
  const option = {
    skuId: sku.id,
    productId: product.id,
    skuCode: sku.skuCode,
    productCode: product.productCode,
    productName: product.name,
    colorName: sku.colorName || '',
    sizeName: sku.sizeName || '',
    imageUrl: skuImage(sku.id) || selectedProductMainImage.value || productMainImage(product),
    price: positiveNumber(sku.price) || positiveNumber(product.wholesalePrice) || 0,
    costPrice: positiveNumber(sku.costPrice) || positiveNumber(product.costPrice) || 0,
  }
  skuOptions.value.push(option)
  filteredSkuOptions.value.push(option)
}

async function queryCustomerSuggestions(query: string, callback: (items: CustomerVO[]) => void) {
  const keyword = query.trim()
  if (!keyword) {
    callback([])
    return
  }
  try {
    const res = await getCustomerPage({ current: 1, size: 10, keyword })
    callback((res as any).data?.records || [])
  } catch {
    callback([])
  }
}

function onCustomerSelect(customer: CustomerVO) {
  form.customerId = customer.id
  form.customerName = customer.name
  form.countryCode = customer.countryCode || form.countryCode
  form.customerPhone = customer.phones?.[0] || form.customerPhone
  form.customerAddress = customer.address || ''
}

function onCustomerNameInput() {
  form.customerId = undefined
}

async function searchCustomer() {
  if (!form.customerPhone || form.customerPhone.length < 5) return
  try {
    const phone = form.customerPhone.replace(/[\s\-+]/g, '')
    const res = await searchCustomerByPhone(phone)
    if (res.data) {
      form.customerId = res.data.id
      form.countryCode = res.data.countryCode || form.countryCode
      form.customerName = res.data.name
      form.customerAddress = res.data.address || ''
      ElMessage.success(`已匹配客户：${res.data.name}`)
    }
  } catch {
    form.customerId = undefined
  }
}

function paymentStatusFromPaid() {
  if (Number(form.paidAmount || 0) <= 0) return 0
  return Number(form.paidAmount || 0) >= totalAmount.value ? 2 : 1
}

async function ensureCustomer() {
  if (form.customerId) return form.customerId

  const customerName = form.customerName.trim()
  const customerPhone = form.customerPhone.trim()
  if (!customerName || !customerPhone) return undefined

  const res = await createCustomer({
    name: customerName,
    phones: [customerPhone],
    address: form.customerAddress || undefined,
    countryCode: form.countryCode || undefined,
  })
  form.customerId = (res as any).data || (res as any)
  return form.customerId
}

function isCustomerInfoEmpty() {
  return !form.customerId
    && !form.customerName.trim()
    && !form.customerPhone.trim()
    && !form.customerAddress.trim()
}

async function applyWalkInCustomerIfEmpty() {
  if (!isCustomerInfoEmpty()) return
  form.customerName = walkInCustomerName
  form.customerPhone = walkInCustomerPhone
  await searchCustomer()
}

async function submit(next: boolean) {
  await applyWalkInCustomerIfEmpty()

  if (!form.customerName.trim()) {
    ElMessage.warning('请填写客户名称')
    return
  }
  if (!form.customerId && !form.customerPhone.trim()) {
    ElMessage.warning('新客户请填写客户电话')
    return
  }
  const validItems = form.items.filter((item): item is ValidQuickLine => Boolean(item.skuId) && getLineQuantity(item) > 0)
  if (validItems.length === 0) {
    ElMessage.warning('请至少录入一行商品')
    return
  }
  saving.value = true
  try {
    const currentSourceDocNo = form.sourceDocNo
    const customerId = await ensureCustomer()
    const data = {
      customerId,
      orderDate: form.orderDate,
      sourceDocNo: form.sourceDocNo || undefined,
      sourceShop: form.sourceShop || undefined,
      orderType: form.orderType,
      customerName: form.customerName,
      customerPhone: form.customerPhone ? form.customerPhone.replace(/[\s\-+]/g, '') : undefined,
      customerAddress: form.customerAddress || undefined,
      paymentStatus: paymentStatusFromPaid(),
      paidAmount: Number(form.paidAmount || 0),
      depositAmount: paymentStatusFromPaid() === 1 ? Number(form.paidAmount || 0) : undefined,
      freightAmount: Number(form.freightAmount || 0),
      freightCost: Number(form.freightCost || 0),
      needDelivery: needDelivery.value ? 1 : 0,
      deliveryAddress: needDelivery.value ? form.deliveryAddress : undefined,
      remark: form.remark || undefined,
      images: imageFileIds.value.length ? JSON.stringify(imageFileIds.value) : undefined,
      items: validItems.map(item => ({
        skuId: item.skuId,
        quantity: getLineQuantity(item),
        price: parsePlainAmount(item.priceText ?? String(item.price || 0)),
        costPrice: parsePlainAmount(item.costPriceText ?? String(item.costPrice || 0)),
      })),
    }
    const res = await createOrder(data)
    ElMessage.success('订单创建成功')
    if (next) {
      resetForNext(currentSourceDocNo)
    } else {
      router.push(`/orders/${res.data}`)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '创建订单失败')
  } finally {
    saving.value = false
  }
}

async function handleImageUpload(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files?.length) return

  try {
    for (const file of Array.from(target.files)) {
      const res = await uploadFile(file, 'order')
      const fileId = res.data.id
      imageFileIds.value.push(String(fileId))
      imageSources.value.push(fileVariantUrl(fileId, 'thumb'))
    }
  } catch (error: any) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    target.value = ''
  }
}

function removeImage(index: number) {
  imageSources.value.splice(index, 1)
  imageFileIds.value.splice(index, 1)
}

function resetForNext(previousSourceDocNo = '') {
  form.sourceDocNo = incrementSourceDocNo(previousSourceDocNo)
  form.sourceShop = defaultSourceShop
  form.customerId = undefined
  form.countryCode = '+86'
  form.customerPhone = ''
  form.customerName = ''
  form.customerAddress = ''
  form.paidAmount = 0
  form.freightAmount = 0
  form.freightCost = 0
  form.deliveryAddress = ''
  form.remark = ''
  form.items = []
  imageSources.value = []
  imageFileIds.value = []
  needDelivery.value = false
  selectedProductId.value = undefined
  selectedProduct.value = null
  hoveredProduct.value = null
  selectedProductBindings.value = null
  batchDefaultPriceText.value = ''
  batchDefaultCostPriceText.value = ''
  clearSkuQuantities()
  addLine()
}

async function loadProducts() {
  const res = await getProductPage({ current: 1, size: 1000 })
  const records = (res as any).data?.data?.records || (res as any).data?.records || []
  skuOptions.value = records
    .filter((product: any) => product.status === 1)
    .flatMap((product: any) => (product.skus || [])
      .filter((sku: any) => sku.status === 1)
      .map((sku: any) => ({
        skuId: sku.id,
        productId: product.id,
        skuCode: sku.skuCode,
        productCode: product.productCode,
        productName: product.name,
        colorName: sku.colorName || '',
        sizeName: sku.sizeName || '',
        imageUrl: productMainImage(product),
        price: positiveNumber(sku.price) || positiveNumber(product.wholesalePrice) || 0,
        costPrice: positiveNumber(sku.costPrice) || positiveNumber(product.costPrice) || 0,
      })))
  filteredSkuOptions.value = skuOptions.value.slice(0, 50)
}

function positiveNumber(value: unknown) {
  const amount = Number(value || 0)
  return amount > 0 ? amount : 0
}

// ========== 按商品批量添加 SKU 方法 ==========

async function searchProducts(keyword: string) {
  if (!keyword || keyword.trim().length < 1) {
    productSearchOptions.value = []
    hoveredProduct.value = null
    return
  }
  productSearchLoading.value = true
  try {
    const res = await getProductPage({ current: 1, size: 20, keyword: keyword.trim() })
    const records = (res as any).data?.data?.records || (res as any).data?.records || []
    productSearchOptions.value = (records as ProductVO[]).filter(p => p.status === 1)
  } catch {
    productSearchOptions.value = []
  } finally {
    productSearchLoading.value = false
  }
}

async function onProductSelect(productId: number | undefined) {
  if (!productId) {
    selectedProduct.value = null
    hoveredProduct.value = null
    selectedProductBindings.value = null
    clearSkuQuantities()
    return
  }
  const product = productSearchOptions.value.find(p => p.id === productId)
  selectedProduct.value = product || null
  hoveredProduct.value = null
  selectedProductBindings.value = null
  const firstActiveSku = (product?.skus || []).find(sku => sku.status === 1)
  batchDefaultPriceText.value = formatPlainAmount(positiveNumber(product?.wholesalePrice) || positiveNumber(firstActiveSku?.price))
  batchDefaultCostPriceText.value = formatPlainAmount(positiveNumber(product?.costPrice) || positiveNumber(firstActiveSku?.costPrice))
  clearSkuQuantities()
  if (product) {
    await loadProductBindings(product.id)
  }
}

async function loadProductBindings(productId: number) {
  try {
    const res = await getProductFileBindings(productId)
    selectedProductBindings.value = res.data || null
    productBindingsCache.set(productId, selectedProductBindings.value)
  } catch {
    selectedProductBindings.value = null
    productBindingsCache.set(productId, null)
  }
}

function clearSkuQuantities() {
  Object.keys(skuQuantityMap).forEach(key => {
    delete skuQuantityMap[Number(key)]
  })
}

function onSkuQtyInput(skuId: number) {
  const value = String(skuQuantityMap[skuId] || '').replace(/[^\d]/g, '')
  skuQuantityMap[skuId] = value
}

function onBatchPriceInput(type: 'price' | 'cost') {
  const source = type === 'price' ? batchDefaultPriceText.value : batchDefaultCostPriceText.value
  const sanitized = sanitizeMoneyText(source)
  if (type === 'price') {
    batchDefaultPriceText.value = sanitized
  } else {
    batchDefaultCostPriceText.value = sanitized
  }
}

function addBatchToOrder() {
  if (!selectedProduct.value) return

  let added = 0
  let merged = 0
  const pendingSkus = activeSkus.value
    .map(sku => ({ sku, quantity: Number(skuQuantityMap[sku.id] || 0) }))
    .filter(item => item.quantity > 0)

  if (pendingSkus.length === 0) {
    ElMessage.warning('请至少填写一个 SKU 数量')
    return
  }

  removePlaceholderLines()

  for (const { sku, quantity } of pendingSkus) {
    ensureSkuOption(selectedProduct.value, sku)
    const imageUrl = skuImage(sku.id) || selectedProductMainImage.value

    const existing = form.items.find(item => item.skuId === sku.id)
    if (existing) {
      const currentQuantity = getLineQuantity(existing)
      const nextQuantity = currentQuantity + quantity
      existing.quantity = nextQuantity
      existing.quantityText = String(nextQuantity)
      if (!existing.imageUrl) existing.imageUrl = imageUrl
      merged++
      continue
    }

    form.items.push({
      skuId: sku.id,
      skuCode: sku.skuCode,
      productCode: selectedProduct.value.productCode,
      productName: selectedProduct.value.name,
      colorName: sku.colorName,
      sizeName: sku.sizeName,
      imageUrl,
      quantity,
      quantityText: String(quantity),
      price: parsePlainAmount(batchDefaultPriceText.value),
      priceText: formatPlainAmount(parsePlainAmount(batchDefaultPriceText.value)),
      costPrice: parsePlainAmount(batchDefaultCostPriceText.value),
      costPriceText: formatPlainAmount(parsePlainAmount(batchDefaultCostPriceText.value)),
    })
    added++
  }

  const handled = added + merged
  clearSkuQuantities()
  ElMessage.success(`已添加 ${handled} 个 SKU，合并 ${merged} 个重复 SKU`)
}

onMounted(async () => {
  addLine()
  await loadProducts()
})
</script>

<style scoped>
.quick-order-page {
  min-width: 1180px;
}

.form-panel {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.summary-panel {
  min-height: 100%;
  border-radius: 12px;
  padding: 24px;
  color: #fff;
  background: #1a1c1e;
  box-shadow: 0 14px 32px rgb(15 23 42 / 16%);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 12px;
  border-left: 4px solid #408aee;
  margin-bottom: 20px;
}

.panel-title h3 {
  font-size: 16px;
  line-height: 1.2;
  font-weight: 800;
  color: #111827;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-block > span {
  font-size: 12px;
  line-height: 1;
  font-weight: 800;
  color: #6b7280;
}

.quick-order-page :deep(.el-input__wrapper),
.quick-order-page :deep(.el-select__wrapper) {
  min-height: 42px;
  border-radius: 8px;
}

.quick-segmented {
  width: 100%;
}

.quick-table-wrap {
  min-height: 360px;
}

.quick-table :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.quick-table :deep(.el-table__cell) {
  padding-top: 14px;
  padding-bottom: 14px;
}

.quick-image-tile,
.quick-image-upload {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
}

.quick-image-tile {
  border: 1px solid #e5e7eb;
}

.quick-image-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 2px dashed #d1d5db;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.quick-image-upload:hover {
  border-color: #408aee;
  background: #eff6ff;
}

.quick-image-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 999px;
  padding: 4px;
  color: #fff;
  background: rgb(0 0 0 / 52%);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.quick-image-tile:hover .quick-image-remove,
.quick-image-remove:focus-visible {
  opacity: 1;
}

.batch-price-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 150px;
}

.batch-price-field span {
  font-size: 12px;
  font-weight: 700;
  color: #6b7280;
}

.batch-product-entry-row {
  position: relative;
}

.batch-product-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 210px;
  min-height: 72px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.batch-product-preview img {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.batch-product-hover-preview {
  position: absolute;
  top: 68px;
  left: 380px;
  z-index: 2600;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 380px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 42px rgb(15 23 42 / 18%);
  pointer-events: none;
}

.batch-product-hover-preview::before {
  content: '';
  position: absolute;
  top: -8px;
  left: 44px;
  width: 16px;
  height: 16px;
  border-top: 1px solid #dbeafe;
  border-left: 1px solid #dbeafe;
  background: #fff;
  transform: rotate(45deg);
}

.batch-product-hover-preview img {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 420px;
  border-radius: 10px;
  object-fit: contain;
  background: #f8fafc;
}

.batch-product-hover-preview span {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-product-preview .material-symbols-outlined {
  font-size: 28px;
  color: #94a3b8;
}

/* ---- SKU 颜色×尺码矩阵 ---- */
.sku-matrix-wrap {
  max-width: 100%;
}

.sku-matrix-table {
  width: auto;
  min-width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.sku-matrix-th {
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 2px solid #e5e7eb;
  font-weight: 800;
  color: #374151;
  white-space: nowrap;
}

.sku-matrix-corner {
  border-radius: 8px 0 0 0;
  text-align: left;
}

.sku-matrix-col-hdr {
  text-align: center;
  min-width: 80px;
}

.sku-matrix-image-hdr {
  text-align: center;
  min-width: 82px;
}

.sku-matrix-td {
  padding: 6px 4px;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}

.sku-matrix-row-hdr {
  font-weight: 700;
  color: #374151;
  background: #fafafa;
  min-width: 70px;
  padding: 8px 14px;
}

.sku-qty-cell :deep(.el-input__wrapper) {
  min-height: 36px;
  border-radius: 6px;
  box-shadow: none;
  border: 1px solid #e5e7eb;
}

.sku-qty-cell :deep(.el-input__wrapper:hover) {
  border-color: #408aee;
}

.sku-qty-cell :deep(.el-input__inner) {
  text-align: center;
}

.sku-matrix-image-cell {
  text-align: center;
  width: 82px;
}

.sku-image-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  cursor: zoom-in;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.sku-image-button:hover {
  border-color: #408aee;
  box-shadow: 0 8px 20px rgb(64 138 238 / 18%);
}

.sku-image-button img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sku-image-preview-large {
  display: block;
  width: 240px;
  max-height: 320px;
  object-fit: contain;
  border-radius: 8px;
  background: #f8fafc;
}

.sku-image-empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #94a3b8;
  background: #f8fafc;
}

.sku-image-empty .material-symbols-outlined {
  font-size: 22px;
}
</style>
