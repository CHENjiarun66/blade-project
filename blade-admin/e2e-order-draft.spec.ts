import { expect, test } from '@playwright/test'
import path from 'path'

test('Agent草稿工作台展示纸单原图、识别警告和纸单价格', async ({ page }) => {
  await page.goto('/login')
  const captcha = (await page.locator('.captcha-text').textContent())?.trim() || ''
  await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant')
  await page.fill('input[placeholder="您的管理员账号"]', 'admin')
  await page.fill('input[placeholder="••••••••"]', 'admin123')
  await page.fill('input[placeholder="输入验证码"]', captcha)
  await page.click('button[type="submit"]')
  await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 })

  await page.goto('/orders/drafts')
  await expect(page.getByRole('heading', { name: '订单草稿工作台' })).toBeVisible()
  await expect(page.getByText('0000115', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('定金待确认').first()).toBeVisible()
  await expect(page.getByText('总金额不一致').first()).toBeVisible()
  await expect(page.getByText('纸单销售价')).toBeVisible()
  await expect(page.getByText('系统参考价')).toBeVisible()

  const sourceImage = page.locator('section').filter({ hasText: '纸单原图' }).locator('img')
  await expect(sourceImage).toBeVisible()
  await expect(sourceImage).toHaveAttribute('src', /\/api\/files\/1942\/preview/)

  await page.screenshot({
    path: path.resolve('../outputs/order-draft-workbench.png'),
    fullPage: true,
  })
})
