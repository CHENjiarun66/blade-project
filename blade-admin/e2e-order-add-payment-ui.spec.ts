import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test.beforeEach(async ({ page }) => {
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
      await route.fulfill({ json: ok({
        id: 118,
        orderNo: 'ORD202609150021',
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
        salesmanId: 1,
        salesmanName: '管理员',
        totalAmount: 14960,
        totalCostAmount: 12760,
        grossProfit: 2200,
        paidAmount: 14945,
        netReceivedAmount: 14945,
        balanceAmount: 15,
        depositAmount: 0,
        freightAmount: 0,
        freightCost: 0,
        status: 1,
        statusName: '已确认',
        paymentStatus: 1,
        paymentStatusName: '部分收款',
        adjustmentStatus: 'NONE',
        needDelivery: 0,
        deliveryAddress: '',
        isDelivered: 0,
        remark: '',
        images: '',
        items: [],
        collectionStatus: 'PARTIAL',
        fulfillmentStatus: 'CONFIRMED',
        fulfillmentMode: 'UNDECIDED',
        legacyUnmigrated: false,
        allowedActions: ['recordPayment'],
        financialRecords: [],
      }) })
      return
    }
    if (
      url.pathname === '/api/order-deliveries/order/118'
      || url.pathname === '/api/orders/118/delivery-plan'
      || url.pathname === '/api/orders/118/adjustment'
    ) {
      await route.fulfill({ json: ok([]) })
      return
    }
    await route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })
})

test('加收金额默认一位小数且加减按钮按一元步进', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/orders/118')

  await page.getByRole('button', { name: '加收金额' }).click()
  const dialog = page.getByRole('dialog', { name: '加收金额' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('¥ 14960.0', { exact: true })).toBeVisible()
  await expect(dialog.getByText('¥ 14945.0', { exact: true }).first()).toBeVisible()
  await expect(dialog.getByText('¥ 15.0', { exact: true }).first()).toBeVisible()

  const amountInput = dialog.locator('.el-input-number input')
  await expect(amountInput).toHaveValue('0.0')
  await dialog.locator('.el-input-number__increase').click()
  await expect(amountInput).toHaveValue('1.0')
  await dialog.locator('.el-input-number__increase').click()
  await expect(amountInput).toHaveValue('2.0')
  await dialog.locator('.el-input-number__decrease').click()
  await expect(amountInput).toHaveValue('1.0')

  await amountInput.fill('2.5')
  await amountInput.blur()
  await expect(amountInput).toHaveValue('2.5')
  await expect(dialog.getByText('¥ 14947.5', { exact: true })).toBeVisible()
  await expect(dialog.getByText('¥ 12.5', { exact: true })).toBeVisible()
})
