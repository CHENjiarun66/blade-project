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
  await page.getByRole('button', { name: '编辑订单内容' }).click()

  const dialog = page.getByRole('dialog', { name: '编辑订单内容' })
  await expect(dialog).toBeVisible()
  await dialog.getByPlaceholder('请输入纸质单号').fill('42_0999')
  await dialog.getByText('订货订单', { exact: true }).click()
  const customerName = dialog.getByPlaceholder('可选择已有客户，也可留空按散客保存')
  await customerName.fill('临时新客户')
  const customerSection = dialog.locator('section.edit-section', { hasText: '客户信息' })
  await customerSection.locator('label.edit-field').nth(1).locator('input').fill('13800000000')
  await dialog.getByText('需要送货', { exact: true }).click()
  await dialog.getByPlaceholder('请输入配送地址').fill('测试配送地址')

  const itemRow = dialog.locator('.order-edit-table tbody tr').first()
  const quantityInput = itemRow.locator('.el-input-number input').nth(0)
  await quantityInput.fill('12')
  await quantityInput.blur()
  await dialog.getByRole('button', { name: '保存修改' }).click()

  await expect.poll(() => updatePayload).toBeTruthy()
  expect(updatePayload).toMatchObject({
    sourceDocNo: '42_0999',
    orderType: 'PREORDER',
    customerName: '临时新客户',
    customerPhone: '13800000000',
    needDelivery: 1,
    deliveryAddress: '测试配送地址',
    items: [{ skuId: 101, quantity: 12, price: 20, costPrice: 12 }],
  })
  expect(updatePayload?.customerId).toBeUndefined()
})

test('订单锁定后隐藏内容编辑，但仍可维护备注图片和审计变更档口', async ({ page }) => {
  const permissions = [
    'menu:order',
    'btn:order:edit',
    'btn:order:changeOutlet',
    'data:outlet:all',
    'data:outlet:unassigned',
  ]
  const updatePayloads: Record<string, any>[] = []

  await page.addInitScript((codes) => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }))
    localStorage.setItem('permissions', JSON.stringify(codes))
  }, permissions)

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (url.pathname === '/api/auth/codes') return route.fulfill({ json: permissions })
    if (url.pathname === '/api/user/info') return route.fulfill({ json: { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] } })
    if (url.pathname === '/api/outlets/options') {
      return route.fulfill({ json: ok({
        scopeType: 'ALL',
        peopleScope: 'ALL_USERS',
        locked: false,
        defaultOutletId: 1,
        items: [
          { id: 1, outletCode: 'YL', outletName: '御龙', status: 1 },
          { id: 2, outletCode: 'HZ', outletName: '杭州档', status: 1 },
        ],
      }) })
    }
    if (url.pathname === '/api/orders/118' && route.request().method() === 'PUT') {
      updatePayloads.push(route.request().postDataJSON())
      return route.fulfill({ json: ok(null) })
    }
    if (url.pathname === '/api/orders/118') {
      return route.fulfill({ json: ok({
        id: 118,
        orderNo: 'ORD-LOCKED-118',
        orderDate: '2026-09-15',
        sourceDocNo: '42_0877',
        sourceOutletId: 1,
        sourceOutletCode: 'YL',
        sourceShop: '御龙',
        orderType: 'SPOT',
        orderTypeName: '现货订单',
        customerId: 12,
        customerName: '锁定客户',
        customerPhone: '',
        customerAddress: '',
        salesmanId: 1,
        salesmanName: '管理员',
        totalAmount: 200,
        totalCostAmount: 120,
        grossProfit: 80,
        paidAmount: 200,
        netReceivedAmount: 200,
        balanceAmount: 0,
        freightAmount: 0,
        freightCost: 0,
        status: 4,
        statusName: '已完成',
        paymentStatus: 2,
        paymentStatusName: '已结清',
        adjustmentStatus: 'NONE',
        needDelivery: 0,
        deliveryAddress: '',
        isDelivered: 1,
        remark: '旧备注',
        images: '',
        items: [],
        collectionStatus: 'SETTLED',
        fulfillmentStatus: 'COMPLETED',
        fulfillmentMode: 'RECORD_ONLY',
        legacyUnmigrated: false,
        allowedActions: [],
        financialRecords: [],
      }) })
    }
    if (
      url.pathname === '/api/order-deliveries/order/118'
      || url.pathname === '/api/orders/118/delivery-plan'
      || url.pathname === '/api/orders/118/adjustment'
    ) return route.fulfill({ json: ok([]) })
    return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })

  await page.goto('/orders/118')
  await expect(page.getByRole('button', { name: '编辑订单内容' })).toHaveCount(0)

  await page.getByRole('button', { name: '维护备注/图片' }).click()
  const assetsDialog = page.getByRole('dialog', { name: '维护备注/图片' })
  await assetsDialog.getByPlaceholder('补充订单说明').fill('锁定后补充的备注')
  await assetsDialog.getByRole('button', { name: '保存备注/图片' }).click()
  await expect.poll(() => updatePayloads.length).toBe(1)
  expect(updatePayloads[0]).toMatchObject({ id: 118, remark: '锁定后补充的备注', images: '' })

  await page.getByRole('button', { name: '变更来源档口' }).click()
  const outletDialog = page.getByRole('dialog', { name: '变更来源档口' })
  await outletDialog.locator('[data-testid="outlet-select"]').click()
  await page.getByRole('option', { name: /杭州档/ }).click()
  await outletDialog.getByRole('button', { name: '确认变更' }).click()
  await expect(page.getByText('请填写档口变更原因')).toBeVisible()
  await outletDialog.getByPlaceholder('请说明为什么需要变更来源档口').fill('订单来源录入错误')
  await outletDialog.getByRole('button', { name: '确认变更' }).click()
  await expect.poll(() => updatePayloads.length).toBe(2)
  expect(updatePayloads[1]).toMatchObject({
    id: 118,
    sourceOutletId: 2,
    outletChangeReason: '订单来源录入错误',
  })
})

