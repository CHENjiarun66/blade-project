<template>
  <div class="analytics-page">
    <div class="flex justify-between items-end mb-5">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">数据分析</h2>
        <p class="text-gray-500 text-sm">按销售、商品、SKU、颜色和尺码拆解经营表现。</p>
      </div>
      <div class="flex items-center gap-3">
        <el-radio-group v-model="selectedPeriod" @change="onFilterChange">
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
          @change="onFilterChange"
        />
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="grid gap-4 mb-4" :class="summary.profitVisible ? 'grid-cols-7' : 'grid-cols-4'">
      <div class="metric-panel">
        <div class="metric-icon bg-blue-50 text-blue-600"><el-icon><ShoppingCart /></el-icon></div>
        <div>
          <div class="metric-value">{{ summary.orderCount || 0 }}</div>
          <div class="metric-label">{{ periodLabel }}订单</div>
        </div>
      </div>
      <div class="metric-panel">
        <div class="metric-icon bg-emerald-50 text-emerald-600"><el-icon><Money /></el-icon></div>
        <div>
          <div class="metric-value">¥{{ formatNumber(summary.salesAmount) }}</div>
          <div class="metric-label">{{ periodLabel }}销售额</div>
        </div>
      </div>
      <div class="metric-panel">
        <div class="metric-icon bg-amber-50 text-amber-600"><el-icon><Goods /></el-icon></div>
        <div>
          <div class="metric-value">{{ formatNumber(summary.salesQuantity) }}</div>
          <div class="metric-label">{{ periodLabel }}销量</div>
        </div>
      </div>
      <div class="metric-panel">
        <div class="metric-icon bg-slate-100 text-slate-600"><el-icon><TrendCharts /></el-icon></div>
        <div>
          <div class="metric-value">¥{{ formatNumber(summary.avgItemPrice) }}</div>
          <div class="metric-label">件单价</div>
        </div>
      </div>
      <template v-if="summary.profitVisible">
        <div class="metric-panel">
          <div class="metric-icon bg-indigo-50 text-indigo-600"><el-icon><Histogram /></el-icon></div>
          <div>
            <div class="metric-value">¥{{ formatNumber(summary.grossProfit) }}</div>
            <div class="metric-label">{{ periodLabel }}毛利</div>
          </div>
        </div>
        <div class="metric-panel">
          <div class="metric-icon bg-purple-50 text-purple-600"><el-icon><DataLine /></el-icon></div>
          <div>
            <div class="metric-value">{{ formatPercent(summary.grossProfitRate) }}</div>
            <div class="metric-label">毛利率</div>
          </div>
        </div>
        <div class="metric-panel">
          <div class="metric-icon bg-rose-50 text-rose-600"><el-icon><RefreshLeft /></el-icon></div>
          <div>
            <div class="metric-value">¥{{ formatNumber(summary.refundAmount) }}</div>
            <div class="metric-label">退款金额</div>
          </div>
        </div>
      </template>
    </div>

    <el-row :gutter="16" class="mb-4">
      <el-col :span="14">
        <section class="analysis-panel">
          <div class="panel-header">
            <div>
              <h3>{{ periodLabel }}经营趋势</h3>
              <p>销售额、销量和毛利随时间变化</p>
            </div>
            <el-checkbox-group v-model="trendMetrics" size="small" @change="renderTrendChart">
              <el-checkbox-button label="sales">销售额</el-checkbox-button>
              <el-checkbox-button label="quantity">销量</el-checkbox-button>
              <el-checkbox-button v-if="summary.profitVisible" label="profit">毛利</el-checkbox-button>
            </el-checkbox-group>
          </div>
          <div ref="trendChartRef" class="chart-large"></div>
        </section>
      </el-col>
      <el-col :span="10">
        <section class="analysis-panel h-full">
          <div class="panel-header">
            <div>
              <h3>经营效率</h3>
              <p>周期内客单和商品效率</p>
            </div>
          </div>
          <div class="efficiency-grid">
            <div>
              <span>客单价</span>
              <strong>¥{{ formatNumber(summary.avgOrderValue) }}</strong>
            </div>
            <div>
              <span>件单价</span>
              <strong>¥{{ formatNumber(summary.avgItemPrice) }}</strong>
            </div>
            <div>
              <span>单均件数</span>
              <strong>{{ avgQuantityPerOrder }}</strong>
            </div>
            <div v-if="summary.profitVisible">
              <span>每件毛利</span>
              <strong>¥{{ formatNumber(avgProfitPerItem) }}</strong>
            </div>
          </div>
        </section>
      </el-col>
    </el-row>

    <section class="analysis-panel">
      <div class="panel-header">
        <div>
          <h3>商品分析 Top 20</h3>
          <p>按商品、SKU、颜色和尺码查看销售贡献</p>
        </div>
        <div class="flex items-center gap-3">
          <el-select v-model="sortBy" size="small" style="width: 140px" @change="loadRanking">
            <el-option label="按销售额" value="SALES" />
            <el-option label="按销量" value="QUANTITY" />
            <el-option v-if="summary.profitVisible" label="按毛利" value="GROSS_PROFIT" />
          </el-select>
        </div>
      </div>

      <el-tabs v-model="dimension" @tab-change="loadRanking">
        <el-tab-pane label="商品" name="PRODUCT" />
        <el-tab-pane label="SKU" name="SKU" />
        <el-tab-pane label="颜色" name="COLOR" />
        <el-tab-pane label="尺码" name="SIZE" />
      </el-tabs>

      <el-row :gutter="16">
        <el-col :span="10">
          <div ref="rankingChartRef" class="chart-ranking"></div>
        </el-col>
        <el-col :span="14">
          <el-table
            v-loading="rankingLoading"
            :data="rankingRows"
            size="small"
            height="360"
            empty-text="暂无分析数据"
            @row-click="openProductDetail"
          >
            <el-table-column prop="label" :label="dimensionLabel" min-width="180" show-overflow-tooltip />
            <el-table-column prop="salesQuantity" label="销量" width="90" align="right" />
            <el-table-column label="销售额" width="120" align="right">
              <template #default="{ row }">¥{{ formatNumber(row.salesAmount) }}</template>
            </el-table-column>
            <el-table-column prop="orderCount" label="订单数" width="90" align="right" />
            <el-table-column v-if="summary.profitVisible" label="毛利" width="120" align="right">
              <template #default="{ row }">¥{{ formatNumber(row.grossProfit) }}</template>
            </el-table-column>
            <el-table-column v-if="summary.profitVisible" label="毛利率" width="100" align="right">
              <template #default="{ row }">{{ formatPercent(row.grossProfitRate) }}</template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </section>

    <el-drawer v-model="detailVisible" size="720px" :title="detailTitle">
      <div v-if="productDetail" class="variant-quality">
        <div>
          <span>款号总销量</span>
          <strong>{{ productDetail.totalSalesQuantity || 0 }} 件</strong>
        </div>
        <div>
          <span>已明确规格</span>
          <strong>{{ productDetail.specifiedSalesQuantity || 0 }} 件</strong>
        </div>
        <div>
          <span>规格覆盖率</span>
          <strong>{{ (Number(productDetail.variantCoverageRate || 0) * 100).toFixed(1) }}%</strong>
        </div>
        <div v-if="productDetail.unspecified" class="variant-unspecified">
          <span>整款录入（规格未指定）</span>
          <strong>{{ productDetail.unspecified.salesQuantity }} 件</strong>
        </div>
        <div v-if="productDetail.historicalNoVariant" class="variant-historical">
          <span>历史无规格</span>
          <strong>{{ productDetail.historicalNoVariant.salesQuantity }} 件</strong>
        </div>
      </div>
      <el-tabs v-model="detailTab">
        <el-tab-pane label="SKU" name="skus">
          <detail-table :rows="productDetail?.skus || []" :profit-visible="summary.profitVisible" />
        </el-tab-pane>
        <el-tab-pane label="颜色" name="colors">
          <detail-table :rows="productDetail?.colors || []" :profit-visible="summary.profitVisible" />
        </el-tab-pane>
        <el-tab-pane label="尺码" name="sizes">
          <detail-table :rows="productDetail?.sizes || []" :profit-visible="summary.profitVisible" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { DataLine, Goods, Histogram, Money, Refresh, RefreshLeft, ShoppingCart, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { PeriodType } from '@/api/dashboard'
import {
  getAnalyticsProductDetail,
  getAnalyticsProductRanking,
  getAnalyticsSummary,
  getAnalyticsTrend,
  type AnalyticsDimension,
  type AnalyticsProductDetail,
  type AnalyticsRanking,
  type AnalyticsSortBy,
  type AnalyticsSummary,
  type AnalyticsTrend,
} from '@/api/analytics'

const selectedPeriod = ref<PeriodType>('WEEK')
const customDateRange = ref<[string, string] | null>(null)
const trendMetrics = ref<string[]>(['sales', 'quantity'])
const dimension = ref<AnalyticsDimension>('PRODUCT')
const sortBy = ref<AnalyticsSortBy>('SALES')
const rankingRows = ref<AnalyticsRanking[]>([])
const rankingLoading = ref(false)
const productDetail = ref<AnalyticsProductDetail | null>(null)
const detailVisible = ref(false)
const detailTab = ref('skus')

const trendChartRef = ref<HTMLDivElement>()
const rankingChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let rankingChart: echarts.ECharts | null = null
let trendData: AnalyticsTrend | null = null

const periodLabels: Record<PeriodType, string> = {
  TODAY: '今日',
  WEEK: '本周',
  MONTH: '本月',
  QUARTER: '本季',
  YEAR: '本年',
  CUSTOM: '自定义',
}

const summary = reactive<AnalyticsSummary>({
  orderCount: 0,
  salesAmount: 0,
  salesQuantity: 0,
  grossProfit: null,
  grossProfitRate: null,
  refundAmount: 0,
  avgOrderValue: 0,
  avgItemPrice: 0,
  profitVisible: false,
})

const periodLabel = computed(() => periodLabels[selectedPeriod.value])
const dimensionLabel = computed(() => ({ PRODUCT: '商品', SKU: 'SKU', COLOR: '颜色', SIZE: '尺码' }[dimension.value]))
const detailTitle = computed(() => productDetail.value ? `${productDetail.value.productName} 分析详情` : '商品分析详情')
const avgQuantityPerOrder = computed(() => summary.orderCount > 0 ? (summary.salesQuantity / summary.orderCount).toFixed(2) : '0')
const avgProfitPerItem = computed(() => summary.profitVisible && summary.salesQuantity > 0
  ? Number(summary.grossProfit || 0) / summary.salesQuantity
  : 0)

function buildFilter() {
  if (selectedPeriod.value === 'CUSTOM' && customDateRange.value) {
    return {
      periodType: 'CUSTOM' as PeriodType,
      startDate: customDateRange.value[0],
      endDate: customDateRange.value[1],
    }
  }
  return { periodType: selectedPeriod.value }
}

function formatNumber(value?: number | null) {
  const num = Number(value || 0)
  if (Math.abs(num) >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString(undefined, { maximumFractionDigits: 2 })
}

function formatPercent(value?: number | null) {
  return `${Number(value || 0).toFixed(2)}%`
}

function onFilterChange() {
  loadAll()
}

async function loadSummary() {
  const res = await getAnalyticsSummary(buildFilter())
  if (res.code === 200 && res.data) {
    Object.assign(summary, res.data)
    if (!summary.profitVisible) {
      trendMetrics.value = trendMetrics.value.filter(item => item !== 'profit')
      if (sortBy.value === 'GROSS_PROFIT') sortBy.value = 'SALES'
    }
  }
}

async function loadTrend() {
  const res = await getAnalyticsTrend(buildFilter())
  if (res.code === 200 && res.data) {
    trendData = res.data
    renderTrendChart()
  }
}

async function loadRanking() {
  rankingLoading.value = true
  try {
    const res = await getAnalyticsProductRanking(buildFilter(), dimension.value, sortBy.value, 20)
    rankingRows.value = res.code === 200 && res.data ? res.data : []
    renderRankingChart()
  } finally {
    rankingLoading.value = false
  }
}

function renderTrendChart() {
  if (!trendChartRef.value || !trendData) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendChartRef.value)

  const series: any[] = []
  if (trendMetrics.value.includes('sales')) {
    series.push({ name: '销售额', type: 'line', smooth: true, data: trendData.salesAmounts, yAxisIndex: 0, itemStyle: { color: '#2563eb' } })
  }
  if (trendMetrics.value.includes('quantity')) {
    series.push({ name: '销量', type: 'bar', data: trendData.salesQuantities, yAxisIndex: 1, itemStyle: { color: '#f59e0b' }, barMaxWidth: 18 })
  }
  if (summary.profitVisible && trendMetrics.value.includes('profit')) {
    series.push({ name: '毛利', type: 'line', smooth: true, data: trendData.grossProfits || [], yAxisIndex: 0, itemStyle: { color: '#7c3aed' } })
  }

  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 48, right: 48, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: trendData.dates, boundaryGap: true },
    yAxis: [
      { type: 'value', name: '金额', axisLabel: { formatter: (value: number) => `¥${formatNumber(value)}` } },
      { type: 'value', name: '销量' },
    ],
    series,
  })
}

