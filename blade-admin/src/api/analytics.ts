import client from './client'
import type { DateRangeFilter } from './dashboard'

export type AnalyticsDimension = 'PRODUCT' | 'SKU' | 'COLOR' | 'SIZE'
export type AnalyticsSortBy = 'SALES' | 'QUANTITY' | 'GROSS_PROFIT'

export interface AnalyticsSummary {
  orderCount: number
  salesAmount: number
  salesQuantity: number
  grossProfit?: number | null
  grossProfitRate?: number | null
  refundAmount: number
  avgOrderValue: number
  avgItemPrice: number
  profitVisible: boolean
}

export interface AnalyticsTrend {
  dates: string[]
  orderCounts: number[]
  salesAmounts: number[]
  salesQuantities: number[]
  grossProfits?: number[] | null
  profitVisible: boolean
}

export interface AnalyticsRanking {
  key: string
  label: string
  productName: string
  skuCode: string
  colorName: string
  sizeName: string
  orderCount: number
  salesQuantity: number
  salesAmount: number
  costAmount?: number | null
  grossProfit?: number | null
  grossProfitRate?: number | null
}

export interface AnalyticsProductDetail {
  productName: string
  skus: AnalyticsRanking[]
  colors: AnalyticsRanking[]
  sizes: AnalyticsRanking[]
  unspecified?: AnalyticsRanking | null
  totalSalesQuantity: number
  specifiedSalesQuantity: number
  variantCoverageRate: number
  variantDataQuality: 'HIGH' | 'MEDIUM' | 'LOW'
  profitVisible: boolean
}

export function getAnalyticsSummary(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: AnalyticsSummary }>('/analytics/summary', { params: filter }) as any
}

export function getAnalyticsTrend(filter?: DateRangeFilter) {
  return client.get<{ code: number; data: AnalyticsTrend }>('/analytics/trend', { params: filter }) as any
}

export function getAnalyticsProductRanking(filter: DateRangeFilter | undefined, dimension: AnalyticsDimension, sortBy: AnalyticsSortBy, limit = 20) {
  return client.get<{ code: number; data: AnalyticsRanking[] }>('/analytics/product-ranking', {
    params: { ...filter, dimension, sortBy, limit },
  }) as any
}

export function getAnalyticsProductDetail(filter: DateRangeFilter | undefined, productName: string) {
  return client.get<{ code: number; data: AnalyticsProductDetail }>('/analytics/product-detail', {
    params: { ...filter, productName },
  }) as any
}
