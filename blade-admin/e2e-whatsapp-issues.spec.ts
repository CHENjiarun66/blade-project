import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test('缺失媒体按聊天聚合，并在详情抽屉展示该客户全部明细', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e2e-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:whatsapp', 'btn:whatsapp:rescan']))
  })
  await page.route('**/api/auth/codes', route => route.fulfill({ json: ['menu:whatsapp', 'btn:whatsapp:rescan'] }))
  await page.route('**/api/whatsapp/issues/summary', route => route.fulfill({ json: ok({ open: 5, resolved: 0, missingPath: 5, missingFile: 0, image: 3, video: 1, audio: 1 }) }))
  await page.route('**/api/whatsapp/accounts', route => route.fulfill({ json: ok([{ id: 1, displayName: 'Mac WhatsApp Business', accountRef: 'mac:primary', status: 1 }]) }))
  await page.route('**/api/whatsapp/scan-jobs/latest', route => route.fulfill({ json: ok(null) }))
  await page.route('**/api/whatsapp/bindings/pending', route => route.fulfill({ json: ok([]) }))
  await page.route('**/api/whatsapp/insights?*', route => route.fulfill({ json: ok({ records: [], total: 0, size: 20, current: 1, pages: 0 }) }))
  await page.route('**/api/whatsapp/issues/chats?*', route => route.fulfill({ json: ok({
    records: [
      { accountId: 1, conversationId: 10, conversationTitle: 'Shakirah 拉友', conversationJid: '126817868456165@lid', phoneNormalized: '2349164306062', issueCount: 3, imageCount: 2, videoCount: 1, audioCount: 0, openCount: 3, resolvedCount: 0, latestMessageTime: '2026-08-03T09:09:11', lastDetectedAt: '2026-08-25T04:11:17' },
      { accountId: 1, conversationId: 11, conversationTitle: 'PASCAL BZ', conversationJid: '89700001156@s.whatsapp.net', phoneNormalized: '89700001156', issueCount: 2, imageCount: 1, videoCount: 0, audioCount: 1, openCount: 2, resolvedCount: 0, latestMessageTime: '2026-07-31T10:16:41', lastDetectedAt: '2026-08-25T04:11:17' },
    ], total: 2, size: 20, current: 1, pages: 1,
  }) }))
  await page.route('**/api/whatsapp/issues?*', route => route.fulfill({ json: ok({
    records: [1, 2, 3].map(id => ({ id, accountId: 1, conversationId: 10, conversationTitle: 'Shakirah 拉友', conversationJid: '25600003159@s.whatsapp.net', messageId: id, messageTime: `2026-08-03T09:0${id}:11`, issueType: 'MEDIA_PATH_EMPTY', status: 'OPEN', severity: 'WARNING', mediaType: id === 3 ? 'VIDEO' : 'IMAGE', occurrenceCount: 1, firstDetectedAt: '2026-08-25T04:11:17', lastDetectedAt: '2026-08-25T04:11:17' })),
    total: 3, size: 20, current: 1, pages: 1,
  }) }))

  await page.goto('/whatsapp')
  await page.getByRole('tab', { name: '缺失媒体' }).click()

  await expect(page.getByText('Shakirah 拉友', { exact: true })).toHaveCount(1)
  await expect(page.getByText('PASCAL BZ', { exact: true })).toHaveCount(1)
  await expect(page.getByText('234****6062', { exact: true })).toBeVisible()
  await expect(page.getByText('126****6165', { exact: true })).toHaveCount(0)
  await expect(page.getByText('图片 2', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看详情' })).toHaveCount(2)

  await page.getByRole('button', { name: '查看详情' }).first().click()
  const drawer = page.locator('.el-drawer')
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('共 3 项', { exact: false })).toBeVisible()
  await expect(drawer.locator('.el-table__body tbody tr')).toHaveCount(3)
  await expect(drawer.getByRole('button', { name: '打开这个聊天' })).toBeVisible()
})
