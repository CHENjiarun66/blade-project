import { expect, test, type Page } from '@playwright/test'

const permissions = ['data:catalog:view', 'btn:order:create', 'btn:order:view', 'menu:order']
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
function product(id: number) {
  return { id, productCode: `P${id}`, name: `款式${id}`, categoryId: null, categoryName: null,
    mainImageUrl: `/api/files/${id * 10}/preview`, imageUrls: [`/api/files/${id * 10 + 1}/preview`], hasImage: true, hasStock: true, stockStatus: '有现货', tags: [], colors: [], sizes: [], skus: [], createTime: '2026-11-02T00:00:00' }
}
async function setup(page: Page, goods = [product(1), product(2)], access = permissions) {
  await page.addInitScript(({ access }) => {
    const payload = btoa(JSON.stringify({ tenantId: 1, sub: 'catalog-test', exp: Math.floor(Date.now() / 1000) + 3600 }))
    localStorage.setItem('token', `e30.${payload}.fixture`)
    localStorage.setItem('userInfo', JSON.stringify({ userId: '4', username: 'catalog-test', realName: '测试店员' }))
    localStorage.setItem('permissions', JSON.stringify(access))
  }, { access })
  await page.route('**/api/auth/codes', route => route.fulfill({ json: access }))
  await page.route('**/api/catalog/filters', route => route.fulfill({ json: { code: 200, data: { categories: [], colors: [], sizes: [], stockModes: [] } } }))
  await page.route('**/api/outlets/options', route => route.fulfill({ json: { code: 200, data: { items: [{ id: 1, outletCode: 'TEST', outletName: '测试档口', status: 1 }], defaultOutletId: 1, locked: true, scopeType: 'ASSIGNED', peopleScope: 'SELF' } } }))
  const requestedPages: number[] = [], imageRequests: string[] = []
  await page.route('**/api/catalog/products**', async route => {
    const url = new URL(route.request().url()), detail = url.pathname.match(/\/products\/(\d+)$/)
    if (detail) { await route.fulfill({ json: { code: 200, data: goods.find(p => p.id === Number(detail[1])) } }); return }
    let filtered = goods
    if (url.searchParams.get('productCode')) filtered = goods.filter(p => p.productCode === url.searchParams.get('productCode'))
    if (url.searchParams.get('productIds')) filtered = goods.filter(p => url.searchParams.get('productIds')!.split(',').map(Number).includes(p.id))
    const current = Number(url.searchParams.get('current') || 1), size = Number(url.searchParams.get('size') || 20)
    requestedPages.push(current)
    await route.fulfill({ json: { code: 200, data: { records: filtered.slice((current - 1) * size, current * size), total: filtered.length, current, size, pages: Math.ceil(filtered.length / size) } } })
  })
  await page.route('**/api/files/**', async route => {
    const url = new URL(route.request().url())
    imageRequests.push(`${url.pathname}?type=${url.searchParams.get('type') || 'original'}`)
    await route.fulfill({ contentType: 'image/png', body: pixel })
  })
  return { requestedPages, imageRequests }
}
async function drag(page: Page, selector: string, dx: number, dy = 0) {
  const bounds = await page.locator(selector).boundingBox(); expect(bounds).toBeTruthy()
  const x = bounds!.x + bounds!.width / 2, y = bounds!.y + bounds!.height / 2
  await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + dx, y + dy, { steps: 8 }); await page.mouse.up()
}
async function fillStyle(page: Page, id = 1, qty = '120', price = '28.00') {
  await page.getByRole('button', { name: `查看款式${id}详情`, exact: true }).click()
  await page.getByLabel(`P${id}颜色说明`, { exact: true }).fill('混色（3色）')
  await page.getByLabel(`P${id}总数量`, { exact: true }).fill(qty)
  await page.getByLabel(`P${id}本次单价`, { exact: true }).fill(price)
  await page.locator('#addDetail').click()
  await expect(page.locator('.side-panel')).toHaveCount(0)
}

test('catalog pages on scrolling without showing pagination', async ({ page }) => {
  const { requestedPages } = await setup(page, Array.from({ length: 25 }, (_, i) => product(i + 1)))
  await page.goto('/catalog'); await expect(page.locator('.product-card')).toHaveCount(20)
  await page.locator('.grid-area').evaluate(el => { el.scrollTop = el.scrollHeight; el.dispatchEvent(new Event('scroll')) })
  await expect(page.locator('.product-card')).toHaveCount(25); expect(requestedPages).toContain(2)
  await expect(page.locator('.grid-pagination')).toHaveCount(0)
})

