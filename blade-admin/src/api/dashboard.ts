import client from './client'

export type PeriodType = 'TODAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM'

export interface DateRangeFilter {
  periodType: PeriodType
  startDate?: string
  endDate?: string
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
  return client.get<{ code: number; data: DashboardStats }>('/dashboard/stats', { params: filter }) as any
}

export function getOrderTrend(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: OrderTrend }>('/dashboard/trend', { params: filter }) as any
}

export function getTopProducts(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: TopProduct[] }>('/dashboard/top-products', { params: filter }) as any
}

export function getOrderStatus(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: OrderStatus[] }>('/dashboard/order-status', { params: filter }) as any
}

export function getInventoryAlerts() {
  return client.get<{ code: number; data: InventoryAlert[] }>('/dashboard/inventory-alerts') as any
}

export function getInventoryStats() {
  return client.get<{ code: number; data: InventoryStats }>('/dashboard/inventory-stats') as any
}
