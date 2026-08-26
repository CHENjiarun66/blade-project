import { expect, test } from '@playwright/test'

const ok = (data: unknown) => ({ code: 200, message: 'success', data })

test('客户详情直接展示已绑定的 WhatsApp 聊天与缺失媒体', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e2e-token')
    localStorage.setItem('userInfo', JSON.stringify({ userId: '1', username: 'admin', realName: '管理员', roles: ['ROLE_ADMIN'] }))
    localStorage.setItem('permissions', JSON.stringify(['menu:customer', 'menu:whatsapp']))
  })
  await page.route('**/api/auth/codes', route => route.fulfill({ json: ['menu:customer', 'menu:whatsapp'] }))
  await page.route('**/api/customers/32', route => route.fulfill({ json: ok({
    id: 32, name: 'Sbk(刚果金) Fashion+243', countryCode: '+243', countryName: '刚果（金）',
    phones: ['835453734'], address: '', remark: '', createTime: '2026-08-20T10:00:00',
  }) }))
  await page.route('**/api/whatsapp/bindings/refresh', route => route.fulfill({ json: ok([]) }))
  await page.route('**/api/whatsapp/customers/32/workspace', route => route.fulfill({ json: ok([{
    customerId: 32, bindingId: 91, bindingStatus: 'CONFIRMED', contactId: 45,
    contactName: 'Sbk(刚果金) Fashion+243', phoneNormalized: '243835453734',
    accountId: 1, accountName: 'Mac WhatsApp Business', lastSyncTime: '2026-08-26T10:00:00',
    identityKey: '243835453734', conversationJid: '243835453734@s.whatsapp.net',
    messageCount: 52, lastMessageAt: '2026-08-25T18:30:00', openIssueCount: 2,
    imageIssueCount: 0, videoIssueCount: 2, audioIssueCount: 0, confirmedAt: '2026-08-25T09:00:00',
  }]) }))
  await page.route('**/api/whatsapp/archive/messages?*', route => {
    const requestedPage = new URL(route.request().url()).searchParams.get('page')
    const records = requestedPage === '2' ? [
      { id: 1, sentAt: '2026-08-24T10:00:00', direction: 'INBOUND', messageType: 'TEXT', textContent: '这是向上加载的更早消息', starred: false, media: [] },
    ] : [
      { id: 2, sentAt: '2026-08-25T18:20:00', direction: 'INBOUND', messageType: 'TEXT', textContent: '请帮我确认这款商品', starred: false, media: [] },
      { id: 3, sentAt: '2026-08-25T18:25:00', direction: 'OUTBOUND', messageType: 'IMAGE', textContent: '这是商品图片', starred: false, media: [{ id: 20, fileId: 1354, mediaType: 'IMAGE', mimeType: 'image/jpeg', downloadStatus: 'IMPORTED' }] },
      { id: 4, sentAt: '2026-08-25T18:30:00', direction: 'INBOUND', messageType: 'VIDEO', starred: false, media: [{ id: 30, mediaType: 'VIDEO', downloadStatus: 'METADATA_ONLY', issueType: 'MEDIA_PATH_EMPTY' }] },
    ]
    return route.fulfill({ json: ok({ records, total: 52, size: 50, current: Number(requestedPage || 1), pages: 2 }) })
  })
  await page.route('**/api/whatsapp/issues?*', route => route.fulfill({ json: ok({
    records: [
      { id: 30, accountId: 1, identityKey: '243835453734', contactName: 'Sbk(刚果金) Fashion+243', phoneNormalized: '243835453734', messageTime: '2026-08-25T18:30:00', mediaType: 'VIDEO', issueType: 'MEDIA_PATH_EMPTY', status: 'OPEN', lastDetectedAt: '2026-08-26T10:00:00' },
      { id: 31, accountId: 1, identityKey: '243835453734', contactName: 'Sbk(刚果金) Fashion+243', phoneNormalized: '243835453734', messageTime: '2026-08-25T17:00:00', mediaType: 'VIDEO', issueType: 'THUMBNAIL_ONLY', status: 'OPEN', lastDetectedAt: '2026-08-26T10:00:00' },
    ], total: 2, size: 20, current: 1, pages: 1,
  }) }))
  await page.route('**/api/files/*/preview?*', route => route.fulfill({ status: 200, contentType: 'application/octet-stream', body: '' }))

  await page.goto('/customers/32')
  await expect(page.getByRole('tab', { name: 'WhatsApp' })).toBeVisible()
  await page.getByRole('tab', { name: 'WhatsApp' }).click()

  await expect(page.getByText('Sbk(刚果金) Fashion+243').first()).toBeVisible()
  await expect(page.getByText('已绑定', { exact: true })).toBeVisible()
  await expect(page.getByText('52', { exact: true })).toBeVisible()
  await expect(page.getByText('请帮我确认这款商品')).toBeVisible()
  await expect(page.getByText('这是商品图片')).toBeVisible()
  await expect(page.getByText('视频尚未完整归档')).toBeVisible()
  await expect(page.getByRole('button', { name: '仅扫描此客户' })).toBeVisible()
  await expect(page.getByText('只读归档，不会向客户发送任何内容')).toBeVisible()

  await page.locator('.wa-conversation__canvas').evaluate(element => { element.scrollTop = 0; element.dispatchEvent(new Event('scroll')) })
  await expect(page.getByText('这是向上加载的更早消息')).toBeVisible()

  await page.locator('.wa-issue-metric').click()
  await expect(page.getByText('Sbk(刚果金) Fashion+243 · 缺失媒体')).toBeVisible()
  await expect(page.getByText('Mac 尚未获得媒体路径', { exact: true })).toBeVisible()
  await expect(page.getByText('只有缩略图，原文件未同步', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /发送/ })).toHaveCount(0)
})
