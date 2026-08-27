import { expect, test } from '@playwright/test'
import path from 'path'

test('Agent草稿使用快速录单布局编辑并按需查看原图', async ({ page }) => {
  await page.goto('/login')
  const captcha = (await page.locator('.captcha-text').textContent())?.trim() || ''
  await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant')
  await page.fill('input[placeholder="您的管理员账号"]', 'admin')
  await page.fill('input[placeholder="••••••••"]', 'admin123')
  await page.fill('input[placeholder="输入验证码"]', captcha)
  await page.click('button[type="submit"]')
  await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 })

  await page.goto('/orders/drafts')
  await expect(page.getByRole('heading', { name: '订单草稿录入' })).toBeVisible()
  await expect(page.getByText('单据信息')).toBeVisible()
  await expect(page.getByText('客户信息')).toBeVisible()
  await expect(page.getByText('商品明细')).toBeVisible()
  await expect(page.getByText('定金待确认').first()).toBeVisible()
  await expect(page.getByText('纸单总额与计算金额不一致').first()).toBeVisible()
  await expect(page.getByText('销售单价')).toBeVisible()
  await expect(page.getByText(/系统参考/).first()).toBeVisible()

  await page.screenshot({
    path: path.resolve('../outputs/order-draft-workbench.png'),
    fullPage: true,
  })

  const customerName = page.locator('input[placeholder*="客户名称搜索"]')
  await expect(customerName).toBeEditable()
  await customerName.fill('测试修改客户')
  await expect(customerName).toHaveValue('测试修改客户')

  await page.getByRole('button', { name: '查看纸单原图' }).click()
  const sourceImage = page.locator('.el-drawer').locator('img')
  await expect(sourceImage).toBeVisible()
  await expect(sourceImage).toHaveAttribute('src', /\/api\/files\/1942\/preview/)

})