test('cached products remain usable while the list refreshes', async ({ page }) => {
  await setup(page, [product(2)])
  await page.addInitScript(cached => { localStorage.setItem('catalog:products:v1:all|all|all|all|all|all', JSON.stringify({ savedAt: Date.now(), products: [cached], total: 1, current: 1, pages: 1 })) }, product(9))
  await page.route('**/api/catalog/products?**', async route => {
    await new Promise(resolve => setTimeout(resolve, 600))
    await route.fulfill({ json: { code: 200, data: { records: [product(2)], total: 1, current: 1, size: 20, pages: 1 } } })
  })
  await page.goto('/catalog'); await expect(page.getByText('P9', { exact: true })).toBeVisible(); await expect(page.getByText('P2', { exact: true })).toBeVisible()
})

test('swipe locks direction and shares current image with detail and lightbox', async ({ page }) => {
  await setup(page); await page.goto('/catalog')
  const card = '.gallery-card[data-product-id="1"]'
  await drag(page, card, 12, -90); await expect(page.locator(card)).toHaveAttribute('data-active-index', '0')
  await drag(page, card, -100, 4); await expect(page.locator(card)).toHaveAttribute('data-active-index', '1')
  await expect(page.locator('dialog.lightbox')).not.toBeVisible()
  await page.getByRole('button', { name: '查看款式1详情' }).click()
  await expect(page.locator('.gallery-detail')).toHaveAttribute('data-active-index', '1')
  await page.locator('.gallery-detail .gallery-slide[aria-hidden="false"]').click()
  await expect(page.locator('.gallery-lightbox')).toHaveAttribute('data-active-index', '1')
  await drag(page, '.gallery-lightbox', -120, 1); await expect(page.locator('.gallery-lightbox')).toHaveAttribute('data-active-index', '1')
  await drag(page, '.gallery-lightbox', 120, 1); await expect(page.locator('.gallery-lightbox')).toHaveAttribute('data-active-index', '0')
})

test('card, selected thumbnail and lightbox use their intended image variants', async ({ page }) => {
  const { imageRequests } = await setup(page); await page.goto('/catalog')
  await expect.poll(() => imageRequests).toContain('/api/files/10/variant?type=card')
  expect(imageRequests.some(url => url.includes('/preview?'))).toBe(false)
  await page.getByLabel('加入款式1到选款清单', { exact: true }).click()
  await page.getByRole('button', { name: '打开批发清单，共1款' }).click()
  await expect.poll(() => imageRequests).toContain('/api/files/10/variant?type=thumb')
  await page.getByRole('button', { name: '继续浏览款式' }).click()
  await page.locator('.gallery-card[data-product-id="1"] .gallery-slide[aria-hidden="false"]').click()
  await expect.poll(() => imageRequests).toContain('/api/files/10/preview?type=original')
})

test('photo order includes the main image and deduplicates uploaded gallery and SKU images', async ({ page }) => {
  const p = { ...product(1), imageUrls: ['/api/files/10/preview', '/api/files/11/preview'], skus: [{ id: 1, skuCode: 'SKU', colorId: 1, colorName: '红色', sizeId: 1, sizeCode: 'M', imageUrls: ['/api/files/12/preview'], hasStock: true, stockStatus: '有现货' }] }
  await setup(page, [p]); await page.goto('/catalog')
  const gallery = page.locator('.gallery-card[data-product-id="1"]')
  await expect(gallery).toHaveAttribute('data-gallery-count', '3')
  await expect(gallery).toHaveAttribute('data-active-index', '0')
  await gallery.getByRole('button', { name: 'P1下一张图片' }).click(); await gallery.getByRole('button', { name: 'P1下一张图片' }).click()
  await expect(gallery).toHaveAttribute('data-active-index', '2')
})

test('phone uses two columns and remains browsable after rotation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await setup(page); await page.goto('/catalog')
  await expect(page.locator('.mobile-nav')).toBeVisible()
  expect(await page.locator('.product-grid').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(2)
  await page.setViewportSize({ width: 844, height: 390 }); await expect(page.getByText('请切回竖屏浏览')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '现货选款' })).toBeVisible()
})

