<template>
  <v-container>
    <div class="text-h6 mb-4">数据看板</div>

    <v-row>
      <v-col cols="6">
        <v-card color="primary" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">{{ stats.todayOrders }}</div>
            <div class="text-caption">今日订单</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="6">
        <v-card color="success" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">¥{{ stats.todayAmount }}</div>
            <div class="text-caption">今日销售额</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="6">
        <v-card color="warning" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">{{ stats.pendingOrders }}</div>
            <div class="text-caption">待处理订单</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="6">
        <v-card color="error" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">{{ stats.lowStock }}</div>
            <div class="text-caption">库存预警</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-card class="mt-4">
      <v-card-title>热销商品 Top 5</v-card-title>
      <v-card-text>
        <v-list density="compact">
          <v-list-item
            v-for="(item, index) in topProducts"
            :key="index"
          >
            <template #prepend>
              <v-avatar
                size="32"
                :color="index === 0 ? 'amber' : index === 1 ? 'grey' : index === 2 ? 'orange' : 'primary'"
                variant="tonal"
              >
                {{ index + 1 }}
              </v-avatar>
            </template>
            <v-list-item-title>{{ item.name }}</v-list-item-title>
            <template #append>
              <span class="text-primary font-weight-bold">{{ item.sales }}</span>
            </template>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>

    <v-card class="mt-4">
      <v-card-title>近 7 天订单趋势</v-card-title>
      <v-card-text>
        <div class="d-flex justify-space-between align-end">
          <div
            v-for="(day, index) in orderTrend"
            :key="index"
            class="text-center"
          >
            <div class="text-h6">{{ day.count }}</div>
            <div class="text-caption">{{ day.day }}</div>
          </div>
        </div>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const stats = ref({
  todayOrders: 0,
  todayAmount: 0,
  pendingOrders: 0,
  lowStock: 0
})

const topProducts = ref<{ name: string; sales: number }[]>([])

const orderTrend = ref<{ day: string; count: number }[]>([
  { day: '周一', count: 0 },
  { day: '周二', count: 0 },
  { day: '周三', count: 0 },
  { day: '周四', count: 0 },
  { day: '周五', count: 0 },
  { day: '周六', count: 0 },
  { day: '周日', count: 0 }
])

onMounted(() => {
  // TODO: 调用后端 API 获取数据
  // 暂时使用模拟数据
  stats.value = {
    todayOrders: 12,
    todayAmount: 15880,
    pendingOrders: 5,
    lowStock: 3
  }
  topProducts.value = [
    { name: '夏季连衣裙', sales: 45 },
    { name: '休闲T恤', sales: 38 },
    { name: '牛仔裤', sales: 32 },
    { name: '运动鞋', sales: 28 },
    { name: '防晒衫', sales: 21 }
  ]
})
</script>
