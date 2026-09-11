import { expect, test } from '@playwright/test'
import path from 'path'

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
    if (/\/api\/files\/(9001|9002)\/preview/.test(url.pathname)) {
      const pageNo = url.pathname.includes('9002') ? '2' : '1'
      await route.fulfill({
        status: 200,
        contentType: 'image/svg+xml',
        body: `<svg xmlns="http://www.w3.org/2000/svg" width="900" height="1300"><rect width="100%" height="100%" fill="#fffdf7"/><text x="70" y="120" font-size="52" fill="#18212f">纸单原图 ${pageNo}</text><path d="M70 190h760M70 290h760M70 390h760M70 490h760M70 590h760M70 690h760M70 790h760" stroke="#94a3b8" stroke-width="3"/><text x="70" y="920" font-size="38" fill="#334155">款号 618-3#　数量 60　单价 42.50</text></svg>`,
      })
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
    if (url.pathname === '/api/order-drafts/1') {
      await route.fulfill({ json: ok({
        id: 1,
        externalRefNo: 'paper-batch-33-0003854',
        sourceBatchNo: '33',
        sourceOrderNo: '0003854',
        sourceFileId: 9001,
        sourceFileIds: [9001, 9002],
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
          sourceOrderNo: '0003854',
          sourceFileId: 9001,
          sourceFileCount: 2,
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

test('宽屏并排显示多张纸单，窄屏改用抽屉且不影响编辑', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 1080 })
  await page.goto('/orders/drafts')

  await expect(page.getByRole('heading', { name: '订单草稿录入' })).toBeVisible()
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

  await page.screenshot({ path: path.resolve('../outputs/order-draft-paper-side-by-side.png') })

  await page.setViewportSize({ width: 1280, height: 900 })
  await expect(aside).toBeHidden()
  await page.getByRole('button', { name: /查看原单/ }).click()
  await expect(page.locator('.el-drawer')).toBeVisible()
  await expect(page.locator('.el-drawer img')).toHaveAttribute('src', /\/api\/files\/9002\/preview/)
})
