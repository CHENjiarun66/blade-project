import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test('旧 Agent 草稿首次打开即把精确 SPU 款号回填为整款录入 SKU', async ({ page }) => {
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
    if (url.pathname === '/api/products') {
      await route.fulfill({ json: ok({ records: [{
        id: 61816,
        productCode: '618-16#',
        name: '测试商品',
        status: 1,
        wholesalePrice: 43,
        costPrice: 29,
        skus: [{
          id: 6181600,
          skuCode: '618-16#-UNSPECIFIED-UNSPEC',
          skuType: 'PLACEHOLDER',
          placeholder: true,
          colorName: '未指定颜色',
          sizeName: 'UNSPEC',
          price: 43,
          costPrice: 29,
          status: 1,
        }],
      }], total: 1, size: 1000, current: 1, pages: 1 }) })
      return
    }
    if (url.pathname === '/api/order-drafts/batches') {
      await route.fulfill({ json: ok([{ sourceBatchNo: '33', draftCount: 1 }]) })
      return
    }
    if (url.pathname === '/api/order-drafts/2') {
      await route.fulfill({ json: ok({
        id: 2,
        externalRefNo: 'paper-batch-33-0000494',
        entrySource: 'AGENT',
        sourceBatchNo: '33',
        sourceOrderNo: '0000494',
        sourceOutletId: 1,
        sourceShop: '御龙',
        customerName: '散客',
        warnings: ['ITEM_1_SKU_UNMATCHED'],
        status: 'EDITING',
        sourceFileIds: [],
        items: [{
          id: 21,
          sourceRowNo: 1,
          rawProductCode: '618-16',
          rawDescription: '无品名',
          rawColor: '',
          quantity: 60,
          salePrice: 42.5,
          paperAmount: 2550,
          matchStatus: 'UNMATCHED',
          warnings: [],
        }],
      }) })
      return
    }
    if (url.pathname === '/api/order-drafts') {
      await route.fulfill({ json: ok({ records: [{
        id: 2,
        externalRefNo: 'paper-batch-33-0000494',
        sourceBatchNo: '33',
        sourceOrderNo: '0000494',
        customerName: '散客',
        status: 'EDITING',
        itemCount: 1,
        unresolvedCount: 1,
        warningCount: 1,
      }], total: 1, size: 100, current: 1, pages: 1 }) })
      return
    }
    if (url.pathname === '/api/outlets/options') {
      await route.fulfill({ json: ok({
        scopeType: 'ALL',
        locked: false,
        defaultOutletId: 1,
        items: [{ id: 1, outletCode: 'YL', outletName: '御龙', status: 1 }],
      }) })
      return
    }
    await route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })

  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/orders/drafts/2')

  await expect(page.getByRole('heading', { name: '草稿订单详情' })).toBeVisible()
  await expect(page.getByText('618-16# / 测试商品 · 整款录入（颜色/尺码未指定）', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('已匹配', { exact: true })).toBeVisible()
  await expect(page.getByText('待匹配', { exact: true })).toHaveCount(0)
  await expect(page.getByText('已按精确款号自动匹配 1 行商品；保存草稿后生效')).toBeVisible()
})
