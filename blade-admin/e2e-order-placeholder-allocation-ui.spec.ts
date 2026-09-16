import { expect, test, type Page } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

const placeholderItem = {
  id: 22,
  orderId: 118,
  productId: 24,
  productName: '616-24#',
  skuId: 344,
  skuCode: '616-24#-UNSPECIFIED-UNSPEC',
  skuType: 'PLACEHOLDER',
  variantUnresolved: true,
  colorName: 'æœªæŒ‡å®šé¢œè‰²',
  sizeName: 'UNSPEC',
  price: 34,
  costPrice: 29,
  quantity: 200,
  subtotal: 6800,
  costAmount: 5800,
  grossProfit: 1000,
}

function orderFixture(id: number, status: number) {
  return {
    id,
    orderNo: `ORD-PLACEHOLDER-${id}`,
    orderDate: '2026-09-15',
    sourceDocNo: '42_0877',
    sourceShop: '御龙',
    orderType: 'SPOT',
    orderTypeName: '现货订单',
    customerId: 1,
    customerName: '散客',
    customerPhone: '',
    customerAddress: '',
    warehouseName: '',
    totalAmount: 6800,
    totalCostAmount: 5800,
    grossProfit: 1000,
    paidAmount: 6800,
    netReceivedAmount: 6800,
    balanceAmount: 0,
    depositAmount: 0,
    freightAmount: 0,
    freightCost: 0,
    status,
    statusName: status === 2 ? '配货中' : '已确认',
    paymentStatus: 2,
    paymentStatusName: '已结清',
    adjustmentStatus: status === 2 ? 'PENDING' : 'NONE',
    needDelivery: 1,
    deliveryAddress: '',
    isDelivered: 0,
    remark: '',
    images: '',
    items: [{ ...placeholderItem, orderId: id }],
    collectionStatus: 'SETTLED',
    fulfillmentStatus: status === 2 ? 'ALLOCATING' : 'WAITING_ALLOCATION',
    fulfillmentMode: 'STOCK_LINKED',
    legacyUnmigrated: false,
    allowedActions: status === 2 ? ['confirmAllocation'] : ['startAllocation'],
    financialRecords: [],
  }
}

async function installMocks(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:order']))
  })

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    if (url.pathname === '/api/auth/codes') {
      await route.fulfill({ json: ['menu:order'] })
      return
    }
    if (url.pathname === '/api/user/info') {
      await route.fulfill({ json: { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] } })
      return
    }
    if (url.pathname === '/api/orders/118') {
      await route.fulfill({ json: ok(orderFixture(118, 1)) })
      return
    }
    if (url.pathname === '/api/orders/119') {
      await route.fulfill({ json: ok(orderFixture(119, 2)) })
      return
    }
    if (url.pathname === '/api/orders/119/delivery-plan') {
      await route.fulfill({ json: ok([{
        id: 1,
        orderId: 119,
        orderNo: 'ORD-PLACEHOLDER-119',
        orderItemId: 22,
        skuId: 344,
        skuCode: '616-24#-UNSPECIFIED-UNSPEC',
        productName: '616-24#',
        colorName: 'æœªæŒ‡å®šé¢œè‰²',
        sizeName: 'UNSPEC',
        warehouseId: 0,
        warehouseName: '',
        plannedQty: 200,
        allocatedQty: 200,
        outQty: 0,
        status: 'PENDING',
        remark: '',
        createTime: '2026-09-15T12:00:00',
      }]) })
      return
    }
    if (url.pathname === '/api/warehouse/all') {
      await route.fulfill({ json: ok([]) })
      return
    }
    if (
      url.pathname === '/api/order-deliveries/order/118'
      || url.pathname === '/api/orders/118/delivery-plan'
      || url.pathname === '/api/orders/118/adjustment'
      || url.pathname === '/api/order-deliveries/order/119'
      || url.pathname === '/api/orders/119/adjustment'
    ) {
      await route.fulfill({ json: ok([]) })
      return
    }
    await route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })
}

test.beforeEach(async ({ page }) => {
  await installMocks(page)
})

test('含整款录入明细时在打开配货弹窗前提示先拆分', async ({ page }) => {
  await page.goto('/orders/118')

  await page.getByRole('button', { name: '创建配货计划' }).click()

  await expect(page.getByText('还有 1 行商品未明确颜色/尺码，请先拆分到具体 SKU 后再创建配货计划')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '创建配货计划' })).toHaveCount(0)
})

test('遗留配货计划中的占位 SKU 使用业务中文而不暴露乱码和 UNSPEC', async ({ page }) => {
  await page.goto('/orders/119')

  await page.getByRole('button', { name: '编辑配货计划' }).click()
  const dialog = page.getByRole('dialog', { name: '编辑配货计划' })

  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('整款录入（颜色/尺码未指定）', { exact: true })).toBeVisible()
  await expect(dialog.getByText(/æœª|UNSPEC/)).toHaveCount(0)
})
