<template>
  <div class="dashboard">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-6">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">仪表盘</h2>
        <p class="text-gray-500 text-sm">实时查看您的销售数据概览。</p>
      </div>
      <!-- 日期筛选 -->
      <div class="flex items-center gap-3">
        <el-radio-group v-model="selectedPeriod" @change="onPeriodChange">
          <el-radio-button value="TODAY">今日</el-radio-button>
          <el-radio-button value="WEEK">本周</el-radio-button>
          <el-radio-button value="MONTH">本月</el-radio-button>
          <el-radio-button value="QUARTER">本季</el-radio-button>
          <el-radio-button value="YEAR">本年</el-radio-button>
          <el-radio-button value="CUSTOM">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="selectedPeriod === 'CUSTOM'"
          v-model="customDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="onPeriodChange"
        />
      </div>
    </div>

    <!-- 统计卡片 - 第一行 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #ecf4ff">
              <el-icon :size="20" color="#408aee"><ShoppingCart /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">{{ stats.periodOrders || 0 }}</div>
              <div class="text-xs text-gray-500 mt-0.5">{{ periodLabel }}订单</div>
              <div v-if="stats.periodOrdersTrend !== 0" class="text-xs mt-0.5" :class="getTrendClass(stats.periodOrdersTrend)">
                {{ formatTrend(stats.periodOrdersTrend) }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #f6ffed">
              <el-icon :size="20" color="#52c41a"><Money /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">¥{{ formatNumber(stats.periodSales) }}</div>
              <div class="text-xs text-gray-500 mt-0.5">{{ periodLabel }}销售额</div>
              <div v-if="stats.periodSalesTrend !== 0" class="text-xs mt-0.5" :class="getTrendClass(stats.periodSalesTrend)">
                {{ formatTrend(stats.periodSalesTrend) }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #fffbe6">
              <el-icon :size="20" color="#faad14"><Goods /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">{{ stats.totalProducts || 0 }}</div>
              <div class="text-xs text-gray-500 mt-0.5">商品数量</div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #fff1f0">
              <el-icon :size="20" color="#ff4d4f"><Odometer /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">{{ stats.pendingOrders || 0 }}</div>
              <div class="text-xs text-gray-500 mt-0.5">待处理订单</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 统计卡片 - 第二行 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #fff1f0">
              <el-icon :size="20" color="#ff4d4f"><Warning /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">{{ stats.lowStockAlerts || 0 }}</div>
              <div class="text-xs text-gray-500 mt-0.5">低库存预警</div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #f0f5ff">
              <el-icon :size="20" color="#408aee"><Calendar /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">{{ stats.weekOrders || 0 }}</div>
              <div class="text-xs text-gray-500 mt-0.5">本周订单</div>
              <div v-if="stats.weekOrdersTrend !== 0" class="text-xs mt-0.5" :class="getTrendClass(stats.weekOrdersTrend)">
                {{ formatTrend(stats.weekOrdersTrend) }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #f6ffed">
              <el-icon :size="20" color="#52c41a"><TrendCharts /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">¥{{ formatNumber(stats.weekSales) }}</div>
              <div class="text-xs text-gray-500 mt-0.5">本周销售额</div>
              <div v-if="stats.weekSalesTrend !== 0" class="text-xs mt-0.5" :class="getTrendClass(stats.weekSalesTrend)">
                {{ formatTrend(stats.weekSalesTrend) }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl flex items-center justify-center" style="background: #f9f0ff">
              <el-icon :size="20" color="#722ed1"><User /></el-icon>
            </div>
            <div>
              <div class="text-xl font-bold text-gray-900">¥{{ formatNumber(stats.avgOrderValue) }}</div>
              <div class="text-xs text-gray-500 mt-0.5">平均客单价</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <!-- 销售趋势 -->
      <el-col :span="14">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-base font-bold text-gray-900">{{ periodLabel }}销售趋势</h3>
          </div>
          <div ref="trendChartRef" style="height: 260px"></div>
        </div>
      </el-col>
      <!-- 热销商品 + 订单状态 -->
      <el-col :span="10">
        <div class="bg-white rounded-xl p-5 shadow-sm mb-4">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-base font-bold text-gray-900">{{ periodLabel }}热销商品 Top 5</h3>
          </div>
          <div ref="topProductsChartRef" style="height: 180px"></div>
        </div>
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-base font-bold text-gray-900">{{ periodLabel }}订单状态</h3>
          </div>
          <div ref="orderStatusChartRef" style="height: 180px"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 库存预警列表 -->
    <el-row :gutter="16" class="mt-4" v-if="inventoryAlerts.length > 0">
      <el-col :span="24">
        <div class="bg-white rounded-xl p-5 shadow-sm">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-base font-bold text-gray-900">库存预警</h3>
            <el-tag type="danger" size="small">{{ inventoryAlerts.length }} 个SKU需要关注</el-tag>
          </div>
          <el-table :data="inventoryAlerts" size="small" max-height="200">
            <el-table-column prop="skuCode" label="SKU编码" width="150" />
            <el-table-column prop="productName" label="商品名称" />
            <el-table-column prop="warehouseName" label="仓库" width="120" />
            <el-table-column label="库存" width="100">
              <template #default="{ row }">
                <span :class="row.quantity <= 3 ? 'text-red-500 font-bold' : 'text-orange-500'">
                  {{ row.quantity }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="alertThreshold" label="预警阈值" width="100" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { ShoppingCart, Money, Goods, Odometer, Warning, Calendar, TrendCharts, User } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardStats, getOrderTrend, getTopProducts, getOrderStatus, getInventoryAlerts, type PeriodType } from '@/api/dashboard'

const trendChartRef = ref<HTMLDivElement>()
const topProductsChartRef = ref<HTMLDivElement>()
const orderStatusChartRef = ref<HTMLDivElement>()

// Chart instances for proper cleanup
let trendChart: echarts.ECharts | null = null
let topProductsChart: echarts.ECharts | null = null
let orderStatusChart: echarts.ECharts | null = null

// 日期筛选状态
const selectedPeriod = ref<PeriodType>('WEEK')
const customDateRange = ref<[string, string] | null>(null)

// 周期标签映射
const periodLabels: Record<PeriodType, string> = {
  'TODAY': '今日',
  'WEEK': '本周',
  'MONTH': '本月',
  'QUARTER': '本季',
  'YEAR': '本年',
  'CUSTOM': '自定义'
}

const periodLabel = computed(() => periodLabels[selectedPeriod.value])

const stats = reactive({
  periodOrders: 0,
  periodOrdersTrend: 0,
  periodSales: 0,
  periodSalesTrend: 0,
  totalProducts: 0,
  pendingOrders: 0,
  pendingOrdersTrend: 0,
  lowStockAlerts: 0,
  weekOrders: 0,
  weekOrdersTrend: 0,
  weekSales: 0,
  weekSalesTrend: 0,
  avgOrderValue: 0
})

const inventoryAlerts = ref<any[]>([])

function buildFilter() {
  if (selectedPeriod.value === 'CUSTOM' && customDateRange.value) {
    return {
      periodType: 'CUSTOM' as PeriodType,
      startDate: customDateRange.value[0],
      endDate: customDateRange.value[1]
    }
  }
  return { periodType: selectedPeriod.value }
}

function formatNumber(num: number): string {
  if (!num) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString()
}

function formatTrend(trend: number): string {
  const prefix = trend > 0 ? '+' : ''
  return `${prefix}${trend}%`
}

function getTrendClass(trend: number): string {
  return trend >= 0 ? 'text-green-600' : 'text-red-500'
}

function onPeriodChange() {
  loadAll()
}

function loadStats() {
  getDashboardStats(buildFilter()).then((res: any) => {
    if (res.code === 200 && res.data) {
      Object.assign(stats, res.data)
    }
  })
}

function loadTrendChart() {
  if (!trendChartRef.value) return

  // Dispose existing chart before creating new one
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }

  getOrderTrend(buildFilter()).then((res: any) => {
    if (res.code !== 200 || !res.data) return

    trendChart = echarts.init(trendChartRef.value!)
    const option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' }
      },
      legend: {
        data: ['订单数', '销售额'],
        bottom: 0
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        top: '10%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: res.data.dates,
        boundaryGap: false
      },
      yAxis: [
        {
          type: 'value',
          name: '订单数',
          position: 'left'
        },
        {
          type: 'value',
          name: '销售额',
          position: 'right',
          formatter: (value: number) => '¥' + value
        }
      ],
      series: [
        {
          name: '订单数',
          type: 'line',
          data: res.data.orderCounts,
          smooth: true,
          itemStyle: { color: '#408aee' },
          areaStyle: { color: 'rgba(64, 138, 238, 0.1)' }
        },
        {
          name: '销售额',
          type: 'line',
          yAxisIndex: 1,
          data: res.data.salesAmounts,
          smooth: true,
          itemStyle: { color: '#52c41a' }
        }
      ]
    }
    trendChart.setOption(option)
  })
}

function loadTopProductsChart() {
  if (!topProductsChartRef.value) return

  // Dispose existing chart before creating new one
  if (topProductsChart) {
    topProductsChart.dispose()
    topProductsChart = null
  }

  getTopProducts(buildFilter()).then((res: any) => {
    if (res.code !== 200 || !res.data || res.data.length === 0) {
      topProductsChartRef.value!.innerHTML = '<div class="flex items-center justify-center h-full text-gray-400 text-sm">暂无数据</div>'
      return
    }

    topProductsChart = echarts.init(topProductsChartRef.value!)
    const option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      grid: {
        left: '3%',
        right: '8%',
        bottom: '3%',
        top: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'value'
      },
      yAxis: {
        type: 'category',
        data: res.data.map((p: any) => p.productName.length > 6 ? p.productName.substring(0, 6) + '...' : p.productName).reverse(),
        axisLabel: { fontSize: 10 }
      },
      series: [
        {
          type: 'bar',
          data: res.data.map((p: any) => p.totalQuantity).reverse(),
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#83bff6' },
              { offset: 1, color: '#408aee' }
            ])
          },
          barWidth: '50%'
        }
      ]
    }
    topProductsChart.setOption(option)
  })
}

