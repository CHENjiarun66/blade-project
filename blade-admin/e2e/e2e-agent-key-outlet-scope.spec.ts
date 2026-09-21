import { expect, test, Page } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

const PERMISSIONS = ['menu:system', 'agent-key:manage']

const ADMIN_INFO = { userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }

const SCOPES = [
  'catalog:read',
  'products:read',
  'orders:read',
  'customers:read',
  'orders:write',
  'products:create',
  'products:cost:write',
  'orders:cost:write',
  'customers:create',
  'analytics:read',
  'outlets:read',
  'whatsapp:analyze',
]

// 租户可配置档口：广州档已停用，可保留历史绑定但不能新选。
const OUTLET_OPTIONS = [
  { id: 1, outletCode: 'YL', outletName: '御龙', status: 1, tenantDefault: true },
  { id: 2, outletCode: 'HZ', outletName: '杭州档', status: 1, tenantDefault: false },
  { id: 3, outletCode: 'GZ', outletName: '广州档', status: 0, tenantDefault: false },
]

// 已存在的 ASSIGNED Key：绑定「御龙（在用，默认）」和「广州档（已停用）」。
const AGENT_KEYS = [
  {
    id: 11,
    name: 'Mac 纸单录入 Agent',
    keyPrefix: 'bk_live_abc',
    scopes: ['catalog:read', 'orders:write', 'outlets:read'],
    outletScopeType: 'ASSIGNED',
    defaultOutletId: 1,
    outlets: [
      { id: 1, outletCode: 'YL', outletName: '御龙', status: 1, isDefault: true },
      { id: 3, outletCode: 'GZ', outletName: '广州档', status: 0, isDefault: false },
    ],
    status: 1,
    expired: false,
    createTime: '2026-09-01 10:00:00',
  },
]

function init(page: Page) {
  return page.addInitScript((permissions: string[]) => {
    localStorage.setItem('token', 'mock-access-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_OWNER'] }))
    localStorage.setItem('permissions', JSON.stringify(permissions))
  }, PERMISSIONS)
}

async function setupRoutes(page: Page) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (url.pathname === '/api/auth/codes') return route.fulfill({ json: PERMISSIONS })
    if (url.pathname === '/api/user/info') return route.fulfill({ json: ADMIN_INFO })
    if (url.pathname === '/api/system/users') {
      return route.fulfill({ json: ok({ records: [], total: 0, size: 20, current: 1, pages: 0 }) })
    }
    if (url.pathname === '/api/system/roles/all') return route.fulfill({ json: ok([]) })
    if (url.pathname === '/api/outlets/options') {
      return route.fulfill({ json: ok({ scopeType: 'ALL', peopleScope: 'ALL_USERS', locked: false, defaultOutletId: 1, items: [] }) })
    }
    if (url.pathname === '/api/permissions/tree') return route.fulfill({ json: ok([]) })
    if (url.pathname === '/api/system/agent-keys') return route.fulfill({ json: ok(AGENT_KEYS) })
    if (url.pathname === '/api/system/agent-keys/scopes') return route.fulfill({ json: ok(SCOPES) })
    if (url.pathname === '/api/system/agent-keys/outlets') return route.fulfill({ json: ok(OUTLET_OPTIONS) })
    return route.fulfill({ status: 404, json: { code: 404, message: `Unmocked ${url.pathname}` } })
  })
}

async function openAgentKeyPanel(page: Page) {
  await page.goto('/system')
  await page.getByRole('tab', { name: 'Agent Key' }).click()
  await expect(page.getByRole('button', { name: /新建 Agent Key/ })).toBeVisible()
}

test.describe('Series E3 Agent Key 档口范围', () => {
  test('BA-E3-001 新建时切换档口范围联动选择器与告警', async ({ page }) => {
    await init(page)
    await setupRoutes(page)
    await openAgentKeyPanel(page)

    await page.getByRole('button', { name: /新建 Agent Key/ }).click()
    const dialog = page.locator('.el-dialog:visible').filter({ hasText: '新建 Agent Key' })
    await expect(dialog).toBeVisible()

    // 默认 NONE：不显示档口选择器，订单权限触发告警（不阻塞）
    await expect(dialog.getByTestId('agent-key-assigned-outlets')).toHaveCount(0)
    await expect(dialog.getByText('已选择订单/分析权限但档口范围为')).toBeVisible()

    // 切到 ASSIGNED：出现档口多选，告警消失
    await dialog.locator('.el-radio-button', { hasText: '指定档口' }).click()
    await expect(dialog.getByTestId('agent-key-assigned-outlets')).toBeVisible()
    await expect(dialog.getByText('已选择订单/分析权限但档口范围为')).toHaveCount(0)

    // 下拉中：在用档口可选，已停用档口不可新选
    await dialog.getByTestId('agent-key-assigned-outlets').click()
    const enabledOption = page.getByRole('option', { name: /YL 御龙/ })
    const disabledOption = page.getByRole('option', { name: /GZ 广州档/ })
    await expect(enabledOption).toBeVisible()
    await expect(disabledOption).toHaveClass(/is-disabled/)
    await enabledOption.click()
    await page.keyboard.press('Escape')

    // 只选一个在用档口时自动成为默认档口
    await expect(dialog.getByTestId('agent-key-assigned-default-outlet')).toContainText('御龙')

    // 切回 NONE：选择器消失，告警重新出现
    await dialog.locator('.el-radio-button', { hasText: '不开放档口数据' }).click()
    await expect(dialog.getByTestId('agent-key-assigned-outlets')).toHaveCount(0)
    await expect(dialog.getByText('已选择订单/分析权限但档口范围为')).toBeVisible()
  })

  test('BA-E3-002 调整权限弹窗预载 Key 的档口范围', async ({ page }) => {
    await init(page)
    await setupRoutes(page)
    await openAgentKeyPanel(page)

    await page.getByRole('button', { name: '调整权限' }).click()
    const dialog = page.locator('.el-dialog:visible').filter({ hasText: '调整 Agent 权限' })
    await expect(dialog).toBeVisible()

    // outletScopeType=ASSIGNED 预载为选中项
    await expect(dialog.locator('.el-radio-button.is-active')).toContainText('指定档口')
    // outletIds=[1,3] 预载为已选标签（含已停用的历史绑定）
    await expect(dialog.getByTestId('agent-key-assigned-outlets')).toContainText('御龙')
    await expect(dialog.getByTestId('agent-key-assigned-outlets')).toContainText('广州档')
    // defaultOutletId=1 预载为默认档口
    await expect(dialog.getByTestId('agent-key-assigned-default-outlet')).toContainText('御龙')
    // ASSIGNED 不显示“范围不匹配”告警
    await expect(dialog.getByText('已选择订单/分析权限但档口范围为')).toHaveCount(0)
  })
})