function renderRankingChart() {
  if (!rankingChartRef.value) return
  if (rankingChart) rankingChart.dispose()

  if (rankingRows.value.length === 0) {
    rankingChartRef.value.innerHTML = '<div class="empty-chart">暂无分析数据</div>'
    return
  }

  rankingChart = echarts.init(rankingChartRef.value)
  const rows = rankingRows.value.slice(0, 12).reverse()
  const valueKey = sortBy.value === 'QUANTITY' ? 'salesQuantity' : sortBy.value === 'GROSS_PROFIT' ? 'grossProfit' : 'salesAmount'

  rankingChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 96, right: 24, top: 10, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: rows.map(row => row.label.length > 10 ? row.label.slice(0, 10) + '...' : row.label),
      axisLabel: { fontSize: 11 },
    },
    series: [{
      type: 'bar',
      data: rows.map(row => Number((row as any)[valueKey] || 0)),
      barWidth: 14,
      itemStyle: { color: '#2563eb', borderRadius: [0, 4, 4, 0] },
    }],
  })
}

async function openProductDetail(row: AnalyticsRanking) {
  if (dimension.value !== 'PRODUCT') return
  const res = await getAnalyticsProductDetail(buildFilter(), row.productName)
  if (res.code === 200 && res.data) {
    productDetail.value = res.data
    detailVisible.value = true
    detailTab.value = 'skus'
  }
}

