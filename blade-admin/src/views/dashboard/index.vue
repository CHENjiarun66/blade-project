<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="stat in stats" :key="stat.title">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: stat.color }">
            <el-icon :size="24"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-title">{{ stat.title }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card class="chart-card">
          <template #header>
            <span>销售趋势</span>
          </template>
          <div class="chart-placeholder">
            <el-icon :size="48" color="#ccc"><DataAnalysis /></el-icon>
            <p>图表区域</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="chart-card">
          <template #header>
            <span>热销商品</span>
          </template>
          <div class="chart-placeholder">
            <el-icon :size="48" color="#ccc"><TrendCharts /></el-icon>
            <p>图表区域</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { shallowRef } from 'vue'
import { Goods, Money, ShoppingCart, Odometer, DataAnalysis, TrendCharts } from '@element-plus/icons-vue'

const stats = shallowRef([
  { title: '今日订单', value: '128', icon: ShoppingCart, color: '#409eff' },
  { title: '今日销售额', value: '¥12,580', icon: Money, color: '#67c23a' },
  { title: '商品数量', value: '2,456', icon: Goods, color: '#e6a23c' },
  { title: '待处理订单', value: '23', icon: Odometer, color: '#f56c6c' },
])
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.stat-title {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.chart-card {
  height: 360px;
}

.chart-card :deep(.el-card__header) {
  font-weight: 600;
}

.chart-placeholder {
  height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}
</style>
