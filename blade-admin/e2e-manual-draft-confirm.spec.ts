import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test('手工草稿可恢复完整快速录单字段并确认生成正式订单', async ({ page }) => {
  let savePayload: Record<string, any> | undefined
  let confirmed = false

  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:order', 'btn:order:create']))
  })

  const draft = {
    id: 2,
    externalRefNo: 'manual-e2e-2',
    entrySource: 'MANUAL',
    sourceOrderNo: 'QUICK-DRAFT-002',
    sourceShop: '御龙',
    orderType: 'SPOT',
    customerName: '手工草稿客户',
    customerPhone: '13800138000',
    customerCountryCode: '+86',
    customerAddress: '客户地址',
    orderDate: '2026-09-13',
    paidAmount: 40,
    freightAmount: 8,
    freightCost: 3,
    needDelivery: 1,
    deliveryAddress: '送货地址',
    calculatedTotalAmount: 100,
    note: '手工暂存备注',
    warnings: [],
    status: 'EDITING',
    createTime: '2026-09-13T10:00:00',
    updateTime: '2026-09-13T10:00:00',
    items: [{
      id: 21,
      sourceRowNo: 1,
      productId: 10,
      skuId: 101,
      quantity: 2,
      salePrice: 50,
      costPrice: 12,
      paperAmount: 100,
      systemReferencePrice: 55,
      matchStatus: 'MATCHED',
      warnings: [],
    }],
  }

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    if (url.pathname === '/api/auth/codes') {
      await route.fulfill({ json: ['menu:order', 'btn:order:create'] })
      return
    }
    if (url.pathname === '/api/user/info') {
      await route.fulfill({ json: { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] } })
      return
    }
    if (url.pathname === '/api/products') {
      await route.fulfill({ json: ok({ records: [{
        id: 10,
        productCode: '7000#',
        name: '测试商品',
        status: 1,
        wholesalePrice: 55,
        costPrice: 12,
        skus: [{
          id: 101,
          skuCode: '7000#-BLACK-S',
          skuType: 'NORMAL',
          placeholder: false,
          colorId: 1,
          colorName: '黑色',
          sizeId: 1,
          sizeName: 'S',
          price: 55,
          costPrice: 12,
          status: 1,
        }],
      }], total: 1, size: 1000, current: 1, pages: 1 }) })
      return
    }
    if (url.pathname === '/api/customers') {
      await route.fulfill({ json: ok({ records: [], total: 0, size: 10, current: 1, pages: 0 }) })
      return
    }
    if (url.pathname === '/api/order-drafts' && route.request().method() === 'GET') {
      await route.fulfill({ json: ok({ records: [{
        id: 2,
        externalRefNo: draft.externalRefNo,
        entrySource: 'MANUAL',
        sourceOrderNo: draft.sourceOrderNo,
        customerName: draft.customerName,
        paperTotalAmount: 108,
        status: 'EDITING',
        itemCount: 1,
        unresolvedCount: 0,
        warningCount: 0,
        updateTime: draft.updateTime,
      }], total: 1, size: 100, current: 1, pages: 1 }) })
      return
    }
    if (url.pathname === '/api/order-drafts/2' && route.request().method() === 'GET') {
      await route.fulfill({ json: ok(draft) })
      return
    }
    if (url.pathname === '/api/order-drafts/2' && route.request().method() === 'PUT') {
      savePayload = route.request().postDataJSON()
      await route.fulfill({ json: ok(null) })
      return
    }
    if (url.pathname === '/api/order-drafts/2/confirm') {
      confirmed = true
      draft.status = 'CONFIRMED'
      await route.fulfill({ json: ok({ draftId: 2, orderId: 999, alreadyConfirmed: false }) })
      return
    }
    await route.fulfill({ json: ok(null) })
  })

  await page.goto('/orders/drafts')

  await expect(page.getByText('订单应收').first()).toBeVisible()
  await expect(page.getByText('¥108.00').first()).toBeVisible()
  await expect(page.getByText('实收金额').first()).toBeVisible()
  await expect(page.getByText('结算与配送')).toBeVisible()
  expect(await page.locator('textarea').evaluateAll(elements =>
    elements.map(element => (element as HTMLTextAreaElement).value),
  )).toContain('送货地址')

  await page.getByRole('button', { name: '确认并生成订单' }).click()
  await page.getByRole('button', { name: '确认生成正式订单' }).click()

  await expect(page).toHaveURL(/\/orders\/999$/)
  expect(savePayload).toMatchObject({
    sourceShop: '御龙',
    orderType: 'SPOT',
    paidAmount: 40,
    freightAmount: 8,
    freightCost: 3,
    needDelivery: 1,
    deliveryAddress: '送货地址',
  })
  expect(savePayload?.items[0].costPrice).toBe(12)
  expect(confirmed).toBe(true)
})
