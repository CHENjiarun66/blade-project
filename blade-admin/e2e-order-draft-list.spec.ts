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
    if (url.pathname === '/api/order-drafts/batches') {
      await route.fulfill({ json: ok([
        { sourceBatchNo: '40', draftCount: 1, latestUpdateTime: '2026-09-14T12:00:00' },
        { sourceBatchNo: '39', draftCount: 26, latestUpdateTime: '2026-09-13T12:00:00' },
      ]) })
      return
    }
    if (url.pathname === '/api/products') {
      await route.fulfill({ status: 500, json: { code: 500, message: '商品目录暂时不可用' } })
      return
    }
    if (url.pathname === '/api/order-drafts/3') {
      await route.fulfill({ json: ok({
        id: 3,
        externalRefNo: 'paper-batch-40-0004001',
        entrySource: 'MANUAL',
        sourceBatchNo: '40',
        sourceOrderNo: '0004001',
        customerName: '第四十单客户',
        orderDate: '2026-09-14',
        paidAmount: 0,
        freightAmount: 0,
        calculatedTotalAmount: 0,
        warnings: [],
        status: 'EDITING',
        sourceFileIds: [],
        createTime: '2026-09-14T12:00:00',
        updateTime: '2026-09-14T12:00:00',
        items: [{
          id: 31,
          sourceRowNo: 1,
          rawProductCode: '616-24#',
          rawDescription: '整款录入（颜色/尺码未指定）',
          productId: 131,
          skuId: 999,
          quantity: 10,
          salePrice: 34,
          costPrice: 29,
          paperAmount: 340,
          systemReferencePrice: 34,
          matchStatus: 'MATCHED',
          warnings: [],
        }],
      }) })
      return
    }
    if (url.pathname === '/api/order-drafts') {
      const batch = url.searchParams.get('sourceBatchNo')
      const records = batch === '40' ? [{
        id: 3,
        externalRefNo: 'paper-batch-40-0004001',
        entrySource: 'MANUAL',
        sourceBatchNo: '40',
        sourceOrderNo: '0004001',
        customerName: '第四十单客户',
        orderDate: '2026-09-14',
        paperTotalAmount: 3200,
        status: 'EDITING',
        itemCount: 4,
        unresolvedCount: 0,
        warningCount: 0,
        sourceFileCount: 0,
        updateTime: '2026-09-14T12:00:00',
      }] : [{
        id: 1,
        externalRefNo: 'paper-batch-39-0003901',
        entrySource: 'AGENT',
        sourceBatchNo: '39',
        sourceOrderNo: '0003901',
        sourceFileId: 9001,
        sourceFileCount: 2,
        customerName: 'K. Sankoh',
        orderDate: '2026-09-13',
        paperTotalAmount: 5100,
        status: 'EDITING',
        itemCount: 2,
        unresolvedCount: 1,
        warningCount: 1,
        updateTime: '2026-09-13T12:00:00',
      }]
      await route.fulfill({ json: ok({ records, total: batch ? records.length : 27, size: 20, current: 1, pages: batch ? 1 : 2 }) })
      return
    }
    if (url.pathname === '/api/files/9001/preview') {
      await route.fulfill({
        status: 200,
        contentType: 'image/svg+xml',
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="90" height="120"><rect width="100%" height="100%" fill="#f8fafc"/></svg>',
      })
      return
    }
    await route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })
})

test('草稿列表支持分页展示、批次筛选并跳转到具体草稿', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/orders/drafts')

  await expect(page.getByRole('heading', { name: '草稿订单列表' })).toBeVisible()
  await expect(page.getByText('27 张待处理', { exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '0003901 paper-batch-39-0003901' })).toBeVisible()
  await expect(page.getByText('1 行待匹配')).toBeVisible()
  await expect(page.getByText('共 27 张待处理草稿')).toBeVisible()

  await page.getByRole('combobox').first().click()
  await page.getByRole('option', { name: '第 40 单（1）' }).click()
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.getByText('第四十单客户')).toBeVisible()
  await expect(page.getByText('第 40 单', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '0004001' }).click()
  await expect(page).toHaveURL(/\/orders\/drafts\/3$/)
  await expect(page.getByRole('heading', { name: '草稿订单详情' })).toBeVisible()
  await expect(page.getByText('暂无符合条件的订单草稿')).toHaveCount(0)
  await expect(page.locator('input[placeholder="如 41"]')).toHaveValue('40')
  await expect(page.locator('input[placeholder="如 0135"]')).toHaveValue('0004001')
  await expect(page.getByText('616-24# · 整款录入（颜色/尺码未指定）', { exact: true })).toBeVisible()
})
