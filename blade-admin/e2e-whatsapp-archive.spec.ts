import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })
const emptyPage = { records: [], total: 0, size: 20, current: 1, pages: 0 }

test('聊天记录以只读 WhatsApp 视图展示文字与媒体完整性', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e2e-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:whatsapp']))
  })
  await page.route('**/api/auth/codes', route => route.fulfill({ json: ['menu:whatsapp'] }))
  await page.route('**/api/whatsapp/accounts', route => route.fulfill({ json: ok([{ id: 1, displayName: 'Mac WhatsApp Business', accountRef: 'mac:primary', status: 1 }]) }))
  await page.route('**/api/whatsapp/issues/summary', route => route.fulfill({ json: ok({ open: 1, resolved: 0, missingPath: 1, missingFile: 0, image: 0, video: 1, audio: 0 }) }))
  await page.route('**/api/whatsapp/scan-jobs/latest', route => route.fulfill({ json: ok(null) }))
  await page.route('**/api/whatsapp/bindings/pending', route => route.fulfill({ json: ok([]) }))
  await page.route('**/api/whatsapp/bindings/refresh', route => route.fulfill({ json: ok([]) }))
  await page.route('**/api/whatsapp/insights?*', route => route.fulfill({ json: ok(emptyPage) }))
  await page.route('**/api/whatsapp/issues/chats?*', route => route.fulfill({ json: ok(emptyPage) }))
  await page.route('**/api/whatsapp/archive/chats?*', route => route.fulfill({ json: ok({
    records: [{ accountId: 1, identityKey: '2348033912244', displayName: 'Pastor（尼日利亚）', phoneNormalized: '2348033912244', messageCount: 4, lastMessageAt: '2026-08-14T07:31:32', lastDirection: 'OUTBOUND', lastMessageType: 'VIDEO' }],
    total: 1, size: 30, current: 1, pages: 1,
  }) }))
  await page.route('**/api/whatsapp/archive/messages?*', route => route.fulfill({ json: ok({
    records: [
      { id: 1, sentAt: '2026-08-14T07:20:00', direction: 'INBOUND', messageType: 'TEXT', textContent: '请问这个产品还有库存吗？', starred: false, media: [] },
      { id: 2, sentAt: '2026-08-14T07:21:00', direction: 'OUTBOUND', messageType: 'IMAGE', textContent: '这是实拍图', starred: false, media: [{ id: 20, fileId: 1354, mediaType: 'IMAGE', mimeType: 'image/jpeg', downloadStatus: 'IMPORTED' }] },
      { id: 3, sentAt: '2026-08-14T07:22:00', direction: 'INBOUND', messageType: 'AUDIO', starred: false, media: [{ id: 30, fileId: 1355, mediaType: 'AUDIO', mimeType: 'audio/ogg', downloadStatus: 'IMPORTED' }] },
      { id: 4, sentAt: '2026-08-14T07:23:00', direction: 'OUTBOUND', messageType: 'VIDEO', starred: false, media: [{ id: 40, mediaType: 'VIDEO', downloadStatus: 'METADATA_ONLY', issueType: 'MEDIA_PATH_EMPTY' }] },
    ], total: 4, size: 50, current: 1, pages: 1,
  }) }))
  await page.route('**/api/files/*/preview?*', route => route.fulfill({ status: 200, contentType: 'application/octet-stream', body: '' }))

  await page.goto('/whatsapp')
  await page.getByRole('tab', { name: '聊天记录' }).click()

  await expect(page.getByText('Pastor（尼日利亚）').first()).toBeVisible()
  await expect(page.getByText('请问这个产品还有库存吗？')).toBeVisible()
  await expect(page.getByText('这是实拍图')).toBeVisible()
  await expect(page.locator('.message-image')).toHaveCount(1)
  await expect(page.locator('audio.message-audio')).toHaveCount(1)
  await expect(page.getByText('视频尚未完整归档')).toBeVisible()
  await expect(page.getByText('Mac 尚未获得媒体路径')).toBeVisible()
  await expect(page.getByText('只读归档，不会向客户发送任何内容')).toBeVisible()
  await expect(page.getByRole('button', { name: /发送/ })).toHaveCount(0)
})
