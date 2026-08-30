<template>
  <v-container>
    <div class="text-h6 mb-4">订单列表</div>
    <v-text-field
      v-model="search"
      prepend-inner-icon="mdi-magnify"
      label="搜索订单号/客户名"
      density="compact"
      hide-details
      class="mb-4"
    />

    <v-tabs v-model="statusFilter" color="primary" density="compact" class="mb-4">
      <v-tab value="">全部</v-tab>
      <v-tab value="CONFIRMED">已确认</v-tab>
      <v-tab value="WAITING_ALLOCATION">待配货</v-tab>
      <v-tab value="ALLOCATING">配货中</v-tab>
      <v-tab value="READY_TO_SHIP">待发货</v-tab>
      <v-tab value="SHIPPED">已发货</v-tab>
      <v-tab value="COMPLETED">已完成</v-tab>
      <v-tab value="CANCELLED">已取消</v-tab>
    </v-tabs>

    <v-list lines="two">
      <v-list-item
        v-for="order in orders"
        :key="order.id"
        :to="{ name: 'OrderDetail', params: { id: order.id } }"
        class="mb-2"
      >
        <template #prepend>
          <v-avatar color="primary" variant="tonal">
            <v-icon>mdi-clipboard-text</v-icon>
          </v-avatar>
        </template>

        <v-list-item-title>{{ order.orderNo }}</v-list-item-title>
        <v-list-item-subtitle>
          {{ order.customerName }} | {{ order.statusName }}
        </v-list-item-subtitle>

        <template #append>
          <div class="text-right">
            <div class="text-body-2">¥{{ order.totalAmount }}</div>
            <v-chip size="x-small" :color="getStatusColor(order.status)" variant="tonal">
              {{ order.statusName }}
            </v-chip>
          </div>
        </template>
      </v-list-item>
    </v-list>

    <v-skeleton-loader v-if="loading" type="list-item-two-line" />

    <v-empty-state
      v-if="!loading && orders.length === 0"
      icon="mdi-clipboard-text-outline"
      title="暂无订单"
      text="点击下方按钮创建新订单"
    />

    <v-btn
      color="primary"
      icon
      size="large"
      to="/orders/create"
      class="fab"
      elevation="4"
    >
      <v-icon>mdi-plus</v-icon>
    </v-btn>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getOrderList } from '@/api/order'
import type { OrderVO, OrderPageDTO } from '@/types/order'
import { showToast } from '@/utils/toast'

const search = ref('')
const statusFilter = ref('')
const loading = ref(false)
const orders = ref<OrderVO[]>([])

async function fetchOrders() {
  loading.value = true
  try {
    const params: OrderPageDTO = {
      current: 1,
      size: 20
    }
    if (search.value) {
      params.orderNo = search.value
      params.customerName = search.value
    }
    if (statusFilter.value !== '') {
      // 新模型：按履约状态字符串筛选（不提交数字状态）
      ;(params as any).fulfillmentStatus = statusFilter.value
    }
    const res = await getOrderList(params)
    orders.value = res.data.records
  } catch (error: any) {
    showToast(error.message || '获取订单列表失败', 'error')
  } finally {
    loading.value = false
  }
}

function getStatusColor(status: number) {
  const colors: Record<number, string> = {
    0: 'warning',
    1: 'info',
    2: 'primary',
    3: 'success',
    4: 'error',
    5: 'orange',
    6: 'grey'
  }
  return colors[status] || 'grey'
}

watch([search, statusFilter], () => {
  fetchOrders()
})

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.fab {
  position: fixed;
  bottom: 80px;
  right: 16px;
}
</style>
