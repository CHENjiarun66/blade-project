<template>
  <div class="order-form-page">
    <!-- 页面标题区 -->
    <div class="flex items-center justify-between mb-8">
      <div class="flex items-center gap-4">
        <button
          @click="handleBack"
          class="flex items-center gap-2 px-4 py-2 border border-gray-200 rounded-xl text-gray-600 hover:bg-gray-50 transition-colors bg-white font-medium text-sm"
        >
          <span class="material-symbols-outlined text-[20px]">arrow_back</span>
          返回列表
        </button>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight">新建订单</h2>
      </div>
      <div class="flex items-center gap-3">
        <button @click="handleSubmit" class="px-6 py-2.5 bg-[#408aee] text-white rounded-xl font-semibold shadow-lg shadow-primary/20 hover:bg-[#2d7be0] active:scale-95 transition-all flex items-center gap-2">
          <span class="material-symbols-outlined text-[20px]">save</span>
          保存订单
        </button>
      </div>
    </div>

    <div class="grid grid-cols-12 gap-8 items-start pb-24">
      <!-- 左侧内容 -->
      <div class="col-span-12 lg:col-span-8 space-y-6">
        <!-- 客户信息 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">客户信息</h3>
            <span v-if="isExistingCustomer" class="ml-2 px-2 py-0.5 bg-green-100 text-green-600 text-[10px] font-bold rounded-full">已匹配客户</span>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">客户电话 <span class="text-red-500">*</span></label>
              <el-input
                v-model="form.customerPhone"
                placeholder="输入电话号码自动匹配客户"
                class="order-form-input"
                @blur="searchCustomer"
                clearable
              />
            </div>
            <div>
              <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">客户名称 <span class="text-red-500">*</span></label>
              <el-input
                v-model="form.customerName"
                :placeholder="isExistingCustomer ? '已匹配客户' : '请输入客户名称'"
                class="order-form-input"
                :disabled="isExistingCustomer"
              />
            </div>
            <div class="md:col-span-2">
              <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">客户地址</label>
              <el-input
                v-model="form.customerAddress"
                :placeholder="isExistingCustomer ? '已匹配客户' : '请输入客户地址'"
                class="order-form-input"
                :disabled="isExistingCustomer"
              />
            </div>
          </div>
        </div>

        <!-- 支付信息 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">支付信息</h3>
          </div>
          <div class="space-y-6">
            <div>
              <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-4">支付状态</label>
              <div class="flex flex-wrap gap-4">
                <label
                  v-for="option in paymentStatusOptions"
                  :key="option.value"
                  class="flex items-center gap-3 cursor-pointer group"
                >
                  <input
                    type="radio"
                    :value="option.value"
                    v-model="form.paymentStatus"
                    @change="onPaymentStatusChange(option.value)"
                    class="w-5 h-5 text-[#408aee] focus:ring-[#408aee] border-gray-300 bg-gray-50"
                  />
                  <span class="text-sm font-medium text-gray-700 group-hover:text-[#408aee] transition-colors">{{ option.label }}</span>
                </label>
              </div>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 border-t border-gray-100">
              <div>
                <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">定金金额</label>
                <div class="relative">
                  <span class="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500 font-medium">¥</span>
                  <el-input
                    v-model.number="form.depositAmount"
                    type="number"
                    :disabled="form.paymentStatus !== 1"
                    class="order-form-input !pl-8"
                    placeholder="0.00"
                  />
                </div>
              </div>
              <div>
                <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">已付金额</label>
                <div class="relative">
                  <span class="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500 font-medium">¥</span>
                  <el-input
                    v-model.number="form.paidAmount"
                    type="number"
                    :disabled="form.paymentStatus !== 2"
                    class="order-form-input !pl-8"
                    placeholder="0.00"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 商品明细 -->
        <div class="bg-white rounded-xl shadow-sm overflow-hidden">
          <div class="p-6 border-b border-gray-100 flex justify-between items-center">
            <div class="flex items-center gap-2 border-l-4 border-[#408aee] pl-4">
              <h3 class="text-lg font-bold text-gray-900">商品明细</h3>
            </div>
            <button
              @click="showProductDialog = true"
              class="flex items-center gap-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-xl font-bold text-sm hover:bg-gray-200 active:scale-95 transition-all"
            >
              <span class="material-symbols-outlined text-[18px]">add_circle</span>
              添加商品
            </button>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-left">
              <thead class="bg-gray-50 text-xs font-bold text-gray-500 uppercase tracking-wider">
                <tr>
                  <th class="px-6 py-4">商品名称</th>
                  <th class="px-6 py-4">SKU编码</th>
                  <th class="px-6 py-4">颜色/尺码</th>
                  <th class="px-6 py-4">单价</th>
                  <th class="px-6 py-4">数量</th>
                  <th class="px-6 py-4">小计</th>
                  <th class="px-6 py-4 text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="item in form.items" :key="item.skuId" class="hover:bg-gray-50/50 transition-colors group">
                  <td class="px-6 py-4 font-semibold text-gray-900">{{ item.productName }}</td>
                  <td class="px-6 py-4 font-mono text-sm text-gray-500">{{ item.skuCode }}</td>
                  <td class="px-6 py-4">
                    <span class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md bg-gray-100 text-xs font-bold text-gray-600">
                      {{ item.colorName }} / {{ item.sizeName }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex items-center gap-1">
                      <span class="text-gray-500">¥</span>
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        :value="item.price"
                        @input="item.price = parseFloat(($event.target as HTMLInputElement).value) || 0"
                        class="w-20 h-8 text-center border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-[#408aee]"
                      />
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div class="flex items-center bg-gray-100 rounded-lg w-fit">
                      <button
                        @click="decreaseQty(item)"
                        class="w-8 h-8 flex items-center justify-center hover:text-[#408aee] transition-colors"
                      >-</button>
                      <span class="w-8 text-center text-sm font-bold">{{ item.quantity }}</span>
                      <button
                        @click="item.quantity++"
                        class="w-8 h-8 flex items-center justify-center hover:text-[#408aee] transition-colors"
                      >+</button>
                    </div>
                  </td>
                  <td class="px-6 py-4 font-bold text-[#408aee]">¥ {{ ((item.price || 0) * item.quantity).toFixed(2) }}</td>
                  <td class="px-6 py-4 text-right">
                    <button
                      @click="removeItem(item.skuId)"
                      class="text-gray-400 hover:text-red-500 transition-colors p-2 hover:bg-red-50 rounded-lg"
                    >
                      <span class="material-symbols-outlined">delete</span>
                    </button>
                  </td>
                </tr>
                <tr v-if="form.items.length === 0">
                  <td colspan="7" class="px-6 py-12 text-center text-gray-400">
                    <span class="material-symbols-outlined text-4xl mb-2">inventory_2</span>
                    <p class="text-sm">暂无商品，请点击"添加商品"按钮添加</p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="p-4 bg-gray-50/50 flex justify-between text-sm font-medium text-gray-500">
            <span>已添加商品种类数：共 {{ form.items.length }} 种</span>
            <span>已添加商品总数：共 {{ totalQuantity }} 件</span>
          </div>
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="col-span-12 lg:col-span-4 space-y-6">
        <!-- 金额汇总 -->
        <div class="bg-[#1a1c1e] text-white rounded-2xl p-6 shadow-2xl relative overflow-hidden">
          <div class="absolute -right-8 -top-8 w-24 h-24 bg-[#408aee]/20 rounded-full blur-2xl"></div>
          <div class="absolute -left-8 -bottom-8 w-24 h-24 bg-blue-400/10 rounded-full blur-2xl"></div>
          <div class="relative z-10">
            <h3 class="text-lg font-bold mb-6 flex items-center gap-2">
              <span class="material-symbols-outlined text-blue-400">payments</span>
              金额汇总
            </h3>
            <div class="space-y-4">
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">商品总额</span>
                <span class="text-lg font-bold text-white">¥ {{ totalAmount.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">已付金额</span>
                <span class="text-lg font-bold text-emerald-400">¥ {{ (form.paidAmount || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="pt-4 border-t border-slate-800">
                <div class="flex justify-between items-end">
                  <div>
                    <p class="text-[10px] font-black text-blue-400 uppercase tracking-widest mb-1">应付尾款余额</p>
                    <h4 class="text-3xl font-black tracking-tight">¥ {{ balance.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</h4>
                  </div>
                  <div class="p-2 bg-[#408aee]/20 rounded-lg">
                    <span class="material-symbols-outlined text-blue-400">account_balance_wallet</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="mt-6 space-y-2">
              <button
                @click="handleSubmit"
                :disabled="!canSubmit"
                class="w-full py-3 bg-[#408aee] hover:bg-[#2d7be0] disabled:bg-gray-600 disabled:cursor-not-allowed text-white font-black rounded-xl transition-all shadow-lg shadow-primary/30 flex items-center justify-center gap-2 active:scale-95"
              >
                保存订单并进入详情
                <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
              </button>
              <button class="w-full py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 font-bold rounded-xl transition-all flex items-center justify-center gap-2 text-sm">
                <span class="material-symbols-outlined text-[16px]">print</span>
                打印预览
              </button>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="bg-white p-4 rounded-xl shadow-sm border-l-4 border-amber-400">
            <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">库存筛选（可选）</p>
            <el-select v-model="form.warehouseId" placeholder="不选则按全仓库存参考" clearable class="w-full">
              <el-option
                v-for="wh in warehouseList"
                :key="wh.id"
                :label="wh.warehouseName"
                :value="wh.id"
              />
            </el-select>
          </div>
          <div class="bg-white p-4 rounded-xl shadow-sm border-l-4 border-blue-400">
            <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">优先级</p>
            <span class="text-sm font-bold text-gray-900">标准处理</span>
          </div>
        </div>

        <!-- 送货设置 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center justify-between mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">送货设置</h3>
            <label class="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                v-model="needDelivery"
                class="sr-only peer"
              />
              <div class="w-11 h-6 bg-gray-200 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-0.5 after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#408aee]"></div>
              <span class="ml-3 text-xs font-bold text-gray-500 uppercase">是否需要送货</span>
            </label>
          </div>
          <div>
            <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">送货地址</label>
            <el-input
              v-model="form.deliveryAddress"
              type="textarea"
              :rows="2"
              :disabled="!needDelivery"
              placeholder="请输入详细送货地址"
              class="order-form-textarea"
            />
          </div>
        </div>

        <!-- 图片上传 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">图片上传</h3>
          </div>
          <div class="space-y-4">
            <div class="flex flex-wrap gap-4">
              <div
                v-for="(img, idx) in imageList"
                :key="idx"
                class="relative w-24 h-24 rounded-lg overflow-hidden border border-gray-200 group"
              >
                <img :src="img" alt="" class="w-full h-full object-cover" />
                <button
                  @click="removeImage(idx)"
                  class="absolute top-1 right-1 bg-black/50 text-white rounded-full p-1 opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <span class="material-symbols-outlined text-[14px]">close</span>
                </button>
              </div>
              <label class="w-24 h-24 flex flex-col items-center justify-center border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-[#408aee] hover:bg-blue-50 transition-colors">
                <span class="material-symbols-outlined text-gray-400 text-2xl mb-1">add_photo_alternate</span>
                <span class="text-[10px] text-gray-500 font-medium">上传图片</span>
                <input
                  type="file"
                  multiple
                  accept="image/*"
                  class="hidden"
                  @change="handleImageUpload"
                />
              </label>
            </div>
            <p class="text-xs text-gray-400">支持 JPG, PNG, GIF 格式，可多选上传</p>
          </div>
        </div>

        <!-- 备注信息 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">备注信息</h3>
          </div>
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
            class="order-form-textarea"
          />
        </div>
      </div>
    </div>

    <!-- 添加商品弹窗 -->
    <el-dialog
      v-model="showProductDialog"
      title="添加商品"
      width="700px"
      class="product-dialog"
    >
      <div class="mb-4">
        <el-input
          v-model="productSearch"
          placeholder="搜索商品名称或款号"
          class="order-form-input"
          clearable
        >
          <template #prefix>
            <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
          </template>
        </el-input>
      </div>

      <!-- 商品列表 -->
      <div class="max-h-[400px] overflow-y-auto space-y-3">
        <div
          v-for="product in filteredProducts"
          :key="product.id"
          class="border border-gray-200 rounded-xl overflow-hidden"
        >
          <!-- 商品表头（可点击展开） -->
          <div
            class="flex items-center justify-between px-4 py-3 bg-gray-50 cursor-pointer hover:bg-gray-100 transition-colors"
            @click="toggleProduct(product.id)"
          >
            <div class="flex items-center gap-3">
              <span class="material-symbols-outlined text-gray-400">{{ expandedProducts.has(product.id) ? 'expand_more' : 'chevron_right' }}</span>
              <div>
                <p class="font-bold text-gray-900">{{ product.name }}</p>
                <p class="text-xs text-gray-500">{{ product.code }} · {{ product.skus.length }} 个规格</p>
              </div>
            </div>
            <div class="flex items-center gap-4">
              <span class="text-sm font-bold text-[#408aee]">¥ {{ product.price?.toFixed(2) }}</span>
              <span
                v-if="getProductSelectedCount(product.id) > 0"
                class="px-2 py-0.5 bg-[#408aee] text-white text-xs font-bold rounded-full"
              >
                {{ getProductSelectedCount(product.id) }} 件已选
              </span>
            </div>
          </div>

          <!-- SKU 矩阵（展开时显示） -->
          <div v-if="expandedProducts.has(product.id)" class="border-t border-gray-200">
            <!-- 单价调整行 -->
            <div class="px-4 py-2 bg-gray-50 flex items-center gap-4">
              <span class="text-xs font-bold text-gray-500">单价调整：</span>
              <div class="flex items-center gap-1">
                <span class="text-gray-500">¥</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  v-model.number="product.price"
                  class="w-24 h-7 text-center border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-[#408aee]"
                />
              </div>
              <span class="text-xs text-gray-400">（所有颜色/尺码统一价格）</span>
            </div>
            <table class="w-full text-left">
              <thead class="bg-white text-xs font-bold text-gray-500 uppercase tracking-wider">
                <tr>
                  <th class="px-4 py-2">颜色/尺码</th>
                  <th v-for="size in product.sizes" :key="size" class="px-4 py-2 text-center">{{ size }}</th>
                  <th class="px-4 py-2 text-center">小计</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="color in product.colors" :key="color">
                  <td class="px-4 py-2">
                    <span class="inline-flex items-center px-2 py-1 rounded bg-gray-100 text-xs font-bold text-gray-600">
                      {{ color }}
                    </span>
                  </td>
                  <td
                    v-for="size in product.sizes"
                    :key="size"
                    class="px-4 py-2 text-center"
                  >
                    <div class="flex flex-col items-center gap-1">
                      <!-- 库存信息：已选仓库显示仓库库存，未选则显示跨仓合计 -->
                      <span :class="getInventoryClass(getSkuByColorSize(product, color, size)?.id || 0)">
                        {{ getSkuAvailableQtyText(getSkuByColorSize(product, color, size)?.id || 0) }}
                        <template v-if="!form.warehouseId && getSkuAvailableQty(getSkuByColorSize(product, color, size)?.id || 0) > 0">
                          <span class="text-gray-400 font-normal">(合计)</span>
                        </template>
                      </span>
                      <!-- 数量输入框（创建订单不强制校验库存，始终可填写） -->
                      <input
                        type="number"
                        min="0"
                        :value="getSkuQty(product.id, color, size)"
                        @input="setSkuQty(product.id, color, size, ($event.target as HTMLInputElement).value)"
                        class="w-16 h-8 text-center border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-[#408aee]"
                        placeholder="0"
                      />
                    </div>
                  </td>
                  <td class="px-4 py-2 text-center">
                    <span class="text-xs font-bold text-[#408aee]">
                      {{ getRowTotal(product.id, color) > 0 ? `${getRowTotal(product.id, color)} 件` : '-' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="filteredProducts.length === 0" class="text-center py-12 text-gray-400">
          <span class="material-symbols-outlined text-4xl mb-2">inventory_2</span>
          <p class="text-sm">暂无商品，请尝试其他关键词</p>
        </div>
      </div>

      <!-- 底部批量操作栏 -->
      <div class="mt-4 pt-4 border-t border-gray-200 flex items-center justify-between">
        <div class="text-sm text-gray-500">
          已选 <span class="font-bold text-[#408aee]">{{ totalSelectedItems }}</span> 个规格，共 <span class="font-bold text-[#408aee]">{{ totalSelectedQuantity }}</span> 件
        </div>
        <div class="flex gap-3">
          <el-button @click="clearAllSelections">清空选择</el-button>
          <el-button
            type="primary"
            :disabled="totalSelectedQuantity === 0"
            @click="batchAddProducts"
          >
            批量添加 ({{ totalSelectedQuantity }})
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder } from '@/api/order'
import { searchCustomerByPhone, type CustomerVO } from '@/api/customer'
import { uploadFile } from '@/api/file'
import { getProductPage } from '@/api/product'
import { getAllWarehouses, getInventoryByWarehouse, getInventoryPage } from '@/api/inventory'

const router = useRouter()

// 支付状态选项
const paymentStatusOptions = [
  { label: '未付款', value: 0 },
  { label: '已付定金', value: 1 },
  { label: '已付全款', value: 2 },
]

// 仓库列表，仅用于库存参考筛选，不作为订单来源字段
const warehouseList = ref<Array<{ id: number; warehouseName: string }>>([])

// 加载仓库列表
async function loadWarehouses() {
  try {
    const res = await getAllWarehouses()
    warehouseList.value = res.data || []
  } catch (error) {
    console.error('加载仓库失败:', error)
  }
}

// 表单数据
const form = reactive({
  customerId: undefined as number | undefined,
  customerName: '',
  customerPhone: '',
  customerAddress: '',
  paymentStatus: 0,
  depositAmount: 0,
  paidAmount: 0,
  needDelivery: 0,
  deliveryAddress: '',
  warehouseId: undefined as number | undefined,
  remark: '',
  images: '',
  items: [] as Array<{
    skuId: number
    skuCode: string
    productName: string
    colorName: string
    sizeName: string
    price: number
    quantity: number
  }>,
})

const needDelivery = ref(false)
const imageList = ref<string[]>([])
const imageFileIds = ref<string[]>([])
const showProductDialog = ref(false)
const productSearch = ref('')
const isExistingCustomer = ref(false)
const currentCustomer = ref<CustomerVO | null>(null)


// 商品列表类型定义
interface ProductItem {
  id: number
  name: string
  code: string
  price: number
  colors: string[]
  sizes: string[]
  skus: Array<{
    id: number
    color: string
    size: string
    skuCode: string
    price: number
  }>
}

// 商品列表（带颜色/尺码矩阵）
const products = ref<ProductItem[]>([])

// 加载商品数据
async function loadProducts() {
  try {
    const res = await getProductPage({ current: 1, size: 100 })
    // 转换后端数据为前端格式 (response.data 是 ApiResponse<ProductPageResponse>)
    const pageData = (res as any).data?.data || (res as any).data
    if (!pageData?.records) return
    products.value = pageData.records.map((p: any) => ({
      id: p.id,
      name: p.name,
      code: p.productCode,
      price: p.price,
      colors: (p.colors || []).map((c: any) => c.colorName),
      sizes: (p.sizes || []).map((s: any) => s.sizeCode),
      skus: (p.skus || []).map((s: any) => ({
        id: s.id,
        color: s.colorName,
        size: s.sizeName,
        skuCode: s.skuCode,
        price: s.price,
      })),
    }))
  } catch (error: any) {
    ElMessage.error(error.message || '加载商品失败')
  }
}

// 页面加载时获取商品数据、仓库列表和全局库存
onMounted(() => {
  loadProducts()
  loadWarehouses()
  loadGlobalInventory()
})

// 已展开的商品 ID 集合
const expandedProducts = ref(new Set<number>())

// SKU 数量 Map：key = "productId-color-size", value = 数量
const skuQtys = ref<Record<string, number>>({})

// SKU 库存映射: skuId → availableQty
const skuInventoryMap = ref<Record<number, number>>({})

// 加载全量库存（跨仓库汇总），页面初始化时调用
async function loadGlobalInventory() {
  try {
    const res = await getInventoryPage({ current: 1, size: 1000 })
    const records = (res as any).data?.data?.records || (res as any).data?.records || []
    const map: Record<number, number> = {}
    for (const inv of records) {
      map[inv.skuId] = (map[inv.skuId] || 0) + (inv.availableQty || 0)
    }
    skuInventoryMap.value = map
  } catch (error) {
    console.error('加载全局库存失败:', error)
  }
}

// 加载指定仓库的库存
async function loadInventoryByWarehouse(warehouseId: number) {
  try {
    const res = await getInventoryByWarehouse(warehouseId)
    const map: Record<number, number> = {}
    const inventoryList = (res as any).data?.data || (res as any).data || []
    for (const inv of inventoryList) {
      map[inv.skuId] = inv.availableQty
    }
    skuInventoryMap.value = map
  } catch (error) {
    console.error('加载库存失败:', error)
  }
}

// 获取SKU可用库存
function getSkuAvailableQty(skuId: number): number {
  return skuInventoryMap.value[skuId] ?? 0
}

// 获取库存显示文本
function getSkuAvailableQtyText(skuId: number): string {
  const qty = getSkuAvailableQty(skuId)
  if (qty === 0) return '无货'
  return `可用: ${qty}`
}

// 获取库存样式类
function getInventoryClass(skuId: number): string {
  const qty = getSkuAvailableQty(skuId)
  if (qty === 0) return 'text-red-500 text-xs font-bold'
  if (qty < 10) return 'text-orange-500 text-xs font-bold'
  return 'text-green-600 text-xs font-bold'
}

// 根据颜色和尺码获取SKU
function getSkuByColorSize(product: ProductItem, color: string, size: string) {
  return product.skus.find(s => s.color === color && s.size === size)
}

// 监听仓库选择变化，加载库存
watch(() => form.warehouseId, async (warehouseId, oldWarehouseId) => {
  if (warehouseId && warehouseId !== oldWarehouseId) {
    await loadInventoryByWarehouse(warehouseId)
  } else if (!warehouseId) {
    // 取消仓库选择时，恢复跨仓合计库存
    await loadGlobalInventory()
  }
})

// 根据搜索过滤商品
const filteredProducts = computed(() => {
  if (!productSearch.value) return products.value
  const search = productSearch.value.toLowerCase()
  return products.value.filter(
    p => p.name.toLowerCase().includes(search) || p.code.toLowerCase().includes(search)
  )
})

// 计算已选规格数和总数量
const totalSelectedItems = computed(() => {
  return Object.values(skuQtys.value).filter(qty => qty > 0).length
})

const totalSelectedQuantity = computed(() => {
  return Object.values(skuQtys.value).reduce((sum, qty) => sum + qty, 0)
})

// 切换商品展开状态
function toggleProduct(productId: number) {
  if (expandedProducts.value.has(productId)) {
    expandedProducts.value.delete(productId)
  } else {
    expandedProducts.value.add(productId)
  }
}

// 获取某个 SKU 的数量
function getSkuQty(productId: number, color: string, size: string): number {
  return skuQtys.value[`${productId}-${color}-${size}`] || 0
}

// 设置某个 SKU 的数量
function setSkuQty(productId: number, color: string, size: string, value: string) {
  const qty = parseInt(value) || 0
  if (qty > 0) {
    skuQtys.value[`${productId}-${color}-${size}`] = qty
  } else {
    delete skuQtys.value[`${productId}-${color}-${size}`]
  }
}

// 获取某行（某个颜色）的总数量
function getRowTotal(productId: number, color: string): number {
  const product = products.value.find(p => p.id === productId)
  if (!product) return 0
  return product.sizes.reduce((sum, size) => {
    return sum + (getSkuQty(productId, color, size) || 0)
  }, 0)
}

// 获取某个商品已选规格数
function getProductSelectedCount(productId: number): number {
  const product = products.value.find(p => p.id === productId)
  if (!product) return 0
  let count = 0
  for (const color of product.colors) {
    for (const size of product.sizes) {
      if (getSkuQty(productId, color, size) > 0) count++
    }
  }
  return count
}

// 清空所有选择
function clearAllSelections() {
  skuQtys.value = {}
}

// 批量添加商品到订单
function batchAddProducts() {
  for (const product of products.value) {
    for (const sku of product.skus) {
      const qty = getSkuQty(product.id, sku.color, sku.size)
      const available = getSkuAvailableQty(sku.id)

      if (qty > 0) {
        // 仅在已选择仓库且库存确实为 0 时提示（创建订单不强制校验库存）
        if (form.warehouseId && available === 0) {
          ElMessage.warning(`${sku.skuCode} 当前仓库库存为 0，已加入订单，请注意`)
        }
        // 使用商品级别的价格（用户可调整）
        const price = product.price
        // 检查是否已存在
        const existing = form.items.find(item => item.skuId === sku.id)
        if (existing) {
          existing.quantity += qty
          // 如果单价有变化，更新为最新单价
          existing.price = price
        } else {
          form.items.push({
            skuId: sku.id,
            skuCode: sku.skuCode,
            productName: product.name,
            colorName: sku.color,
            sizeName: sku.size,
            price: price,
            quantity: qty,
          })
        }
      }
    }
  }
  // 清空选择
  clearAllSelections()
  // 关闭弹窗
  showProductDialog.value = false
  ElMessage.success('已批量添加商品')
}

// 计算属性
const totalAmount = computed(() => {
  return form.items.reduce((sum, item) => sum + (item.price || 0) * item.quantity, 0)
})

const totalQuantity = computed(() => {
  return form.items.reduce((sum, item) => sum + item.quantity, 0)
})

const balance = computed(() => {
  return totalAmount.value - (form.paidAmount || 0)
})

const submitValidationMessage = computed(() => {
  if (!form.customerName.trim()) return '请填写客户名称'
  if (form.items.length === 0) return '请至少添加一件商品'
  if (needDelivery.value && !form.deliveryAddress.trim()) return '需要送货时请填写送货地址'
  if (form.paymentStatus === 1) {
    if (!form.depositAmount || form.depositAmount <= 0) return '已付定金时，定金金额必须大于 0'
    if (form.depositAmount > totalAmount.value) return '定金金额不能大于订单总额'
  }
  if (form.paymentStatus === 2 && totalAmount.value <= 0) return '订单总额必须大于 0 才能标记为已付全款'
  return ''
})

const canSubmit = computed(() => {
  return !submitValidationMessage.value
})

// 监听支付状态变化，自动清零相关金额
function onPaymentStatusChange(val: number) {
  if (val === 0) {
    // 未付款：清零定金和已付金额
    form.depositAmount = 0
    form.paidAmount = 0
  } else if (val === 1) {
    // 已付定金：清零已付金额
    form.paidAmount = 0
  } else if (val === 2) {
    // 已付全款：清零定金金额
    form.depositAmount = 0
  }
}

// 根据电话搜索客户
async function searchCustomer(_event?: Event) {
  if (!form.customerPhone || form.customerPhone.length < 5) {
    return
  }
  // 去掉 + 号、空格、横杠等，只保留纯数字
  const phone = form.customerPhone.replace(/[\s\-+]/g, '')
  try {
    const res = await searchCustomerByPhone(phone)
    if (res.data) {
      // 找到旧客户，自动填充信息并锁定
      currentCustomer.value = res.data
      form.customerId = res.data.id
      form.customerName = res.data.name
      form.customerAddress = res.data.address || ''
      isExistingCustomer.value = true
      ElMessage.success(`已匹配客户：${res.data.name}`)
    } else {
      // 没找到，清空并解锁，当作新客户
      currentCustomer.value = null
      form.customerId = undefined
      form.customerName = ''
      form.customerAddress = ''
      isExistingCustomer.value = false
    }
  } catch (error) {
    // 查询失败，清空并解锁，当作新客户处理
    currentCustomer.value = null
    form.customerId = undefined
    form.customerName = ''
    form.customerAddress = ''
    isExistingCustomer.value = false
  }
}

// 方法
function handleBack() {
  router.back()
}

function decreaseQty(item: { quantity: number }) {
  if (item.quantity > 1) {
    item.quantity--
  }
}

function removeItem(skuId: number) {
  form.items = form.items.filter(item => item.skuId !== skuId)
}

async function handleImageUpload(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files) {
    try {
      const files = Array.from(target.files)
      for (const file of files) {
        const res = await uploadFile(file, 'order')
        imageFileIds.value.push(String(res.data.id))
        imageList.value.push(res.data.url)
      }
      form.images = JSON.stringify(imageFileIds.value)
    } catch (error: any) {
      ElMessage.error(error.message || '图片上传失败')
    } finally {
      target.value = ''
    }
  }
}

function removeImage(index: number) {
  imageList.value.splice(index, 1)
  imageFileIds.value.splice(index, 1)
  form.images = JSON.stringify(imageFileIds.value)
}

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning(submitValidationMessage.value || '请完善订单信息后再提交')
    return
  }

  // 去掉电话中的 + 号、空格、横杠等，只保留纯数字
  const cleanPhone = form.customerPhone ? form.customerPhone.replace(/[\s\-+]/g, '') : undefined

  try {
    const data = {
      customerId: form.customerId,
      customerName: form.customerName,
      customerPhone: cleanPhone,
      customerAddress: form.customerAddress || undefined,
      paymentStatus: form.paymentStatus,
      depositAmount: form.paymentStatus === 1 ? form.depositAmount : undefined,
      needDelivery: needDelivery.value ? 1 : 0,
      deliveryAddress: needDelivery.value ? form.deliveryAddress : undefined,
      warehouseId: form.warehouseId,
      remark: form.remark || undefined,
      images: imageFileIds.value.length > 0 ? form.images : undefined,
      items: form.items.map(item => ({
        skuId: item.skuId,
        quantity: item.quantity,
        price: item.price,
      })),
    }

    const res = await createOrder(data)
    ElMessage.success('订单创建成功')
    router.push(`/orders/${res.data}`)
  } catch (error: any) {
    ElMessage.error(error.message || '创建订单失败')
  }
}
</script>

<style scoped>
.order-form-page {
  padding: 0;
}

/* 输入框样式 */
.order-form-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.order-form-input :deep(.el-input__inner) {
  color: #1a1a2e !important;
  font-size: 14px !important;
}

/* 文本域样式 */
.order-form-textarea :deep(.el-textarea__inner) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  padding: 12px 16px !important;
  color: #1a1a2e !important;
}

/* 弹窗样式 */
.product-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
}

.product-dialog :deep(.el-dialog__header) {
  padding: 20px 24px !important;
  border-bottom: 1px solid rgb(243 244 246) !important;
}

.product-dialog :deep(.el-dialog__body) {
  padding: 16px 24px 24px !important;
}
</style>
