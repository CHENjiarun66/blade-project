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

      <!-- 收款与履约事实（新模型；历史行仅展示旧字段） -->
      <v-card v-if="order" class="mb-4" elevation="1">
        <v-card-text class="py-3">
          <div class="d-flex flex-wrap gap-2 align-center">
            <v-chip size="small" :color="collectionChipColor" variant="tonal">
              收款：{{ collectionLabel }}
            </v-chip>
            <v-chip v-if="order.settlementMethod" size="small" variant="tonal">
              结清：{{ settlementLabel }}
            </v-chip>
            <v-chip size="small" variant="tonal">
              履约：{{ fulfillmentModeLabel }}
            </v-chip>
            <v-chip v-if="order.legacyUnmigrated" size="small" color="warning" variant="tonal">
              历史未迁移
            </v-chip>
          </div>
        </v-card-text>
      </v-card>

      <!-- 操作按钮：按后端 allowedActions 白名单展示 -->
      <div class="d-flex gap-2 flex-wrap">
        <v-btn
          v-if="hasAction('recordPayment')"
          color="primary"
          variant="outlined"
          @click="openAddPayment"
        >
          加收金额
        </v-btn>
        <v-btn
          v-if="hasAction('recordPayment')"
          color="primary"
          @click="openSettlement"
        >
          确认收款
        </v-btn>
        <v-btn
          v-if="hasAction('shipOrder')"
          color="primary"
          @click="handleDeliver"
          :loading="actionLoading"
        >
          发货
        </v-btn>
        <v-btn
          v-if="hasAction('completeOrder')"
          color="success"
          @click="handleComplete"
          :loading="actionLoading"
        >
          完成
        </v-btn>
        <v-btn
          v-if="hasAction('cancelOrder')"
          color="error"
          variant="outlined"
          @click="handleCancel"
          :loading="actionLoading"
        >
          取消订单
        </v-btn>
      </div>

      <v-dialog v-model="addPaymentDialog" max-width="460" persistent>
        <v-card rounded="xl">
          <v-card-title class="pt-5 px-5">加收金额</v-card-title>
          <v-card-text class="px-5">
            <v-sheet color="grey-lighten-4" rounded="lg" class="pa-4 mb-4">
              <div class="d-flex justify-space-between mb-2"><span>订单总额</span><strong>¥{{ money(order.totalAmount) }}</strong></div>
              <div class="d-flex justify-space-between mb-2"><span>已收款金额</span><strong class="text-primary">¥{{ money(currentReceived) }}</strong></div>
              <div class="d-flex justify-space-between"><span>待收尾款</span><strong class="text-error">¥{{ money(currentBalance) }}</strong></div>
            </v-sheet>
            <v-text-field v-model.number="addAmount" type="number" min="0.01" :max="currentBalance" step="0.01" label="加收金额" prefix="¥" variant="outlined" />
            <v-row dense>
              <v-col cols="6"><v-sheet color="blue-lighten-5" rounded="lg" class="pa-3"><div class="text-caption">加收后累计实收</div><strong>¥{{ money(addResultReceived) }}</strong></v-sheet></v-col>
              <v-col cols="6"><v-sheet color="grey-lighten-4" rounded="lg" class="pa-3"><div class="text-caption">加收后剩余尾款</div><strong>¥{{ money(addResultBalance) }}</strong></v-sheet></v-col>
            </v-row>
            <div class="text-caption text-medium-emphasis mt-3">此操作只记录新增收款，不会自动核销尾款。</div>
          </v-card-text>
          <v-card-actions class="px-5 pb-5">
            <v-spacer />
            <v-btn variant="text" :disabled="actionLoading" @click="addPaymentDialog = false">取消</v-btn>
            <v-btn color="primary" :loading="actionLoading" @click="submitAddPayment">确认加收</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>

      <v-dialog v-model="settlementDialog" max-width="500" persistent>
        <v-card rounded="xl">
          <v-card-title class="pt-5 px-5">确认收款</v-card-title>
          <v-card-text class="px-5">
            <v-sheet color="grey-lighten-4" rounded="lg" class="pa-4 mb-4">
              <div class="d-flex justify-space-between mb-2"><span>订单总额</span><strong>¥{{ money(order.totalAmount) }}</strong></div>
              <div class="d-flex justify-space-between mb-2"><span>当前实收金额</span><strong class="text-primary">¥{{ money(currentReceived) }}</strong></div>
              <div class="d-flex justify-space-between"><span>待收尾款</span><strong class="text-error">¥{{ money(currentBalance) }}</strong></div>
            </v-sheet>
            <v-text-field v-model.number="finalReceived" type="number" :min="currentReceived" :max="effectiveReceivable" step="0.01" label="最终累计实收金额" prefix="¥" variant="outlined" hint="系统会自动计算本次增收与短款" persistent-hint />
            <v-row dense class="mt-2">
              <v-col cols="6"><v-sheet color="blue-lighten-5" rounded="lg" class="pa-3"><div class="text-caption">本次新增收款</div><strong>¥{{ money(settlementAdditional) }}</strong></v-sheet></v-col>
              <v-col cols="6"><v-sheet color="amber-lighten-5" rounded="lg" class="pa-3"><div class="text-caption">短款核销金额</div><strong>¥{{ money(settlementWriteOff) }}</strong></v-sheet></v-col>
            </v-row>
            <v-textarea v-if="settlementWriteOff > 0" v-model="writeOffReason" class="mt-4" label="短款核销原因（必填）" variant="outlined" rows="2" placeholder="如：客户少付5元，确认不再追收" />
            <v-alert v-if="settlementWriteOff > 0 && !hasAction('settleWithWriteOff')" type="warning" variant="tonal" density="compact" class="mt-3">当前账号无短款核销权限。</v-alert>
          </v-card-text>
          <v-card-actions class="px-5 pb-5">
            <v-spacer />
            <v-btn variant="text" :disabled="actionLoading" @click="settlementDialog = false">取消</v-btn>
            <v-btn color="primary" :loading="actionLoading" @click="submitSettlement">{{ settlementWriteOff > 0 ? '确认并核销' : '确认收齐' }}</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getOrderById,
  addPayment,
  confirmSettlement,
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
const addPaymentDialog = ref(false)
const settlementDialog = ref(false)
const addAmount = ref(0)
const finalReceived = ref(0)
const writeOffReason = ref('')
const settlementKey = ref('')

