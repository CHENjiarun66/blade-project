import { test, expect, Page } from '@playwright/test'

const BASE_URL = 'http://localhost:5777'

async function login(page: Page) {
  await page.goto(`${BASE_URL}/login`)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(1500)
  const captcha = (await page.locator('.captcha-text').textContent())?.trim() || ''
  await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant')
  await page.fill('input[placeholder="您的管理员账号"]', 'admin')
  await page.fill('input[placeholder="••••••••"]', 'admin123')
  await page.fill('input[placeholder="输入验证码"]', captcha)
  await page.click('button[type="submit"]')
  await page.waitForTimeout(2500)
  expect(page.url()).not.toContain('/login')
}

test.describe('Series B2 档口管理 + 用户档口授权', () => {
  test('BA-OUTLET-001 路由/列表/新建/编辑只读/启停确认', async ({ page }) => {
    await login(page)

    // 菜单入口可见（menu:outlet）
    await expect(page.locator('a', { hasText: '档口管理' }).first()).toBeVisible()

    await page.goto(`${BASE_URL}/outlets`)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.el-table')).toBeVisible()
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-01-list.png', fullPage: true })

    // 新建档口
    const code = 'E2E' + Date.now().toString().slice(-6)
    await page.click('button:has-text("新建档口")')
    await page.fill('.el-dialog input[placeholder="稳定编码，如 YL"]', code)
    await page.fill('.el-dialog input[placeholder="档口名称"]', 'E2E 测试档口')
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-02-create.png', fullPage: true })
    await page.click('.el-dialog button:has-text("确定")')
    await page.waitForTimeout(1200)
    await expect(page.locator('.el-table')).toContainText(code)

    // 编辑：编码只读 + 提示
    const row = page.locator('tr', { hasText: code }).first()
    await row.locator('button:has-text("编辑")').click()
    await page.waitForTimeout(400)
    await expect(page.locator('.el-dialog input[placeholder="稳定编码，如 YL"]')).toBeDisabled()
    await expect(page.locator('.el-dialog')).toContainText('档口编码创建后不可修改')
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-03-edit-readonly.png', fullPage: true })
    await page.click('.el-dialog button:has-text("取消")')

    // 启停二次确认（显示引用数）
    await row.locator('button:has-text("禁用")').click()
    await page.waitForTimeout(400)
    await expect(page.locator('.el-message-box')).toContainText('历史数据仍保留')
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-04-disable-confirm.png', fullPage: true })
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await page.waitForTimeout(1000)
    await expect(row).toContainText('禁用')
  })

  test('BA-OUTLET-002 用户多选+默认、销售员空档口阻止、状态切换 payload 不含 outletIds', async ({ page }) => {
    await login(page)
    await page.goto(`${BASE_URL}/system`)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1200)

    const username = 'e2eoutlet' + Date.now().toString().slice(-6)

    // 新建用户：选 ROLE_SALES，不选档口 -> 阻止保存
    await page.click('button:has-text("新建用户")')
    await page.fill('.el-dialog input[placeholder="请输入用户名"]', username)
    await page.fill('.el-dialog input[placeholder="请输入密码"]', 'e2e12345')

    const roleSelect = page.locator('.el-dialog .el-form-item', { hasText: '角色' }).locator('.el-select').first()
    await roleSelect.click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: 'ROLE_SALES' }).first().click()
    await page.keyboard.press('Escape')

    await page.click('.el-dialog button:has-text("确定")')
    await page.waitForTimeout(600)
    await expect(page.locator('.el-message')).toContainText('销售员至少绑定一个档口')
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-05-sales-block.png', fullPage: true })

    // 正向：ROLE_SALES + 授予 data:outlet:all 的角色（服务端 grantsOutletAll）不应阻止
    await roleSelect.click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: 'ROLE_OWNER' }).first().click()
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
    await expect(page.locator('.el-dialog')).not.toContainText('销售员至少绑定一个档口')

    // 取消 ROLE_OWNER，回到负向场景
    await roleSelect.click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: 'ROLE_OWNER' }).first().click()
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
    await expect(page.locator('.el-dialog')).toContainText('销售员至少绑定一个档口')

    // 选择档口 + 默认档口 -> 保存成功
    const outletSelect = page.locator('.el-dialog .el-form-item', { hasText: '可访问档口' }).locator('.el-select').first()
    await outletSelect.click()
    await page.locator('.el-select-dropdown__item:visible').first().click()
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)

    const defaultSelect = page.locator('.el-dialog .el-form-item', { hasText: '默认档口' }).locator('.el-select').first()
    await defaultSelect.click()
    await page.locator('.el-select-dropdown__item:visible').first().click()
    await page.keyboard.press('Escape')
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-06-user-binding.png', fullPage: true })

    await page.click('.el-dialog button:has-text("确定")')
    await page.waitForTimeout(1500)
    await expect(page.locator('.el-table:visible').first()).toContainText(username)

    // 状态切换：拦截 PUT /api/system/users，断言 payload 不含 outletIds/defaultOutletId
    let statusPayload: any = null
    page.on('request', (req) => {
      if (req.method() === 'PUT'
        && req.url().includes('/api/system/users')
        && !req.url().includes('/password')) {
        try { statusPayload = JSON.parse(req.postData() || '{}') } catch { statusPayload = {} }
      }
    })
    const userRow = page.locator('tr', { hasText: username }).first()
    await userRow.locator('button:has-text("禁用")').click()
    await page.waitForTimeout(400)
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await page.waitForTimeout(1200)

    expect(statusPayload, '状态切换应发出 PUT').not.toBeNull()
    expect(statusPayload.status).toBe(0)
    expect(statusPayload.outletIds).toBeUndefined()
    expect(statusPayload.defaultOutletId).toBeUndefined()
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-07-status-payload.png', fullPage: true })
  })

  test('BA-OUTLET-001 筛选栏紧凑与搜索重置分页', async ({ page }) => {
    await login(page)

    // 模拟列表接口：total=100 保证多页；记录每次请求的 current
    const currents: number[] = []
    await page.route('**/api/outlets?**', async (route) => {
      const url = new URL(route.request().url())
      const current = Number(url.searchParams.get('current') || '1')
      const size = Number(url.searchParams.get('size') || '20')
      currents.push(current)
      const records = Array.from({ length: size }, (_, i) => ({
        id: 900000 + current * 1000 + i,
        outletCode: 'MOCK' + current + i,
        outletName: '模拟档口' + current + i,
        outletType: 'STORE',
        isTenantDefault: 0,
        status: 1,
        boundUserCount: 0,
        orderCount: 0,
        draftCount: 0,
      }))
      await route.fulfill({
        json: { code: 200, message: 'ok', data: { records, total: 100, size, current, pages: Math.ceil(100 / size) } },
      })
    })

    await page.goto(`${BASE_URL}/outlets`)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(600)

    // 紧凑筛选栏：搜索/状态/搜索按钮/新建在同一行，输入框非全宽
    const toolbar = page.locator('.outlet-toolbar')
    await expect(toolbar).toBeVisible()
    const boxes = await Promise.all([
      page.locator('.outlet-toolbar > .el-input').first().boundingBox(),
      page.locator('.outlet-toolbar > .el-select').first().boundingBox(),
      page.locator('.outlet-toolbar button:has-text("搜索")').boundingBox(),
      page.locator('.outlet-toolbar button:has-text("新建档口")').boundingBox(),
    ])
    const valid = boxes.filter((b): b is NonNullable<typeof b> => !!b)
    expect(valid.length).toBe(4)
    const ys = valid.map((b) => b.y)
    expect(Math.max(...ys) - Math.min(...ys), '筛选控件应在同一行').toBeLessThan(8)
    expect(valid[0].width, '搜索输入不应全宽').toBeLessThan(400)
    await page.screenshot({ path: 'test-screenshots/e2e-outlet-08-compact-filter.png', fullPage: true })

    // 翻到第 2 页
    await page.locator('.el-pagination button.btn-next').click()
    await page.waitForTimeout(800)
    expect(currents[currents.length - 1], '翻页应请求 current=2').toBe(2)

    // 点击搜索：必须重置回第 1 页
    await page.locator('.outlet-toolbar button:has-text("搜索")').click()
    await page.waitForTimeout(800)
    expect(currents[currents.length - 1], '搜索应重置 current=1').toBe(1)
  })
})
