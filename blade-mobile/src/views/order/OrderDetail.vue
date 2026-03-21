<template>
  <v-container>
    <v-btn
      icon="mdi-arrow-left"
      variant="text"
      @click="router.back()"
      class="mb-2"
    />

    <v-skeleton-loader v-if="loading" type="card" />

    <div v-if="!loading && order">
      <v-card class="mb-4">
        <v-card-title class="d-flex justify-space-between">
          <span>{{ order.orderNo }}</span>
          <v-chip :color="getStatusColor(order.status)" variant="tonal">
            {{ order.statusName }}
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">客户名称</div>
              <div>{{ order.customerName }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">联系电话</div>
              <div>{{ order.customerPhone }}</div>
            </v-col>
            <v-col cols="12">
              <div class="text-caption text-medium-emphasis">收货地址</div>
              <div>{{ order.customerAddress }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">发货仓库</div>
              <div>{{ order.warehouseName || order.warehouseId }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">订单金额</div>
              <div class="text-primary font-weight-bold">¥{{ order.totalAmount }}</div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <v-card class="mb-4">
        <v-card-title>订单明细</v-card-title>
        <v-card-text>
          <v-list density="compact">
            <v-list-item
              v-for="item in order.items"
              :key="item.id"
              class="px-0"
            >
              <template #prepend>
                <v-avatar size="40" color="primary" variant="tonal">
                  <v-icon size="small">mdi-tshirt-crew</v-icon>
                </v-avatar>
              </template>
              <v-list-item-title>{{ item.productName || item.skuCode }}</v-list-item-title>
              <v-list-item-subtitle>
                {{ item.colorName }} | {{ item.sizeName }}
              </v-list-item-subtitle>
              <template #append>
                <div class="text-right">
                  <div>×{{ item.quantity }}</div>
                  <div class="text-primary">¥{{ item.subtotal }}</div>
                </div>
              </template>
            </v-list-item>
          </v-list>
        </v-card-text>
      </v-card>

      <v-card class="mb-4" v-if="order.remark">
        <v-card-title>备注</v-card-title>
        <v-card-text>{{ order.remark }}</v-card-text>
      </v-card>

      <v-card class="mb-4">
        <v-card-title>操作记录</v-card-title>
        <v-card-text>
          <v-timeline density="compact" side="end">
            <v-timeline-item
              v-if="order.createTime"
              dot-color="grey"
              size="small"
            >
              <div class="text-caption">创建订单</div>
              <div class="text-caption text-medium-emphasis">{{ order.createTime }}</div>
            </v-timeline-item>
            <v-timeline-item
              v-if="order.payTime"
              dot-color="info"
              size="small"
            >
              <div class="text-caption">支付确认</div>
              <div class="text-caption text-medium-emphasis">{{ order.payTime }}</div>
            </v-timeline-item>
            <v-timeline-item
              v-if="order.confirmTime"
              dot-color="primary"
              size="small"
            >
              <div class="text-caption">确认订单</div>
              <div class="text-caption text-medium-emphasis">{{ order.confirmTime }}</div>
            </v-timeline-item>
            <v-timeline-item
              v-if="order.deliverTime"
              dot-color="warning"
              size="small"
            >
              <div class="text-caption">已发货</div>
              <div class="text-caption text-medium-emphasis">{{ order.deliverTime }}</div>
            </v-timeline-item>
            <v-timeline-item
              v-if="order.completeTime"
              dot-color="success"
              size="small"
            >
              <div class="text-caption">已完成</div>
              <div class="text-caption text-medium-emphasis">{{ order.completeTime }}</div>
            </v-timeline-item>
          </v-timeline>
        </v-card-text>
      </v-card>

      <div class="d-flex gap-2 flex-wrap">
        <v-btn
          v-if="order.status === 0"
          color="primary"
          @click="handleConfirmPayment"
          :loading="actionLoading"
        >
          确认付款
        </v-btn>
        <v-btn
          v-if="order.status === 1"
          color="primary"
          @click="handleDeliver"
          :loading="actionLoading"
        >
          发货
        </v-btn>
        <v-btn
          v-if="order.status === 2"
          color="success"
          @click="handleComplete"
          :loading="actionLoading"
        >
          完成
        </v-btn>
        <v-btn
          v-if="order.status === 0 || order.status === 1"
          color="error"
          variant="outlined"
          @click="handleCancel"
          :loading="actionLoading"
        >
          取消订单
        </v-btn>
      </div>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getOrderById,
  confirmPayment,
  deliverOrder,
  completeOrder,
  cancelOrder
} from '@/api/order'
import type { OrderVO } from '@/types/order'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const actionLoading = ref(false)
const order = ref<OrderVO | null>(null)

async function fetchOrder() {
  loading.value = true
  try {
    const res = await getOrderById(route.params.id as any)
    order.value = res.data
  } catch (error: any) {
    showToast(error.message || '获取订单详情失败', 'error')
  } finally {
    loading.value = false
  }
}

async function handleConfirmPayment() {
  if (!order.value) return
  actionLoading.value = true
  try {
    await confirmPayment(order.value.id, order.value.totalAmount)
    showToast('付款确认成功', 'success')
    fetchOrder()
  } catch (error: any) {
    showToast(error.response?.data?.message || '操作失败', 'error')
  } finally {
    actionLoading.value = false
  }
}

async function handleDeliver() {
  if (!order.value) return
  actionLoading.value = true
  try {
    await deliverOrder(order.value.id)
    showToast('发货成功', 'success')
    fetchOrder()
  } catch (error: any) {
    showToast(error.response?.data?.message || '操作失败', 'error')
  } finally {
    actionLoading.value = false
  }
}

async function handleComplete() {
  if (!order.value) return
  actionLoading.value = true
  try {
    await completeOrder(order.value.id)
    showToast('订单已完成', 'success')
    fetchOrder()
  } catch (error: any) {
    showToast(error.response?.data?.message || '操作失败', 'error')
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel() {
  const reason = prompt('请输入取消原因：')
  if (!reason) return
  if (!order.value) return
  actionLoading.value = true
  try {
    await cancelOrder(order.value.id, reason)
    showToast('订单已取消', 'success')
    fetchOrder()
  } catch (error: any) {
    showToast(error.response?.data?.message || '操作失败', 'error')
  } finally {
    actionLoading.value = false
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

onMounted(() => {
  fetchOrder()
})
</script>
