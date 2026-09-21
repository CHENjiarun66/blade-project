import client from './client'

export type PeriodType = 'TODAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM'

export interface DateRangeFilter {
  periodType: PeriodType
  startDate?: string
  endDate?: string
  /** 档口筛选：仅允许当前用户可访问档口；空数组/不传=当前完整可见范围 */
  sourceOutletIds?: number[]
  /** 仅看待归档档口（需 data:outlet:unassigned）；与 sourceOutletIds 互斥 */
  pendingArchive?: boolean
}

/**
 * 统一序列化统计筛选：sourceOutletIds 用逗号拼接，Spring 可直接绑定 List<Long>，
 * 避免 axios 数组括号写法导致后端绑定失败。后端仍会逐项做授权校验。
 */
export function buildFilterParams(filter?: DateRangeFilter): Record<string, unknown> | undefined {
  if (!filter) return undefined
  const params: Record<string, unknown> = { periodType: filter.periodType }
  if (filter.startDate) params.startDate = filter.startDate
  if (filter.endDate) params.endDate = filter.endDate
  if (filter.sourceOutletIds && filter.sourceOutletIds.length) {
    params.sourceOutletIds = filter.sourceOutletIds.join(',')
  }
  if (filter.pendingArchive) params.pendingArchive = true
  return params
}

export interface DashboardStats {
  periodOrders: number
  periodOrdersTrend: number
  periodSales: number
  periodSalesTrend: number
  periodGrossProfit: number
  periodGrossProfitTrend: number
  periodSalesQuantity: number
  periodSalesQuantityTrend: number
  totalProducts: number
  pendingOrders: number
  pendingOrdersTrend: number
  /** 待归档档口订单数：仅 data:outlet:unassigned + pendingArchive=true 时返回 */
  pendingArchiveCount?: number | null
  // 新增字段
  lowStockAlerts: number
  weekOrders: number
  weekOrdersTrend: number
  weekSales: number
  weekSalesTrend: number
  weekGrossProfit: number
  weekGrossProfitTrend: number
  avgOrderValue: number
}

export interface OrderTrend {
  dates: string[]
  orderCounts: number[]
  salesAmounts: number[]
}

export interface TopProduct {
  productId: number | null
  productName: string
  totalQuantity: number
  totalAmount: number
}

export interface OrderStatus {
  status: number
  label: string
  count: number
}

export interface InventoryAlert {
  skuId: number
  skuCode: string
  productName: string
  warehouseName: string
  quantity: number
  alertThreshold: number
}

export interface InventoryStats {
  turnoverRate: number
  totalQuantity: number
  totalSkuCount: number
  lowStockCount: number
  overstockCount: number
}

export function getDashboardStats(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: DashboardStats }>('/dashboard/stats', { params: buildFilterParams(filter) }) as any
}

export function getOrderTrend(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: OrderTrend }>('/dashboard/trend', { params: buildFilterParams(filter) }) as any
}

export function getTopProducts(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: TopProduct[] }>('/dashboard/top-products', { params: buildFilterParams(filter) }) as any
}

export function getOrderStatus(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: OrderStatus[] }>('/dashboard/order-status', { params: buildFilterParams(filter) }) as any
}

export function getInventoryAlerts() {
  return client.get<{ code: number; data: InventoryAlert[] }>('/dashboard/inventory-alerts') as any
}

export function getInventoryStats() {
  return client.get<{ code: number; data: InventoryStats }>('/dashboard/inventory-stats') as any
}
