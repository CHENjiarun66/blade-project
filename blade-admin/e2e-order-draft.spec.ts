import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })
let draftImageIds: number[]
let savedDraftPayload: Record<string, any> | undefined

test.beforeEach(async ({ page }) => {
  draftImageIds = [9001, 9002]
  savedDraftPayload = undefined
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
    if (/\/api\/files\/(9001|9002|9003)\/preview/.test(url.pathname)) {
      const pageNo = url.pathname.match(/900(\d)/)?.[1] || '1'
      await route.fulfill({
        status: 200,
        contentType: 'image/svg+xml',
        body: `<svg xmlns="http://www.w3.org/2000/svg" width="900" height="1300"><rect width="100%" height="100%" fill="#fffdf7"/><text x="70" y="120" font-size="52" fill="#18212f">纸单原图 ${pageNo}</text><path d="M70 190h760M70 290h760M70 390h760M70 490h760M70 590h760M70 690h760M70 790h760" stroke="#94a3b8" stroke-width="3"/><text x="70" y="920" font-size="38" fill="#334155">款号 618-3#　数量 60　单价 42.50</text></svg>`,
      })
      return
    }
    if (url.pathname === '/api/files/upload' && route.request().method() === 'POST') {
      await route.fulfill({ json: ok({
        id: 9003,
        originalName: 'new-paper.jpg',
        contentType: 'image/jpeg',
        fileSize: 128,
        url: '/api/files/9003/preview',
        fileType: 'IMAGE',
      }) })
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
    if (url.pathname === '/api/products') {
      await route.fulfill({ json: ok({ records: [], total: 0, size: 1000, current: 1, pages: 0 }) })
      return
    }
    if (url.pathname === '/api/customers') {
      await route.fulfill({ json: ok({ records: [], total: 0, size: 10, current: 1, pages: 0 }) })
      return
    }
    if (url.pathname === '/api/order-drafts/batches') {
      await route.fulfill({ json: ok([{
        sourceBatchNo: '33',
        draftCount: 1,
        latestUpdateTime: '2026-09-11T10:00:00',
      }]) })
      return
    }
    if (url.pathname === '/api/order-drafts/1' && route.request().method() === 'PUT') {
      savedDraftPayload = route.request().postDataJSON()
      draftImageIds = savedDraftPayload?.sourceFileIds || []
      await route.fulfill({ json: ok(null) })
      return
    }
    if (url.pathname === '/api/order-drafts/1' && route.request().method() === 'GET') {
      await route.fulfill({ json: ok({
        id: 1,
        externalRefNo: 'paper-batch-33-0003854',
        sourceBatchNo: '33',
        sourceOrderNo: '0003854',
        sourceFileId: draftImageIds[0],
        sourceFileIds: draftImageIds,
        rawCustomerName: 'K. Sankoh',
        customerName: 'K. Sankoh',
        rawOrderDate: '2025/11/22',
        orderDate: '2025-11-22',
        deposit: 0,
        paperTotalAmount: 5100,
        calculatedTotalAmount: 5100,
        warnings: [],
        status: 'EDITING',
        createTime: '2026-09-11T10:00:00',
        updateTime: '2026-09-11T10:00:00',
        items: [{
          id: 1,
          sourceRowNo: 1,
          rawProductCode: '618-3#',
          rawDescription: '无品名/颜色原文',
          rawQuantity: '120',
          rawSalePrice: '42.50',
          rawAmount: '5100',
          quantity: 120,
          salePrice: 42.5,
          paperAmount: 5100,
          matchStatus: 'UNMATCHED',
          warnings: [],
        }],
      }) })
      return
    }
    if (url.pathname === '/api/order-drafts') {
      await route.fulfill({ json: ok({
        records: [{
          id: 1,
          externalRefNo: 'paper-batch-33-0003854',
          sourceBatchNo: '33',
          sourceOrderNo: '0003854',
          sourceFileId: draftImageIds[0],
          sourceFileCount: draftImageIds.length,
          customerName: 'K. Sankoh',
          paperTotalAmount: 5100,
          status: 'EDITING',
          itemCount: 1,
          unresolvedCount: 1,
          warningCount: 0,
          updateTime: '2026-09-11T10:00:00',
        }],
        total: 1,
        size: 100,
        current: 1,
        pages: 1,
      }) })
      return
    }
    await route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })
})

test('宽屏和中屏都持续并排显示多张纸单且不影响编辑', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 1080 })
  await page.goto('/orders/drafts/1')

  await expect(page.getByRole('heading', { name: '草稿订单详情' })).toBeVisible()
  await expect(page.getByText('第 33 单（1 张）').first()).toBeVisible()
  const aside = page.getByRole('complementary', { name: '纸单原图对照栏' })
  await expect(aside).toBeVisible()
  await expect(aside.getByText('1 / 2')).toBeVisible()
  await expect(aside.locator('img').first()).toHaveAttribute('src', /\/api\/files\/9001\/preview/)

  const customerName = page.locator('input[placeholder*="客户名称搜索"]')
  await expect(customerName).toBeEditable()
  await customerName.fill('测试修改客户')
  await expect(customerName).toHaveValue('测试修改客户')

  await aside.getByRole('button', { name: '下一张纸单' }).click()
  await expect(aside.getByText('2 / 2')).toBeVisible()
  await expect(aside.locator('img').first()).toHaveAttribute('src', /\/api\/files\/9002\/preview/)

  await page.setViewportSize({ width: 1280, height: 900 })
  await expect(aside).toBeVisible()
  await expect(aside.getByText('2 / 2')).toBeVisible()
  await expect(aside.locator('img').first()).toHaveAttribute('src', /\/api\/files\/9002\/preview/)
  await customerName.fill('中屏继续编辑')
  await expect(customerName).toHaveValue('中屏继续编辑')
  await expect(aside).toBeVisible()
  await expect(page.locator('.el-drawer')).toHaveCount(0)
  await page.evaluate(() => window.scrollTo(0, 0))
})

test('编辑中的草稿可添加、移除和重排图片后统一保存', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/orders/drafts/1')

  const aside = page.getByRole('complementary', { name: '纸单原图对照栏' })
  await expect(aside.getByText('1 / 2')).toBeVisible()

  await aside.getByRole('button', { name: '移除当前图片' }).click()
  await page.getByRole('button', { name: '移除图片' }).click()
  await expect(aside.getByText('1 / 1')).toBeVisible()

  await aside.locator('input[type="file"]').setInputFiles({
    name: 'new-paper.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from('paper-image'),
  })
  await expect(aside.getByText('2 / 2')).toBeVisible()
  await expect(aside.locator('img').first()).toHaveAttribute('src', /\/api\/files\/9003\/preview/)

  await aside.getByRole('button', { name: '设为首图' }).click()
  await page.getByRole('button', { name: '存为草稿' }).click()

  await expect(page.getByText('草稿已保存')).toBeVisible()
  expect(savedDraftPayload?.sourceFileId).toBe(9003)
  expect(savedDraftPayload?.sourceFileIds).toEqual([9003, 9002])
})