async function loadAll() {
  await loadSummary()
  await Promise.all([loadTrend(), loadRanking()])
}

const DetailTable = defineComponent({
  props: {
    rows: { type: Array as () => AnalyticsRanking[], required: true },
    profitVisible: { type: Boolean, required: true },
  },
  setup(props) {
    return () => h('div', [
      h('table', { class: 'detail-table' }, [
        h('thead', [
          h('tr', [
            h('th', '名称'),
            h('th', '销量'),
            h('th', '销售额'),
            ...(props.profitVisible ? [h('th', '毛利'), h('th', '毛利率')] : []),
          ]),
        ]),
        h('tbody', props.rows.map(row => h('tr', [
          h('td', row.label),
          h('td', row.salesQuantity),
          h('td', `¥${formatNumber(row.salesAmount)}`),
          ...(props.profitVisible ? [h('td', `¥${formatNumber(row.grossProfit)}`), h('td', formatPercent(row.grossProfitRate))] : []),
        ]))),
      ]),
    ])
  },
})

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.analytics-page {
  padding: 0;
}

.metric-panel,
.analysis-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.metric-panel {
  min-height: 96px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.metric-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-value {
  color: #111827;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}

.metric-label {
  color: #6b7280;
  font-size: 12px;
  margin-top: 4px;
}

.analysis-panel {
  padding: 18px;
}

.variant-quality {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.variant-quality > div {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
}

.variant-quality span,
.variant-quality strong {
  display: block;
}

.variant-quality span {
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 6px;
}

.variant-quality strong {
  color: #111827;
  font-size: 16px;
}

.variant-quality .variant-unspecified {
  border-color: #fed7aa;
  background: #fff7ed;
}

.variant-quality .variant-historical {
  border-color: #dbe4f0;
  background: #f8fafc;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-header h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.panel-header p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.chart-large {
  height: 320px;
}

.chart-ranking {
  height: 360px;
}

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 13px;
}

.efficiency-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.efficiency-grid div {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  min-height: 78px;
}

.efficiency-grid span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 8px;
}

.efficiency-grid strong {
  color: #111827;
  font-size: 22px;
}

:deep(.el-table__row) {
  cursor: pointer;
}

.detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.detail-table th,
.detail-table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 10px 8px;
  text-align: right;
}

.detail-table th:first-child,
.detail-table td:first-child {
  text-align: left;
}

.detail-table th {
  color: #6b7280;
  font-weight: 600;
  background: #f9fafb;
}

@media (max-width: 1280px) {
  .grid-cols-7 {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
