import { expect, test, Page } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

const OUTLETS = [
  { id: 1, outletCode: 'YL', outletName: '御龙', status: 1 },
  { id: 2, outletCode: 'HZ', outletName: '杭州档', status: 1 },
]

const outletOptions = (locked: boolean, items = OUTLETS) =>
  ok({ scopeType: 'ALL', peopleScope: 'ALL_USERS', locked, defaultOutletId: 1, items })

const ADMIN_PERMISSIONS = [
  'menu:order',
  'btn:order:view',
  'btn:order:create',
  'btn:order:edit',
  'data:outlet:all',
  'data:outlet:unassigned',
  'data:order:peopleAll',
  'btn:order:changeOutlet',
]

const ADMIN_INFO = { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }

function initAdmin(page: Page) {
  return page.addInitScript((permissions: string[]) => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }))
    localStorage.setItem('permissions', JSON.stringify(permissions))
  }, ADMIN_PERMISSIONS)
}


test.describe('Series D 档口选择器与筛选', () => {
  test('BA-OUTLET-003 快速录单单档口显示只读档口，不出现可切换下拉', async ({ page }) => {
    await initAdmin(page)
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: ADMIN_PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') return route.fulfill({ json: outletOptions(true, [OUTLETS[0]]) })
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/orders/quick')
    await expect(page.locator('[data-testid="quick-outlet"] [data-testid="outlet-readonly"]')).toBeVisible()
    await expect(page.locator('[data-testid="quick-outlet"] [data-testid="outlet-select"]')).toHaveCount(0)
  })

  test('BA-OUTLET-003 快速录单多档口可选择并提交 sourceOutletId', async ({ page }) => {
    await initAdmin(page)
    let createPayload: Record<string, any> | undefined

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const method = route.request().method()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: ADMIN_PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') return route.fulfill({ json: outletOptions(false) })
      if (url.pathname === '/api/order-drafts/batches') return route.fulfill({ json: ok([]) })
      if (url.pathname === '/api/products') {
        return route.fulfill({ json: ok({ records: [{
          id: 61, productCode: '616-24#', name: '多档口测试商品', status: 1, wholesalePrice: 34, costPrice: 29,
          skus: [{ id: 61624, skuCode: '616-24#-UNSPECIFIED-UNSPEC', skuType: 'PLACEHOLDER', placeholder: true,
            colorId: 9998, colorName: '未指定颜色', sizeId: 9998, sizeName: 'UNSPEC', price: 34, costPrice: 29, status: 1 }],
        }], total: 1, size: 1000, current: 1, pages: 1 }) })
      }
      if (url.pathname === '/api/customers') return route.fulfill({ json: ok({ records: [], total: 0, size: 10, current: 1, pages: 0 }) })
      if (url.pathname === '/api/order-drafts' && method === 'POST') {
        createPayload = route.request().postDataJSON()
        return route.fulfill({ json: ok({ externalRefNo: createPayload?.externalRefNo, status: 'CREATED_WITH_WARNINGS', draftId: 88, warnings: [] }) })
      }
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/orders/quick')
    await page.getByPlaceholder('如 41').fill('41')
    await page.getByPlaceholder('如 0135').fill('0135')

    const productSearch = page.locator('.batch-product-entry-row').getByRole('combobox')
    await productSearch.fill('616-24#')
    await page.getByRole('option', { name: /616-24#.*多档口测试商品/ }).click()

    // 选择第二个授权档口
    await page.locator('[data-testid="quick-outlet"] [data-testid="outlet-select"]').click()
    await page.getByRole('option', { name: /杭州档/ }).click()

    await page.getByPlaceholder('输入客户名称筛选').fill('多档口客户')
    await page.getByRole('button', { name: '添加到草稿' }).click()

    await expect(page.getByText('已添加到草稿，可从左侧“草稿订单列表”继续填写')).toBeVisible()
    expect(createPayload?.sourceOutletId).toBe(2)
  })

  test('BA-OUTLET-003 档口 options 不持久缓存：跨页面重挂载重新拉取真实结果', async ({ page }) => {
    await initAdmin(page)
    let optionsCalls = 0
    const firstItems = [
      { id: 1, outletCode: 'YL', outletName: '御龙', status: 1 },
      { id: 2, outletCode: 'HZ', outletName: '杭州档', status: 1 },
    ]
    const secondItems = [
      { id: 2, outletCode: 'HZ', outletName: '杭州档', status: 1 },
      { id: 3, outletCode: 'SZ', outletName: '苏州档', status: 1 },
    ]

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: ADMIN_PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') {
        optionsCalls += 1
        const items = optionsCalls === 1 ? firstItems : secondItems
        return route.fulfill({ json: ok({ scopeType: 'ALL', peopleScope: 'ALL_USERS', locked: false, defaultOutletId: items[0].id, items }) })
      }
      if (url.pathname === '/api/products') return route.fulfill({ json: ok({ records: [], total: 0, size: 1000, current: 1, pages: 0 }) })
      if (url.pathname === '/api/customers') return route.fulfill({ json: ok({ records: [], total: 0, size: 10, current: 1, pages: 0 }) })
      if (url.pathname === '/api/order-drafts/batches') return route.fulfill({ json: ok([]) })
      if (url.pathname === '/api/order-drafts') return route.fulfill({ json: ok({ records: [], total: 0, size: 20, current: 1, pages: 0 }) })
      if (url.pathname === '/api/orders') return route.fulfill({ json: ok({ records: [], total: 0, size: 20, current: 1, pages: 0 }) })
      if (url.pathname === '/api/warehouses' || url.pathname === '/api/inventory/by-warehouse') return route.fulfill({ json: ok([]) })
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/orders/quick')
    await expect(page.locator('[data-testid="quick-outlet"]')).toContainText('御龙')

    // SPA 内切换页面（不整页刷新），后一次挂载必须重新取服务器 options，不能复用旧结果
    await page.getByRole('button', { name: '返回订单' }).click()
    await expect(page).toHaveURL(/\/orders$/)
    await page.getByRole('button', { name: '新建订单' }).click()
    await expect(page).toHaveURL(/\/orders\/new$/)
    await expect(page.locator('[data-testid="new-order-outlet"]')).toContainText('杭州档')
    expect(optionsCalls).toBeGreaterThanOrEqual(2)
  })

  test('BA-OUTLET-004 草稿列表档口筛选与待归档标签', async ({ page }) => {
    await initAdmin(page)
    const draftRecords = [
      { id: 501, externalRefNo: 'd-1', entrySource: 'MANUAL', sourceBatchNo: '41', sourceOrderNo: '1', customerName: '正常档客户', sourceOutletId: 1, sourceOutletCode: 'YL', sourceShop: '御龙', status: 'EDITING', itemCount: 1, unresolvedCount: 0, warningCount: 0, updateTime: '2026-09-21 10:00:00' },
      { id: 502, externalRefNo: 'd-2', entrySource: 'MANUAL', sourceBatchNo: '41', sourceOrderNo: '2', customerName: '待归档客户', sourceOutletId: null, sourceOutletCode: null, sourceShop: null, status: 'EDITING', itemCount: 1, unresolvedCount: 0, warningCount: 0, updateTime: '2026-09-21 10:00:00' },
    ]
    let lastParams: Record<string, string> = {}

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: ADMIN_PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') return route.fulfill({ json: outletOptions(false) })
      if (url.pathname === '/api/order-drafts/batches') return route.fulfill({ json: ok([]) })
      if (url.pathname === '/api/order-drafts') {
        lastParams = Object.fromEntries(url.searchParams.entries())
        return route.fulfill({ json: ok({ records: draftRecords, total: 2, size: 20, current: 1, pages: 1 }) })
      }
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/orders/drafts')
    await expect(page.locator('[data-testid="draft-pending-outlet-tag"]')).toBeVisible()
    // 真实 Summary 契约必须返回 sourceShop 名称快照，正常行显示“御龙”而不是仅编码
    await expect(page.locator('tr', { hasText: '正常档客户' })).toContainText('御龙')

    await page.locator('[data-testid="draft-outlet-filter"]').click()
    await page.getByRole('option', { name: '待归档档口' }).click()
    await page.getByRole('button', { name: '查询' }).click()
    await expect.poll(() => lastParams.unassignedOnly).toBe('true')

    await page.locator('[data-testid="draft-outlet-filter"]').click()
    await page.getByRole('option', { name: /御龙（YL）/ }).click()
    await page.getByRole('button', { name: '查询' }).click()
    await expect.poll(() => lastParams.sourceOutletId).toBe('1')
  })

  test('BA-OUTLET-004 正式订单列表档口筛选与待归档标签', async ({ page }) => {
    await initAdmin(page)
    const orders = [
      { id: 601, orderNo: 'SO-601', orderDate: '2026-09-21', customerName: '正常客户', sourceOutletId: 1, sourceOutletCode: 'YL', sourceShop: '御龙', status: 0, statusName: '待处理', paymentStatus: 0, paymentStatusName: '未付款', totalAmount: 100, paidAmount: 0, needDelivery: 0, isDelivered: 0, adjustmentStatus: 'NONE', createTime: '2026-09-21 10:00:00' },
      { id: 602, orderNo: 'SO-602', orderDate: '2026-09-21', customerName: '待归档客户', sourceOutletId: null, sourceOutletCode: null, sourceShop: null, status: 0, statusName: '待处理', paymentStatus: 0, paymentStatusName: '未付款', totalAmount: 50, paidAmount: 0, needDelivery: 0, isDelivered: 0, adjustmentStatus: 'NONE', createTime: '2026-09-21 10:00:00' },
    ]
    let lastParams: Record<string, string> = {}

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: ADMIN_PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
      if (url.pathname === '/api/outlets/options') return route.fulfill({ json: outletOptions(false) })
      if (url.pathname === '/api/orders') {
        lastParams = Object.fromEntries(url.searchParams.entries())
        return route.fulfill({ json: ok({ records: orders, total: 2, size: 20, current: 1, pages: 1 }) })
      }
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/orders')
    await expect(page.locator('[data-testid="order-pending-outlet-tag"]')).toBeVisible()

    await page.locator('[data-testid="order-outlet-filter"]').click()
    await page.getByRole('option', { name: '待归档档口' }).click()
    await page.getByRole('button', { name: '确认筛选' }).click()
    await expect.poll(() => lastParams.unassignedOnly).toBe('true')

    await page.locator('[data-testid="order-outlet-filter"]').click()
    await page.getByRole('option', { name: /御龙（YL）/ }).click()
    await page.getByRole('button', { name: '确认筛选' }).click()
    await expect.poll(() => lastParams.sourceOutletId).toBe('1')
  })
})

test.describe('Series D 后端真实越权（需运行后端）', () => {
  test('伪造/越权档口创建正式订单被拒绝', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1500)
    const captcha = (await page.locator('.captcha-text').textContent())?.trim() || ''
    await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant')
    await page.fill('input[placeholder="您的管理员账号"]', 'admin')
    await page.fill('input[placeholder="••••••••"]', 'admin123')
    await page.fill('input[placeholder="输入验证码"]', captcha)
    await page.click('button[type="submit"]')
    await page.waitForTimeout(2500)
    expect(page.url()).not.toContain('/login')

    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token).toBeTruthy()

    const response = await page.request.post('/api/orders', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        sourceOutletId: 999999999,
        customerName: 'E2E伪造档口客户',
        items: [{ skuId: 1, quantity: 1 }],
      },
    })
    expect(response.status()).toBe(200)
    const body = await response.json()
    expect(body.code).toBe(403)
  })
})