test('detail add returns to browsing and retains the wholesale quote on refresh', async ({ page }) => {
  await setup(page); await page.goto('/catalog'); await fillStyle(page)
  await expect(page.getByRole('button', { name: '打开批发清单，共1款' })).toBeVisible()
  await page.reload(); await page.getByRole('button', { name: '打开批发清单，共1款' }).click()
  await expect(page.getByLabel('P1总数量', { exact: true })).toHaveValue('120')
  await expect(page.getByLabel('P1本次单价', { exact: true })).toHaveValue('28.00')
  await expect(page.locator('[data-line-amount="1"]')).toHaveText('3,360.00')
})

test('handoff retry reuses the same request ID and retains partial pending items', async ({ page }) => {
  await setup(page); const attempts: any[] = []
  await page.route('**/api/catalog/handoffs', async route => {
    attempts.push(route.request().postDataJSON())
    if (attempts.length === 1) { await route.abort('failed'); return }
    await route.fulfill({ json: { code: 200, data: { draftId: 91, reference: 'XK-RETRY', alreadyExists: true } } })
  })
  await page.goto('/catalog'); await fillStyle(page)
  await page.getByLabel('加入款式2到选款清单', { exact: true }).click()
  await page.getByRole('button', { name: '打开批发清单，共2款' }).click(); await page.locator('#handoffButton').click()
  const dialog = page.locator('.handoff-modal')
  await dialog.getByRole('button', { name: '交给店员确认', exact: true }).click()
  await expect(dialog.getByRole('alert')).toBeVisible()
  await dialog.getByRole('button', { name: '交给店员确认', exact: true }).click()
  await expect(dialog.getByRole('heading', { name: '批发清单已移交' })).toBeVisible()
  expect(attempts).toHaveLength(2); expect(attempts[0].requestId).toBe(attempts[1].requestId)
  expect(attempts[0].items[0]).toEqual({ productId: 1, colorNote: '混色（3色）', quantity: 120, unitPrice: '28.00' })
  expect(attempts[0].items[1].unitPrice).toBeNull(); expect(attempts[0].items[1].quantity).toBeNull()
})

test('a permission rejection keeps the logged-in account and selection intact', async ({ page }) => {
  await setup(page); await page.route('**/api/catalog/handoffs', route => route.fulfill({ status: 403, json: { code: 403, message: '档口权限已变更' } }))
  await page.goto('/catalog'); await fillStyle(page); await page.getByRole('button', { name: '打开批发清单，共1款' }).click(); await page.locator('#handoffButton').click()
  await page.locator('.handoff-modal').getByRole('button', { name: '交给店员确认', exact: true }).click()
  await expect(page.locator('.handoff-modal').getByRole('alert')).toBeVisible(); await expect(page).toHaveURL(/\/catalog$/)
  expect(await page.evaluate(() => !!localStorage.getItem('token'))).toBe(true)
  await page.getByRole('button', { name: '关闭确认' }).click(); await expect(page.getByLabel('P1本次单价', { exact: true })).toHaveValue('28.00')
})