const roundMoney = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100
const money = (value: number | undefined | null) => Number(value ?? 0).toFixed(2)
const currentReceived = computed(() => roundMoney(Number(order.value?.netReceivedAmount ?? order.value?.paidAmount ?? 0)))
const currentBalance = computed(() => roundMoney(Number(order.value?.balanceAmount ?? 0)))
const effectiveReceivable = computed(() => roundMoney(currentReceived.value + currentBalance.value))
const addResultReceived = computed(() => roundMoney(currentReceived.value + Number(addAmount.value || 0)))
const addResultBalance = computed(() => roundMoney(Math.max(0, currentBalance.value - Number(addAmount.value || 0))))
const settlementAdditional = computed(() => roundMoney(Math.max(0, Number(finalReceived.value || 0) - currentReceived.value)))
const settlementWriteOff = computed(() => roundMoney(Math.max(0, effectiveReceivable.value - Number(finalReceived.value || 0))))

// 新模型：后端按状态+权限计算可用动作；历史未迁移行允许旧动作兜底
function hasAction(action: string): boolean {
  if (!order.value) return false
  const list = (order.value.allowedActions ?? []) as unknown as string[]
  if (list && list.length > 0) return list.includes(action)
  // 旧响应兼容：按旧数字状态判断（不提交任何数字状态）
  const s = order.value.status
  if (action === 'recordPayment') return s === 0
  if (action === 'settleWithWriteOff') return false
  if (action === 'shipOrder') return s === 3 || s === 1
  if (action === 'completeOrder') return s === 4
  if (action === 'cancelOrder') return s === 0 || s === 1
  return false
}

const collectionLabel = computed(() => {
  const map: Record<string, string> = { UNPAID: '未收款', PARTIAL: '部分收款', SETTLED: '已结清' }
  const v = order.value?.collectionStatus
  if (v) return map[v] ?? v
  const legacyMap: Record<number, string> = { 0: '未付款', 1: '部分收款', 2: '已结清' }
  return order.value?.paymentStatus != null ? legacyMap[order.value.paymentStatus] ?? '未知' : '—'
})
const collectionChipColor = computed(() => {
  const v = order.value?.collectionStatus
  if (v === 'SETTLED') return 'success'
  if (v === 'PARTIAL') return 'warning'
  if (v === 'UNPAID') return 'error'
  return 'grey'
})
const settlementLabel = computed(() => {
  const map: Record<string, string> = { FULL_RECEIPT: '足额收款', WRITE_OFF: '短款结清', MIGRATION_CONFIRMED: '迁移确认' }
  return order.value?.settlementMethod ? map[order.value.settlementMethod] ?? order.value.settlementMethod : '—'
})
const fulfillmentModeLabel = computed(() => {
  const map: Record<string, string> = { UNDECIDED: '尚未选择', STOCK_LINKED: '关联库存', RECORD_ONLY: '仅记录订单' }
  return order.value?.fulfillmentMode ? map[order.value.fulfillmentMode] ?? order.value.fulfillmentMode : '—'
})

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

function openAddPayment() {
  addAmount.value = 0
  addPaymentDialog.value = true
}

function openSettlement() {
  if (!order.value) return
  finalReceived.value = currentReceived.value
  writeOffReason.value = ''
  settlementKey.value = typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `settle-${order.value.id}-${Date.now()}`
  settlementDialog.value = true
}

async function submitAddPayment() {
  if (!order.value || actionLoading.value) return
  if (addAmount.value <= 0 || addAmount.value > currentBalance.value) {
    showToast('加收金额必须大于0且不能超过待收尾款', 'warning')
    return
  }
  actionLoading.value = true
  try {
    await addPayment(order.value.id, addAmount.value)
    showToast('加收金额已记录', 'success')
    addPaymentDialog.value = false
    await fetchOrder()
  } catch (error: any) {
    showToast(error.response?.data?.message || '操作失败', 'error')
  } finally {
    actionLoading.value = false
  }
}

async function submitSettlement() {
  if (!order.value || actionLoading.value) return
  if (finalReceived.value < currentReceived.value || finalReceived.value > effectiveReceivable.value) {
    showToast('最终实收金额必须在当前实收与有效应收之间', 'warning')
    return
  }
  if (settlementWriteOff.value > 0 && (!hasAction('settleWithWriteOff') || !writeOffReason.value.trim())) {
    showToast(hasAction('settleWithWriteOff') ? '请填写短款核销原因' : '当前账号无短款核销权限', 'warning')
    return
  }
  actionLoading.value = true
  try {
    await confirmSettlement(order.value.id, {
      finalReceivedAmount: finalReceived.value,
      writeOffReason: settlementWriteOff.value > 0 ? writeOffReason.value.trim() : undefined,
      idempotencyKey: settlementKey.value,
    })
    showToast(settlementWriteOff.value > 0 ? '收款及短款核销已确认' : '订单已足额收款', 'success')
    settlementDialog.value = false
    await fetchOrder()
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
