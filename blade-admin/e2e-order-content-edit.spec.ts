import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test('未结清且未进入履约的订单可修改客户和商品明细', async ({ page }) => {
  let updatePayload: Record<string, any> | undefined

  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:order', 'btn:order:edit', 'field:cost_price']))
  })

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    if (url.pathname === '/api/auth/codes') {
      await route.fulfill({ json: ['menu:order', 'btn:order:edit', 'field:cost_price'] })
      return
    }
    if (url.pathname === '/api/user/info') {
      await route.fulfill({ json: { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] } })
      return
    }
    if (url.pathname === '/api/orders/118' && route.request().method() === 'PUT') {
      updatePayload = route.request().postDataJSON()
      await route.fulfill({ json: ok(null) })
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
        customerId: 12,
        customerName: '原客户',
        customerPhone: '10086',
        customerAddress: '原地址',
        salesmanId: 1,
        salesmanName: '管理员',
        totalAmount: 200,
        totalCostAmount: 120,
        grossProfit: 80,
        paidAmount: 50,
        netReceivedAmount: 50,
        balanceAmount: 150,
        depositAmount: 0,
        freightAmount: 0,
        freightCost: 0,
        status: 0,
        statusName: '已确认',
        paymentStatus: 1,
        paymentStatusName: '部分收款',
        adjustmentStatus: 'NONE',
        needDelivery: 0,
        deliveryAddress: '',
        isDelivered: 0,
        remark: '',
        images: '',
        items: [{
          id: 900,
          skuId: 101,
          skuCode: '6000-BLACK-M',
          productName: '6000 测试款',
          colorName: '黑色',
          sizeName: 'M',
          price: 20,
          costPrice: 12,
          quantity: 10,
          subtotal: 200,
          costAmount: 120,
        }],
        collectionStatus: 'PARTIAL',
        fulfillmentStatus: 'CONFIRMED',
        fulfillmentMode: 'UNDECIDED',
        legacyUnmigrated: false,
        allowedActions: ['editOrder', 'recordPayment'],
        financialRecords: [],
      }) })
      return
    }
    if (url.pathname === '/api/products') {
      await route.fulfill({ json: ok({ records: [{
        id: 10,
        productCode: '6000',
        name: '6000 测试款',
        status: 1,
        wholesalePrice: 20,
        costPrice: 12,
        skus: [{
          id: 101,
          skuCode: '6000-BLACK-M',
          skuType: 'NORMAL',
          placeholder: false,
          colorId: 1,
          colorName: '黑色',
          sizeId: 1,
          sizeName: 'M',
          price: 20,
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

  await page.goto('/orders/118')
  await page.getByRole('button', { name: '编辑订单' }).click()

  const dialog = page.getByRole('dialog', { name: '编辑订单' })
  await expect(dialog).toBeVisible()
  const customerName = dialog.getByPlaceholder('可选择已有客户，也可留空按散客保存')
  await customerName.fill('临时新客户')
  await dialog.locator('label.edit-field').nth(1).locator('input').fill('13800000000')

  const itemRow = dialog.locator('.order-edit-table tbody tr').first()
  const quantityInput = itemRow.locator('.el-input-number input').nth(0)
  await quantityInput.fill('12')
  await quantityInput.blur()
  await dialog.getByRole('button', { name: '保存修改' }).click()

  await expect.poll(() => updatePayload).toBeTruthy()
  expect(updatePayload).toMatchObject({
    customerName: '临时新客户',
    customerPhone: '13800000000',
    items: [{ skuId: 101, quantity: 12, price: 20, costPrice: 12 }],
  })
  expect(updatePayload?.customerId).toBeUndefined()
})
