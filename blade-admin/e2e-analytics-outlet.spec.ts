import { expect, test, Page } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

const OUTLETS = [
  { id: 1, outletCode: 'YL', outletName: '御龙', status: 1 },
  { id: 2, outletCode: 'HZ', outletName: '杭州档', status: 1 },
]

const PERMISSIONS = ['menu:analytics', 'menu:dashboard', 'data:outlet:all', 'data:order:peopleAll', 'data:outlet:unassigned']

const ADMIN_INFO = { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }

const SUMMARY = ok({
  orderCount: 3, salesAmount: 300, salesQuantity: 3, grossProfit: 120, grossProfitRate: 40,
  refundAmount: 0, avgOrderValue: 100, avgItemPrice: 100, profitVisible: true,
})

const STATS = ok({
  periodOrders: 3, periodOrdersTrend: 0, periodSales: 300, periodSalesTrend: 0,
  periodGrossProfit: 120, periodGrossProfitTrend: 0, periodSalesQuantity: 3, periodSalesQuantityTrend: 0,
  totalProducts: 10, pendingOrders: 2, pendingOrdersTrend: 0, pendingArchiveCount: 4,
  lowStockAlerts: 1, weekOrders: 3, weekOrdersTrend: 0, weekSales: 300, weekSalesTrend: 0,
  weekGrossProfit: 120, weekGrossProfitTrend: 0, avgOrderValue: 100,
})

function init(page: Page) {
  return page.addInitScript((permissions: string[]) => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }))
    localStorage.setItem('permissions', JSON.stringify(permissions))
  }, PERMISSIONS)
}

test.describe('Series E1 统计档口筛选', () => {
  test('BA-OUTLET-005 分析页档口多选序列化为 sourceOutletIds，清空后不发送', async ({ page }) => {
    await init(page)
    let lastAnalyticsParams: Record<string, string> = {}
    let lastDashboardParams: Record<string, string> = {}

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') {
        return route.fulfill({ json: ok({ scopeType: 'ALL', peopleScope: 'ALL_USERS', locked: false, defaultOutletId: 1, items: OUTLETS }) })
      }
      if (url.pathname === '/api/analytics/summary' || url.pathname === '/api/analytics/trend' || url.pathname === '/api/analytics/product-ranking') {
        lastAnalyticsParams = Object.fromEntries(url.searchParams.entries())
        if (url.pathname === '/api/analytics/summary') return route.fulfill({ json: SUMMARY })
        if (url.pathname === '/api/analytics/trend') {
          return route.fulfill({ json: ok({ dates: ['09-21'], orderCounts: [3], salesAmounts: [300], salesQuantities: [3], grossProfits: [120], profitVisible: true }) })
        }
        return route.fulfill({ json: ok([]) })
      }
      if (url.pathname.startsWith('/api/dashboard/')) {
        // 只记录 stats 的筛选参数；inventory-* 无筛选，避免竞态覆盖
        if (url.pathname === '/api/dashboard/stats') {
          lastDashboardParams = Object.fromEntries(url.searchParams.entries())
          return route.fulfill({ json: STATS })
        }
        if (url.pathname === '/api/dashboard/trend') return route.fulfill({ json: ok({ dates: [], orderCounts: [], salesAmounts: [] }) })
        if (url.pathname === '/api/dashboard/top-products') return route.fulfill({ json: ok([]) })
        if (url.pathname === '/api/dashboard/order-status') return route.fulfill({ json: ok([]) })
        if (url.pathname === '/api/dashboard/inventory-alerts') return route.fulfill({ json: ok([]) })
        if (url.pathname === '/api/dashboard/inventory-stats') return route.fulfill({ json: ok({ turnoverRate: 0, totalQuantity: 0, totalSkuCount: 0, lowStockCount: 0, overstockCount: 0 }) })
      }
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/analytics')
    await page.locator('[data-testid="analytics-outlet-filter"]').click()
    await page.getByRole('option', { name: /御龙（YL）/ }).click()
    await expect.poll(() => lastAnalyticsParams.sourceOutletIds).toBe('1')

    // 多选第二个：逗号拼接
    await page.locator('[data-testid="analytics-outlet-filter"]').click()
    await page.getByRole('option', { name: /杭州档（HZ）/ }).click()
    await expect.poll(() => lastAnalyticsParams.sourceOutletIds).toBe('1,2')

    // 清空：不再发送 sourceOutletIds（表示当前完整可见范围）
    await page.locator('[data-testid="analytics-outlet-filter"]').hover()
    await page.locator('[data-testid="analytics-outlet-filter"] .el-select__clear').click({ force: true })
    await expect.poll(() => 'sourceOutletIds' in lastAnalyticsParams).toBe(false)
  })

  test('BA-OUTLET-005 仪表盘档口与待归档互斥，且待归档计数单独展示', async ({ page }) => {
    await init(page)
    let lastDashboardParams: Record<string, string> = {}

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') {
        return route.fulfill({ json: ok({ scopeType: 'ALL', peopleScope: 'ALL_USERS', locked: false, defaultOutletId: 1, items: OUTLETS }) })
      }
      if (url.pathname.startsWith('/api/dashboard/')) {
        // 只记录 stats 的筛选参数；inventory-* 无筛选，避免竞态覆盖
        if (url.pathname === '/api/dashboard/stats') {
          lastDashboardParams = Object.fromEntries(url.searchParams.entries())
          return route.fulfill({ json: STATS })
        }
        if (url.pathname === '/api/dashboard/trend') return route.fulfill({ json: ok({ dates: [], orderCounts: [], salesAmounts: [] }) })
        if (url.pathname === '/api/dashboard/top-products') return route.fulfill({ json: ok([]) })
        if (url.pathname === '/api/dashboard/order-status') return route.fulfill({ json: ok([]) })
        if (url.pathname === '/api/dashboard/inventory-alerts') return route.fulfill({ json: ok([]) })
        if (url.pathname === '/api/dashboard/inventory-stats') return route.fulfill({ json: ok({ turnoverRate: 0, totalQuantity: 0, totalSkuCount: 0, lowStockCount: 0, overstockCount: 0 }) })
      }
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/dashboard')
    // 先选档口
    await page.locator('[data-testid="dashboard-outlet-filter"]').click()
    await page.getByRole('option', { name: /御龙（YL）/ }).click()
    await expect.poll(() => lastDashboardParams.sourceOutletIds).toBe('1')

    // 勾选待归档：清空档口选择，只发送 pendingArchive
    await page.locator('[data-testid="dashboard-pending-archive"]').click()
    await expect.poll(() => lastDashboardParams.pendingArchive).toBe('true')
    await expect.poll(() => 'sourceOutletIds' in lastDashboardParams).toBe(false)
    await expect(page.locator('[data-testid="dashboard-pending-archive-count"]')).toBeVisible()
  })
})
