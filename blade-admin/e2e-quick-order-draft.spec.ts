import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test('快速录单可将未完成内容暂存为同一张手工草稿且不跳页', async ({ page }) => {
  let createPayload: Record<string, any> | undefined
  let updatePayload: Record<string, any> | undefined

  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:order', 'btn:order:create']))
  })

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
        id: 61,
        productCode: '616-24#',
        name: '整款录入测试商品',
        status: 1,
        wholesalePrice: 34,
        costPrice: 29,
        skus: [{
          id: 61624,
          skuCode: '616-24#-UNSPECIFIED-UNSPEC',
          skuType: 'PLACEHOLDER',
          placeholder: true,
          colorId: 9998,
          colorName: 'æœªæŒ‡å®šé¢œè‰²',
          sizeId: 9998,
          sizeName: 'UNSPEC',
          price: 34,
          costPrice: 29,
          status: 1,
        }],
      }], total: 1, size: 1000, current: 1, pages: 1 }) })
      return
    }
    if (url.pathname === '/api/customers') {
      await route.fulfill({ json: ok({ records: [], total: 0, size: 10, current: 1, pages: 0 }) })
      return
    }
    if (url.pathname === '/api/outlets/options') {
      await route.fulfill({ json: ok({
        scopeType: 'ALL',
        peopleScope: 'ALL_USERS',
        locked: false,
        defaultOutletId: 1,
        items: [{ id: 1, outletCode: 'YL', outletName: '御龙', status: 1 }],
      }) })
      return
    }
    if (url.pathname === '/api/files/upload' && route.request().method() === 'POST') {
      await route.fulfill({ json: ok({
        id: 7001,
        originalName: 'quick-order.jpg',
        contentType: 'image/jpeg',
        fileSize: 128,
        url: '/api/files/7001/preview',
        fileType: 'IMAGE',
      }) })
      return
    }
    if (url.pathname === '/api/order-drafts' && route.request().method() === 'POST') {
      createPayload = route.request().postDataJSON()
      await route.fulfill({ json: ok({
        externalRefNo: createPayload?.externalRefNo,
        status: 'CREATED_WITH_WARNINGS',
        draftId: 88,
        warnings: ['ITEM_1_SKU_UNMATCHED'],
      }) })
      return
    }
    if (url.pathname === '/api/order-drafts/88' && route.request().method() === 'PUT') {
      updatePayload = route.request().postDataJSON()
      await route.fulfill({ json: ok(null) })
      return
    }
    await route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })

  await page.goto('/orders/quick')
  await page.getByPlaceholder('如 41').fill('41')
  await page.getByPlaceholder('如 0135').fill('0135')

  const productSearch = page.locator('.batch-product-entry-row').getByRole('combobox')
  await productSearch.fill('616-24#')
  await page.getByRole('option', { name: /616-24#.*整款录入测试商品/ }).click()
  await expect(page.getByRole('cell', { name: '整款录入（颜色/尺码未指定）' })).toBeVisible()
  await expect(page.getByText('未指定尺码')).toBeVisible()
  await expect(page.getByText('æœªæŒ‡å®šé¢œè‰²')).toHaveCount(0)

  await page.getByPlaceholder('输入客户名称筛选').fill('尚未建档客户')
  await page.getByPlaceholder('纸单备注、特殊说明').fill('先存草稿，商品稍后补')
  await page.locator('input[type="file"]').setInputFiles({
    name: 'quick-order.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from('quick-order-image'),
  })

  await page.getByRole('button', { name: '添加到草稿' }).click()

  await expect(page).toHaveURL(/\/orders\/quick$/)
  await expect(page.getByText('已添加到草稿，可从左侧“草稿订单列表”继续填写')).toBeVisible()
  expect(createPayload).toMatchObject({
    sourceBatchNo: '41',
    sourceOrderNo: '0135',
    sourceOutletId: 1,
    orderType: 'SPOT',
    customerName: '尚未建档客户',
    note: '先存草稿，商品稍后补',
    paidAmount: 0,
    freightAmount: 0,
    freightCost: 0,
    needDelivery: 0,
    sourceFileIds: [7001],
  })
  expect(createPayload?.externalRefNo).toMatch(/^manual-/)
  expect(createPayload?.items).toHaveLength(1)
  expect(createPayload?.items[0].skuId).toBeUndefined()

  await page.getByPlaceholder('纸单备注、特殊说明').fill('继续修改后更新同一草稿')
  await page.getByRole('button', { name: '添加到草稿' }).click()

  await expect(page.getByText('草稿已更新，可从左侧“草稿订单列表”继续填写')).toBeVisible()
  expect(updatePayload?.externalRefNo).toBe(createPayload?.externalRefNo)
  expect(updatePayload?.note).toBe('继续修改后更新同一草稿')
})
