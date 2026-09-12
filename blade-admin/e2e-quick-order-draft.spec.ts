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
      await route.fulfill({ json: ok({ records: [], total: 0, size: 1000, current: 1, pages: 0 }) })
      return
    }
    if (url.pathname === '/api/customers') {
      await route.fulfill({ json: ok({ records: [], total: 0, size: 10, current: 1, pages: 0 }) })
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
  await page.getByPlaceholder('如 6月-001').fill('QUICK-DRAFT-001')
  await page.getByPlaceholder('输入客户名称筛选').fill('尚未建档客户')
  await page.getByPlaceholder('纸单备注、特殊说明').fill('先存草稿，商品稍后补')

  await page.getByRole('button', { name: '添加到草稿' }).click()

  await expect(page).toHaveURL(/\/orders\/quick$/)
  await expect(page.getByText('已添加到草稿，可从左侧“订单草稿”继续填写')).toBeVisible()
  expect(createPayload).toMatchObject({
    sourceOrderNo: 'QUICK-DRAFT-001',
    sourceShop: '御龙',
    orderType: 'SPOT',
    customerName: '尚未建档客户',
    note: '先存草稿，商品稍后补',
    paidAmount: 0,
    freightAmount: 0,
    freightCost: 0,
    needDelivery: 0,
  })
  expect(createPayload?.externalRefNo).toMatch(/^manual-/)
  expect(createPayload?.items).toHaveLength(1)
  expect(createPayload?.items[0].skuId).toBeUndefined()

  await page.getByPlaceholder('纸单备注、特殊说明').fill('继续修改后更新同一草稿')
  await page.getByRole('button', { name: '添加到草稿' }).click()

  await expect(page.getByText('草稿已更新，可从左侧“订单草稿”继续填写')).toBeVisible()
  expect(updatePayload?.externalRefNo).toBe(createPayload?.externalRefNo)
  expect(updatePayload?.note).toBe('继续修改后更新同一草稿')
})