// These geometry checks guard the iPad Safari/Home Screen bugs reported on 2026-11-02.
test('lightbox is centered and its close target stays inside after rotation', async ({ page }) => {
  await page.setViewportSize({ width: 834, height: 1194 })
  await setup(page)
  await page.goto('/catalog')
  await page.locator('.gallery-card[data-product-id="1"] .gallery-slide[aria-hidden="false"]').click()
  const dialog = page.getByRole('dialog', { name: '商品大图', exact: true })
  for (const size of [{ width: 834, height: 1194 }, { width: 1194, height: 834 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(size)
    const box = await dialog.boundingBox()
    const close = await dialog.getByRole('button', { name: '关闭大图', exact: true }).boundingBox()
    expect(box).toBeTruthy(); expect(close).toBeTruthy()
    expect(Math.abs(box!.x + box!.width / 2 - size.width / 2)).toBeLessThan(1)
    expect(Math.abs(box!.y + box!.height / 2 - size.height / 2)).toBeLessThan(1)
    expect(close!.x).toBeGreaterThanOrEqual(box!.x)
    expect(close!.y).toBeGreaterThanOrEqual(box!.y)
    expect(close!.x + close!.width).toBeLessThanOrEqual(box!.x + box!.width)
    expect(close!.y + close!.height).toBeLessThanOrEqual(box!.y + box!.height)
    expect(close!.width).toBeGreaterThanOrEqual(44)
    expect(close!.height).toBeGreaterThanOrEqual(44)
  }
  await dialog.getByRole('button', { name: '关闭大图', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('heading', { name: '现货选款', exact: true })).toBeVisible()
})

test('safe areas protect page controls, detail headers and modal hit targets exactly once', async ({ page }) => {
  await page.setViewportSize({ width: 834, height: 1194 })
  await setup(page)
  await page.goto('/catalog')
  // Chromium has no iPad status bar; override the CSS inputs, preserving production layout rules.
  await page.addStyleTag({ content: '.catalog-page{--catalog-safe-top:24px;--catalog-safe-bottom:20px;--catalog-safe-left:0px;--catalog-safe-right:0px}' })
  const toolbar = await page.locator('.topbar').boundingBox()
  expect(toolbar!.y).toBe(24)
  expect((await page.getByRole('searchbox', { name: '搜索款号或商品名称' }).boundingBox())!.height).toBeGreaterThanOrEqual(44)
  await page.getByRole('button', { name: '查看款式1详情', exact: true }).click()
  expect((await page.locator('.detail-header').boundingBox())!.y).toBe(24)
  const footer = await page.locator('.panel-footer').boundingBox()
  expect(footer!.y + footer!.height).toBeLessThanOrEqual(1174)
  await page.locator('.gallery-detail .gallery-slide[aria-hidden="false"]').click()
  const dialog = page.getByRole('dialog', { name: '商品大图', exact: true })
  const bounds = await dialog.boundingBox()
  expect(Math.abs(bounds!.y + bounds!.height / 2 - 599)).toBeLessThan(1)
  await dialog.getByRole('button', { name: '关闭大图', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '返回商品列表', exact: true }).click()
  const nav = await page.locator('.mobile-nav').boundingBox()
  expect(nav!.y + nav!.height).toBe(824)
  expect(await page.locator('.mobile-nav').evaluate(el => getComputedStyle(el).paddingBottom)).toBe('0px')
})

test('detail photos fit in full rather than being cropped by a different frame ratio', async ({ page }) => {
  await page.setViewportSize({ width: 834, height: 1194 })
  await setup(page)
  await page.goto('/catalog')
  await page.getByRole('button', { name: '查看款式1详情', exact: true }).click()
  const photo = page.locator('.gallery-detail .gallery-slide[aria-hidden="false"] img')
  await expect(photo).toHaveCSS('object-fit', 'contain')
  const header = await page.locator('.detail-header').boundingBox()
  const frame = await page.locator('.detail-image').boundingBox()
  expect(frame!.y).toBeGreaterThanOrEqual(header!.y + header!.height)
  expect(await page.locator('.detail-image').evaluate(el => getComputedStyle(el).aspectRatio)).toBe('3 / 4')
})

test('slow short card swipes flip without requiring an exaggerated drag', async ({ page }) => {
  await page.setViewportSize({ width: 834, height: 1194 }); await setup(page); await page.goto('/catalog')
  const gallery = page.locator('.gallery-card[data-product-id="1"]')
  const box = await gallery.boundingBox(); expect(box).toBeTruthy()
  const x = box!.x + box!.width / 2, y = box!.y + box!.height / 2
  await page.mouse.move(x,y); await page.mouse.down(); await page.mouse.move(x-20,y+18,{steps:8})
  await page.waitForTimeout(180) // release slowly: distance, not a fast-flick shortcut, must be enough
  await page.mouse.up(); await expect(gallery).toHaveAttribute('data-active-index','1')
  await drag(page,'.gallery-card[data-product-id="1"]',5,90)
  await expect(gallery).toHaveAttribute('data-active-index','1')
})

test('sellable stock is visible on cards and by colour in detail', async ({ page }) => {
  const p = { ...product(1), availableQty:120, skus:[{id:1,skuCode:'P1-WHITE',colorId:1,colorName:'白色',sizeId:1,sizeCode:'均码',imageUrls:[],hasStock:true,stockStatus:'有现货',availableQty:120}] }
  await setup(page,[p]); await page.goto('/catalog')
  await expect(page.locator('.product-stock')).toHaveText('可选现货 120 件')
  await page.getByRole('button',{name:'查看款式1详情',exact:true}).click()
  await expect(page.locator('.detail-stock-summary')).toContainText('120件')
  await expect(page.locator('.detail-stock-variants')).toContainText('白色 · 均码 120 件')
})

test.describe('touch numeric entry',()=>{
  test.use({hasTouch:true})
  test('quantity and price use the internal pad without shrinking the page',async({page})=>{
    await page.setViewportSize({width:834,height:1194}); await setup(page); await page.goto('/catalog')
    await page.getByRole('button',{name:'查看款式1详情',exact:true}).click()
    const qty=page.getByLabel('P1总数量',{exact:true}),price=page.getByLabel('P1本次单价',{exact:true})
    await expect(qty).toHaveAttribute('readonly',''); await expect(qty).toHaveAttribute('inputmode','none')
    await qty.click(); const pad=page.getByRole('dialog',{name:'数字键盘',exact:true})
    for(const key of ['1','2','0']) await pad.getByRole('button',{name:`数字${key}`,exact:true}).click()
    await pad.getByRole('button',{name:'确定',exact:true}).click(); await expect(qty).toHaveValue('120')
    await price.click(); for(const key of ['2','8','.','5']) await pad.getByRole('button',{name:key==='.'?'小数点':`数字${key}`,exact:true}).click()
    await page.setViewportSize({width:1194,height:834})
    const bounds=await pad.boundingBox();expect(bounds!.y).toBeGreaterThanOrEqual(0);expect(bounds!.y+bounds!.height).toBeLessThanOrEqual(834)
    await pad.getByRole('button',{name:'确定',exact:true}).click();await expect(price).toHaveValue('28.50')
    await expect(page.locator('.detail-line-total')).toContainText('3,420.00')
    await qty.click();await pad.getByRole('button',{name:'数字9',exact:true}).click();await pad.getByRole('button',{name:'取消',exact:true}).click()
    await expect(qty).toHaveValue('120');await expect(pad).not.toBeVisible()
    expect(await page.locator('.catalog-page').evaluate(el=>el.getBoundingClientRect().height)).toBe(834)
    expect(await page.evaluate(()=>window.scrollY)).toBe(0)
  })
})


test('lightbox gestures own the image index and freeze background scrolling until close', async ({ page }) => {
  await setup(page); await page.setViewportSize({width:834,height:1194}); await page.goto('/catalog')
  const card=page.locator('.gallery-card[data-product-id="1"]'),browse=page.locator('.grid-area')
  const before=await browse.evaluate(el=>el.scrollTop)
  await card.locator('.gallery-slide[aria-hidden="false"]').click()
  await expect(page.locator('body')).toHaveCSS('position','fixed')
  const locked=await browse.evaluate(el=>el.scrollTop)
  await drag(page,'.gallery-lightbox',-20,25)
  await expect(page.locator('.gallery-lightbox')).toHaveAttribute('data-active-index','1')
  await expect(card).toHaveAttribute('data-active-index','0')
  await page.mouse.wheel(0,400); expect(await browse.evaluate(el=>el.scrollTop)).toBe(locked)
  await page.getByRole('button',{name:'关闭大图',exact:true}).click()
  await expect(card).toHaveAttribute('data-active-index','1')
  expect(await browse.evaluate(el=>el.scrollTop)).toBe(before)
  await expect(page.locator('body')).not.toHaveCSS('position','fixed')
})

test('single-finger touch listeners consume a diagonal photo swipe but leave vertical scroll available', async ({ page }) => {
  await setup(page); await page.goto('/catalog')
  const gallery=page.locator('.gallery-card[data-product-id="1"]')
  async function touch(dx:number,dy:number) {
    return gallery.evaluate((el,{dx,dy})=>{
      const p=(x:number,y:number)=>new Touch({identifier:1,target:el,clientX:x,clientY:y})
      el.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true,touches:[p(100,100)],changedTouches:[p(100,100)]}))
      const move=new TouchEvent('touchmove',{bubbles:true,cancelable:true,touches:[p(100+dx,100+dy)],changedTouches:[p(100+dx,100+dy)]})
      el.dispatchEvent(move)
      el.dispatchEvent(new TouchEvent('touchend',{bubbles:true,cancelable:true,touches:[],changedTouches:[p(100+dx,100+dy)]}))
      return move.defaultPrevented
    },{dx,dy})
  }
  expect(await touch(7,80)).toBe(false); await expect(gallery).toHaveAttribute('data-active-index','0')
  expect(await touch(-20,18)).toBe(true); await expect(gallery).toHaveAttribute('data-active-index','1')
})
