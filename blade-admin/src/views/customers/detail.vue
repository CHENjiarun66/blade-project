<template>
  <div class="customer-detail page-container">
    <!-- 顶部返回 -->
    <div class="flex items-center gap-3 mb-6">
      <el-button text @click="$router.back()">
        <span class="material-symbols-outlined text-lg">arrow_back</span>
      </el-button>
      <div>
        <h2 class="text-xl font-bold text-gray-900">{{ customer?.name }}</h2>
        <p v-if="customer" class="text-xs text-gray-400 mt-0.5">
          {{ customer.countryName || (customer.countryCode ? getCountryNameZh(customer.countryCode) : '未设置国家') }}
          <span v-if="customer.phones && customer.phones.length"> · {{ formatPhone(customer) }}</span>
        </p>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="customer-tabs">
      <!-- Tab 1: 基本信息 -->
      <el-tab-pane label="基本信息" name="info">
        <div v-loading="infoLoading" class="bg-white rounded-xl p-8 shadow-sm max-w-2xl">
          <div v-if="customer" class="space-y-5">
            <div class="info-row">
              <span class="info-label">客户名称</span>
              <span class="info-value font-semibold">{{ customer.name }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">国家/区号</span>
              <span class="info-value">
                <template v-if="customer.countryCode">
                  {{ customer.countryName || getCountryNameZh(customer.countryCode) }} ({{ customer.countryCode }})
                </template>
                <span v-else class="text-gray-400">未设置</span>
              </span>
            </div>
            <div class="info-row">
              <span class="info-label">联系电话</span>
              <span class="info-value">{{ customer.phones?.join(', ') || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">地址</span>
              <span class="info-value text-gray-600">{{ customer.address || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">备注</span>
              <span class="info-value text-gray-500 text-sm">{{ customer.remark || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">创建时间</span>
              <span class="info-value text-gray-400 text-sm">{{ formatDate(customer.createTime) }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 订单记录 -->
      <el-tab-pane label="订单记录" name="orders">
        <div v-loading="ordersLoading">
          <div v-if="customerOrders.length === 0" class="text-center py-16 text-gray-400">
            <span class="material-symbols-outlined text-5xl mb-3">receipt_long</span>
            <p>暂无订单记录</p>
          </div>

          <div v-else class="space-y-4">
            <div
              v-for="order in customerOrders"
              :key="order.id"
              class="bg-white rounded-xl p-5 shadow-sm cursor-pointer hover:shadow-md transition-shadow"
              @click="$router.push(`/orders/${order.id}`)"
            >
              <div class="flex justify-between items-start mb-3">
                <div>
                  <span class="font-bold text-gray-900 mr-3">{{ order.orderNo }}</span>
                  <el-tag size="small" :type="statusType(order.status)">{{ order.statusName }}</el-tag>
                </div>
                <span class="text-xs text-gray-400">{{ formatDate(order.createTime) }}</span>
              </div>

              <!-- 订单项 -->
              <div v-if="order.items && order.items.length" class="mb-3 space-y-1">
                <div v-for="(item, idx) in order.items.slice(0, 3)" :key="idx" class="flex gap-2 text-sm text-gray-600">
                  <span class="text-gray-400 w-4 shrink-0">{{ Number(idx) + 1 }}.</span>
                  <span>{{ item.productName }}</span>
                  <span class="text-gray-400">{{ item.skuDesc }}</span>
                  <span class="ml-auto">×{{ item.quantity }}</span>
                </div>
                <div v-if="order.items.length > 3" class="text-xs text-gray-400 pl-6">
                  还有 {{ order.items.length - 3 }} 项...
                </div>
              </div>

              <div class="flex justify-between items-center pt-3 border-t border-gray-100">
                <span class="text-xs text-gray-400">共 {{ order.totalQuantity }} 件</span>
                <div class="flex gap-4 text-sm">
                  <span class="text-gray-500">实付</span>
                  <span class="font-bold text-gray-900">{{ order.paidAmountText }}</span>
                  <span class="text-gray-300">/</span>
                  <span class="text-gray-500">{{ order.totalAmountText }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 商品偏好 -->
      <el-tab-pane label="商品偏好" name="preference">
        <div v-loading="prefLoading">
          <div v-if="!preference || preference.productTypeCount === 0" class="text-center py-16 text-gray-400">
            <span class="material-symbols-outlined text-5xl mb-3">analytics</span>
            <p>暂无偏好数据（需要已完成/已发货的订单）</p>
          </div>

          <div v-else class="space-y-6">
            <!-- 概览卡片 -->
            <div class="grid grid-cols-3 gap-4">
              <div class="bg-white rounded-xl p-5 shadow-sm text-center">
                <div class="text-2xl font-bold text-[#408aee]">{{ preference.productTypeCount }}</div>
                <div class="text-xs text-gray-400 mt-1">购买品类数</div>
              </div>
              <div class="bg-white rounded-xl p-5 shadow-sm text-center">
                <div class="text-2xl font-bold text-[#408aee]">{{ totalOrderedItems }}</div>
                <div class="text-xs text-gray-400 mt-1">已购商品件数</div>
              </div>
              <div class="bg-white rounded-xl p-5 shadow-sm text-center">
                <div class="text-2xl font-bold text-[#408aee]">{{ preference.colors.length }}</div>
                <div class="text-xs text-gray-400 mt-1">偏好颜色种类</div>
              </div>
            </div>

            <!-- 颜色偏好 -->
            <div v-if="preference.colors.length" class="bg-white rounded-xl p-6 shadow-sm">
              <h3 class="text-sm font-bold text-gray-700 mb-4 uppercase tracking-wide flex items-center gap-2">
                <span class="material-symbols-outlined text-base text-[#408aee]">palette</span>
                颜色偏好
              </h3>
              <div class="space-y-3">
                <div v-for="color in preference.colors" :key="color.colorName" class="flex items-center gap-3">
                  <span class="text-sm text-gray-600 w-16">{{ color.colorName }}</span>
                  <div class="flex-1 bg-gray-100 rounded-full h-6 overflow-hidden">
                    <div
                      class="h-full rounded-full transition-all"
                      :style="{ width: color.percentage + '%', backgroundColor: colorBar(color.colorName) }"
                    />
                  </div>
                  <span class="text-xs text-gray-400 w-12 text-right">{{ color.percentage }}%</span>
                  <span class="text-xs text-gray-400 w-8 text-right">{{ color.count }}件</span>
                </div>
              </div>
            </div>

            <!-- 尺码偏好 -->
            <div v-if="preference.sizes.length" class="bg-white rounded-xl p-6 shadow-sm">
              <h3 class="text-sm font-bold text-gray-700 mb-4 uppercase tracking-wide flex items-center gap-2">
                <span class="material-symbols-outlined text-base text-[#408aee]">straighten</span>
                尺码偏好
              </h3>
              <div class="grid grid-cols-5 gap-3">
                <div v-for="size in preference.sizes" :key="size.sizeName" class="text-center">
                  <div class="bg-[#408aee]/10 rounded-xl py-3 mb-1">
                    <div class="text-lg font-bold text-[#408aee]">{{ size.percentage }}%</div>
                  </div>
                  <div class="text-sm font-medium text-gray-700">{{ size.sizeName }}</div>
                  <div class="text-xs text-gray-400">{{ size.count }}件</div>
                </div>
              </div>
            </div>

            <!-- 品类偏好 -->
            <div v-if="preference.categories.length" class="bg-white rounded-xl p-6 shadow-sm">
              <h3 class="text-sm font-bold text-gray-700 mb-4 uppercase tracking-wide flex items-center gap-2">
                <span class="material-symbols-outlined text-base text-[#408aee]">category</span>
                商品偏好
              </h3>
              <div class="space-y-3">
                <div v-for="cat in preference.categories" :key="cat.categoryName" class="flex items-center gap-3">
                  <span class="text-sm text-gray-600 w-32 truncate">{{ cat.categoryName }}</span>
                  <div class="flex-1 bg-gray-100 rounded-full h-6 overflow-hidden">
                    <div
                      class="h-full bg-[#408aee] rounded-full transition-all"
                      :style="{ width: cat.percentage + '%' }"
                    />
                  </div>
                  <span class="text-xs text-gray-400 w-12 text-right">{{ cat.percentage }}%</span>
                  <span class="text-xs text-gray-400 w-8 text-right">{{ cat.count }}件</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 4: WhatsApp -->
      <el-tab-pane v-if="canViewWhatsapp" label="WhatsApp" name="whatsapp" lazy>
        <CustomerWhatsappWorkspace :customer-id="customerId" :customer-name="customer?.name || '客户'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCustomerById, getCustomerOrders, getCustomerPreference } from '@/api/customer'
import type { CustomerVO } from '@/api/customer'
import { getCountryNameZh } from '@/data/countries'
import { formatDate } from '@/utils/format'
import CustomerWhatsappWorkspace from '@/components/whatsapp/CustomerWhatsappWorkspace.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const customerId = Number(route.params.id)
const canViewWhatsapp = computed(() => authStore.permissions.includes('menu:whatsapp'))

const activeTab = ref('info')
const infoLoading = ref(false)
const ordersLoading = ref(false)
const prefLoading = ref(false)

const customer = ref<CustomerVO | null>(null)
const customerOrders = ref<any[]>([])
const preference = ref<any | null>(null)

const totalOrderedItems = computed(() => {
  if (!preference.value) return 0
  const total = preference.value.categories?.reduce((s: number, c: any) => s + c.count, 0) || 0
  return total
})

function formatPhone(c: CustomerVO) {
  if (!c.phones?.length) return ''
  return c.phones.join(', ')
}

function statusType(status: number): string {
  const map: Record<number, string> = {
    0: 'info', 1: 'success', 2: 'warning', 3: 'warning',
    4: 'primary', 5: 'success', 6: 'danger',
  }
  return map[status] || 'info'
}

// Simple color map for bar rendering
const colorMap: Record<string, string> = {
  '黑色': '#1f2937', '白色': '#f3f4f6', '红色': '#ef4444', '蓝色': '#3b82f6',
  '绿色': '#22c55e', '黄色': '#eab308', '紫色': '#a855f7', '橙色': '#f97316',
  '粉色': '#ec4899', '灰色': '#6b7280', '棕色': '#92400e', '米色': '#d6c5a8',
  '藏青': '#1e3a5f', '酒红': '#722f37', '墨绿': '#1a4731',
}
function colorBar(name: string): string {
  return colorMap[name] || '#408aee'
}

async function loadInfo() {
  infoLoading.value = true
  try {
    const res = await getCustomerById(customerId)
    customer.value = res.data
  } finally {
    infoLoading.value = false
  }
}

async function loadOrders() {
  ordersLoading.value = true
  try {
    const res = await getCustomerOrders(customerId)
    customerOrders.value = res.data.records || []
  } finally {
    ordersLoading.value = false
  }
}

async function loadPreference() {
  prefLoading.value = true
  try {
    const res = await getCustomerPreference(customerId)
    preference.value = res.data
  } catch {
    preference.value = null
  } finally {
    prefLoading.value = false
  }
}

onMounted(() => {
  loadInfo()
})

watch(activeTab, (tab) => {
  if (tab === 'orders' && customerOrders.value.length === 0) {
    loadOrders()
  } else if (tab === 'preference' && preference.value === null) {
    loadPreference()
  }
})
</script>

<style scoped>
.customer-detail {
  padding: 0;
}

.customer-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f3f4f6;
}
.info-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-label {
  font-size: 13px;
  color: #9ca3af;
  font-weight: 500;
  width: 80px;
  flex-shrink: 0;
  padding-top: 2px;
}

.info-value {
  font-size: 14px;
  color: #374151;
  flex: 1;
}
</style>