test('订单列表只保留查看入口，点击后进入统一详情页', async ({ page }) => {
  const permissions = ['menu:order', 'btn:order:view']
  await page.addInitScript((codes) => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员' }))
    localStorage.setItem('permissions', JSON.stringify(codes))
  }, permissions)
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (url.pathname === '/api/auth/codes') return route.fulfill({ json: permissions })
    if (url.pathname === '/api/user/info') return route.fulfill({ json: { userId: '1', username: 'admin', realName: '管理员' } })
    if (url.pathname === '/api/outlets/options') return route.fulfill({ json: ok({ scopeType: 'ALL', peopleScope: 'SELF', locked: true, defaultOutletId: 1, items: [{ id: 1, outletCode: 'YL', outletName: '御龙', status: 1 }] }) })
    if (url.pathname === '/api/orders') {
      return route.fulfill({ json: ok({ records: [{
        id: 118,
        orderNo: 'ORD-118',
        orderDate: '2026-09-15',
        sourceDocNo: '42_0877',
        sourceOutletId: 1,
        sourceShop: '御龙',
        customerName: '测试客户',
        totalAmount: 200,
        paidAmount: 0,
        status: 0,
        statusName: '已确认',
        paymentStatus: 0,
        paymentStatusName: '未收款',
        needDelivery: 0,
        adjustmentStatus: 'NONE',
        images: '',
        createTime: '2026-09-15 10:00:00',
      }], total: 1, size: 10, current: 1, pages: 1 }) })
    }
    return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })

  await page.goto('/orders')
  await expect(page.getByRole('button', { name: '查看详情' })).toBeVisible()
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '查看详情' }).click()
  await expect(page).toHaveURL(/\/orders\/118$/)
})