function loadOrderStatusChart() {
  if (!orderStatusChartRef.value) return

  // Dispose existing chart before creating new one
  if (orderStatusChart) {
    orderStatusChart.dispose()
    orderStatusChart = null
  }

  getOrderStatus(buildFilter()).then((res: any) => {
    if (res.code !== 200 || !res.data) {
      orderStatusChartRef.value!.innerHTML = '<div class="flex items-center justify-center h-full text-gray-400 text-sm">暂无数据</div>'
      return
    }

    orderStatusChart = echarts.init(orderStatusChartRef.value!)
    const option = {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        right: '5%',
        top: 'center',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: { fontSize: 10 }
      },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          center: ['35%', '50%'],
          avoidLabelOverlap: false,
          label: { show: false },
          data: res.data
            .filter((s: any) => s.count > 0)
            .map((s: any) => ({
              value: s.count,
              name: s.label,
              itemStyle: {
                color: ['#408aee', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2', '#eb2f96'][s.status % 7]
              }
            }))
        }
      ]
    }
    orderStatusChart.setOption(option)
  })
}

function loadInventoryAlerts() {
  getInventoryAlerts().then((res: any) => {
    if (res.code === 200 && res.data) {
      inventoryAlerts.value = res.data
    }
  })
}

function loadAll() {
  loadStats()
  loadTrendChart()
  loadTopProductsChart()
  loadOrderStatusChart()
}

onMounted(() => {
  loadAll()
  loadInventoryAlerts()
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}
</style>
