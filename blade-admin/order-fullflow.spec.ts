import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

const BASE_URL = 'http://127.0.0.1:5777'
const API_BASE = process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api'

type LoginResult = {
  token: string
  codes: string[]
}

async function apiLogin(request: APIRequestContext): Promise<LoginResult> {
  const loginRes = await request.post(`${API_BASE}/auth/login`, {
    data: {
      tenantCode: 'test_tenant',
      username: 'admin',
      password: 'admin123',
    },
  })
  expect(loginRes.ok()).toBeTruthy()
  const loginJson = await loginRes.json()
  const token = loginJson.accessToken || loginJson.token
  expect(token).toBeTruthy()

  const codesRes = await request.get(`${API_BASE}/auth/codes`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(codesRes.ok()).toBeTruthy()
  const codes = await codesRes.json()
  expect(Array.isArray(codes)).toBeTruthy()

  return { token, codes }
}

async function createSeedProductAndInventory(request: APIRequestContext, token: string) {
  const unique = Date.now()
  const productCode = `PW-E2E-${unique}`
  const productName = `订单E2E商品-${unique}`

  const createProductRes = await request.post(`${API_BASE}/products`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: productName,
      productCode,
      categoryId: 1,
      unit: '件',
      price: 188,
      description: '订单全流程自动化测试商品',
      colorIds: [1],
      sizeIds: [1],
    },
  })
  expect(createProductRes.ok()).toBeTruthy()
  const createProductJson = await createProductRes.json()
  const productId = createProductJson.data
  expect(productId).toBeTruthy()

  const productDetailRes = await request.get(`${API_BASE}/products/${productId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(productDetailRes.ok()).toBeTruthy()
  const productDetailJson = await productDetailRes.json()
  const sku = productDetailJson.data?.skus?.[0]
  expect(sku?.id).toBeTruthy()

  const inventoryInRes = await request.post(`${API_BASE}/inventory/in`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      warehouseId: 1,
      remark: '订单全流程自动化测试入库',
      items: [
        {
          skuId: sku.id,
          quantity: 50,
        },
      ],
    },
  })
  expect(inventoryInRes.ok()).toBeTruthy()

  return {
    productCode,
    productName,
  }
}

async function uiLogin(page: Page) {
  await page.goto(`${BASE_URL}/login`)
  await page.waitForLoadState('networkidle')

  const captcha = (await page.locator('.captcha-text').textContent())?.trim() || ''
  await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant')
  await page.fill('input[placeholder="您的管理员账号"]', 'admin')
  await page.fill('input[placeholder="••••••••"]', 'admin123')
  await page.fill('input[placeholder="输入验证码"]', captcha)
  await page.click('button[type="submit"]')

  await page.waitForURL(/dashboard|orders|inventory|products|clients|system/, { timeout: 15000 })
}

async function openNewOrderPage(page: Page) {
  await page.goto(`${BASE_URL}/orders/new`)
  await page.waitForLoadState('networkidle')
  await expect(page.getByRole('heading', { name: '新建订单' })).toBeVisible()
}

test.describe('订单系统全流程 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
  })

  test('登录 -> 新建订单 -> 确认收款 -> 配货 -> 发货 -> 完成', async ({ page, request }) => {
    const { token } = await apiLogin(request)
    const seed = await createSeedProductAndInventory(request, token)

    await uiLogin(page)
    await openNewOrderPage(page)

    await page.fill('input[placeholder*="电话"]', `139${String(Date.now()).slice(-8)}`)
    await page.fill('input[placeholder="请输入客户名称"]', '订单全流程E2E客户')
    await page.fill('input[placeholder="请输入客户地址"]', '上海市订单测试地址')

    const warehouseSelect = page.locator('.el-select').first()
    await warehouseSelect.click()
    await page.locator('.el-select-dropdown__item').first().click()

    await page.getByRole('button', { name: '添加商品' }).click()
    await page.fill('input[placeholder="搜索商品名称或款号"]', seed.productCode)
    await page.waitForTimeout(500)
    await page.locator(`text=${seed.productName}`).first().click()
    await page.waitForTimeout(500)

    const qtyInput = page.locator('.product-dialog input[type="number"]:not([disabled])').last()
    await qtyInput.fill('2')
    await page.getByRole('button', { name: /批量添加/ }).click()

    await expect(page.locator('tbody tr')).toContainText([seed.productName])

    await page.getByRole('button', { name: '保存订单并进入详情' }).click()
    await page.waitForURL(/\/orders\/\d+$/, { timeout: 15000 })

    const orderUrl = page.url()
    const orderId = Number(orderUrl.split('/').pop())
    expect(orderId).toBeTruthy()
    await expect(page.getByText('订单详情')).toBeVisible()

    await page.getByRole('button', { name: '确认收款' }).click()
    await page.locator('.el-dialog input').last().fill('376')
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByRole('button', { name: '创建配货计划' })).toBeVisible({ timeout: 15000 })

    await page.getByRole('button', { name: '创建配货计划' }).click()
    await page.waitForTimeout(500)
    const planWarehouseSelect = page.locator('.el-dialog .el-select').first()
    if (await planWarehouseSelect.count()) {
      await planWarehouseSelect.click()
      const option = page.locator('.el-select-dropdown__item').first()
      if (await option.count()) {
        await option.click()
      }
    }
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByRole('button', { name: '确认调整方案' })).toBeVisible({ timeout: 15000 })

    await page.getByRole('button', { name: '确认调整方案' }).click()
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByRole('button', { name: '发货' })).toBeVisible({ timeout: 15000 })

    await page.getByRole('button', { name: '发货' }).click()
    await page.getByRole('button', { name: '确认发货' }).click()
    await expect(page.getByRole('button', { name: '完成订单' })).toBeVisible({ timeout: 15000 })

    await page.getByRole('button', { name: '完成订单' }).click()
    await page.getByRole('button', { name: '确认' }).click()

    await expect(page.getByText('已完成')).toBeVisible({ timeout: 15000 })
  })
})
