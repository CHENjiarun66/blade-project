import { expect, test, Page } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

const PERMISSIONS = ['menu:file', 'btn:file:viewOwn', 'btn:file:viewAll']

const INFO = { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }

function init(page: Page) {
  return page.addInitScript((permissions: string[]) => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }))
    localStorage.setItem('permissions', JSON.stringify(permissions))
  }, PERMISSIONS)
}

test.describe('Series E2 文件中心档口范围', () => {
  test('order_draft 筛选参数按后端契约发送，403 显示友好提示', async ({ page }) => {
    await init(page)
    let lastParams: Record<string, string> = {}
    let deny = false

    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      if (url.pathname === '/api/auth/codes') return route.fulfill({ json: PERMISSIONS })
      if (url.pathname === '/api/user/info') return route.fulfill({ json: INFO })
      if (url.pathname === '/api/file-folders/tree') return route.fulfill({ json: ok([]) })
      if (url.pathname === '/api/files') {
        lastParams = Object.fromEntries(url.searchParams.entries())
        if (deny) {
          return route.fulfill({ json: { code: 403, message: '无权访问该文件' } })
        }
        return route.fulfill({ json: ok({ records: [], total: 0, size: 20, current: 1, pages: 0 }) })
      }
      return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
    })

    await page.goto('/files')
    await page.waitForTimeout(500)

    // 业务类型筛选包含“订单草稿”，并发送 businessType=order_draft
    await page.locator('.file-select').nth(1).click()
    await page.getByRole('option', { name: '订单草稿' }).click()
    await expect.poll(() => lastParams.businessType).toBe('order_draft')

    // 后端 403 时前端只做提示，不过滤、不隐藏
    deny = true
    await page.locator('.file-select').nth(1).click()
    await page.getByRole('option', { name: '订单', exact: true }).click()
    await expect(page.getByText('无权查看该范围的文件，仅显示你有权限的档口数据')).toBeVisible()
  })
})
