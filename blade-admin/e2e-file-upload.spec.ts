import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const dirname = path.dirname(fileURLToPath(import.meta.url))

async function loginAdmin(page: Page, request: APIRequestContext) {
  const loginResponse = await request.post('http://127.0.0.1:8080/api/auth/login', {
    data: {
      tenantCode: 'super_admin',
      username: 'admin',
      password: 'admin123',
      remember: true,
    },
  })
  expect(loginResponse.ok()).toBeTruthy()
  const auth = await loginResponse.json()

  await page.addInitScript(({ token, refreshToken }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('refreshToken', refreshToken)
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '1',
      username: 'admin',
      realName: '管理员',
    }))
    localStorage.setItem('permissions', JSON.stringify([
      'menu:dashboard',
      'menu:analytics',
      'menu:order',
      'menu:inventory',
      'menu:product',
      'menu:customer',
      'menu:system',
    ]))
  }, {
    token: auth.accessToken || auth.token,
    refreshToken: auth.refreshToken,
  })

  return auth
}

test('product image upload returns fileId through unified file API', async ({ page, request }) => {
  const auth = await loginAdmin(page, request)

  await page.goto('/products')
  await page.getByRole('button', { name: '新建商品' }).click()

  const uploadResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/files/upload')
    && response.request().method() === 'POST'
  )

  await page.locator('input[type="file"]').first().setInputFiles(
    path.resolve(dirname, 'src/views/customers/test-screenshots/c01-login-filled.png')
  )

  const uploadResponse = await uploadResponsePromise
  expect(uploadResponse.status()).toBe(200)
  const uploadBody = await uploadResponse.json()
  expect(uploadBody.code).toBe(200)
  expect(uploadBody.data.id).toEqual(expect.any(Number))
  expect(uploadBody.data.url).toContain(`/api/files/${uploadBody.data.id}/preview`)

  const preview = page.locator('img[src*="/api/files/"]').first()
  await expect(preview).toBeVisible()
  await expect(preview).toHaveAttribute('src', uploadBody.data.url)
  await expect.poll(() => preview.evaluate((image) => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)

  if (process.env.BLADE_UPLOAD_SCREENSHOT_PATH) {
    await page.screenshot({ path: process.env.BLADE_UPLOAD_SCREENSHOT_PATH })
  }

  await request.delete(`http://127.0.0.1:8080/api/files/${uploadBody.data.id}`, {
    headers: {
      Authorization: `Bearer ${auth.accessToken || auth.token}`,
    },
  })

  await expect(page.getByText('系统错误')).toHaveCount(0)
  await expect(page.getByText('服务器内部错误')).toHaveCount(0)
})

test('order edit image upload shows preview in order dialog', async ({ page, request }) => {
  const auth = await loginAdmin(page, request)

  await page.goto('/orders')
  await page.getByRole('button', { name: '编辑' }).first().click()
  await expect(page.getByRole('dialog', { name: '编辑订单' })).toBeVisible()

  const uploadResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/files/upload')
    && response.request().method() === 'POST'
  )

  await page.locator('input[type="file"]').setInputFiles(
    path.resolve(dirname, 'src/views/customers/test-screenshots/c01-login-filled.png')
  )

  const uploadResponse = await uploadResponsePromise
  expect(uploadResponse.status()).toBe(200)
  const uploadBody = await uploadResponse.json()
  expect(uploadBody.code).toBe(200)

  const preview = page.locator('img[src*="/api/files/"]').last()
  await expect(preview).toBeVisible()
  await expect(preview).toHaveAttribute('src', uploadBody.data.url)
  await expect.poll(() => preview.evaluate((image) => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)

  if (process.env.BLADE_ORDER_EDIT_SCREENSHOT_PATH) {
    await page.getByText('订单图片').scrollIntoViewIfNeeded()
    await page.screenshot({ path: process.env.BLADE_ORDER_EDIT_SCREENSHOT_PATH })
  }

  await request.delete(`http://127.0.0.1:8080/api/files/${uploadBody.data.id}`, {
    headers: {
      Authorization: `Bearer ${auth.accessToken || auth.token}`,
    },
  })
})

test('quick order image upload shows preview before saving', async ({ page, request }) => {
  const auth = await loginAdmin(page, request)

  await page.goto('/orders/quick')
  await expect(page.getByRole('heading', { name: '快速录单' })).toBeVisible()

  const uploadResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/files/upload')
    && response.request().method() === 'POST'
  )

  await page.locator('input[type="file"]').setInputFiles(
    path.resolve(dirname, 'src/views/customers/test-screenshots/c01-login-filled.png')
  )

  const uploadResponse = await uploadResponsePromise
  expect(uploadResponse.status()).toBe(200)
  const uploadBody = await uploadResponse.json()
  expect(uploadBody.code).toBe(200)

  const preview = page.locator('img[src*="/api/files/"]').last()
  await expect(preview).toBeVisible()
  await expect(preview).toHaveAttribute('src', uploadBody.data.url)
  await expect.poll(() => preview.evaluate((image) => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)

  if (process.env.BLADE_QUICK_ORDER_SCREENSHOT_PATH) {
    await page.getByText('订单图片').scrollIntoViewIfNeeded()
    await page.screenshot({ path: process.env.BLADE_QUICK_ORDER_SCREENSHOT_PATH })
  }

  await request.delete(`http://127.0.0.1:8080/api/files/${uploadBody.data.id}`, {
    headers: {
      Authorization: `Bearer ${auth.accessToken || auth.token}`,
    },
  })

  await expect(page.getByText('系统错误')).toHaveCount(0)
  await expect(page.getByText('服务器内部错误')).toHaveCount(0)
})
