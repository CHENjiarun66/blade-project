import { expect, test, type Page, type Route } from '@playwright/test'

function product(id: number, code = `P${id}`) {
  return {
    id,
    productCode: code,
    name: code,
    categoryId: null,
    categoryName: null,
    mainImageUrl: null,
    imageUrls: [],
    hasImage: false,
    hasStock: true,
    stockStatus: '有现货',
    tags: null,
    colors: [],
    sizes: [],
    skus: [],
    createTime: '2026-06-04T00:00:00',
  }
}

async function grantCatalogAccess(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token')
    localStorage.setItem('refreshToken', 'test-refresh-token')
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '1',
      username: 'admin',
      realName: '管理员',
    }))
    localStorage.setItem('permissions', JSON.stringify(['data:catalog:view']))
  })
}

async function mockFilters(page: Page) {
  await page.route('**/api/catalog/filters', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: { categories: [], colors: [], sizes: [], stockModes: [] },
      }),
    })
  })
}

test('catalog loads next page when scrolled near the bottom instead of showing pagination', async ({ page }) => {
  await grantCatalogAccess(page)
  await mockFilters(page)
  const requestedPages: number[] = []

  await page.route('**/api/catalog/products**', async (route: Route) => {
    const url = new URL(route.request().url())
    const current = Number(url.searchParams.get('current') || '1')
    requestedPages.push(current)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current,
          size: 20,
          total: 25,
          pages: 2,
          records: current === 1
            ? Array.from({ length: 20 }, (_, index) => product(index + 1))
            : Array.from({ length: 5 }, (_, index) => product(index + 21)),
        },
      }),
    })
  })

  await page.goto('/catalog')
  await expect(page.locator('.product-card')).toHaveCount(20)
  await expect(page.locator('.grid-pagination')).toHaveCount(0)

  await page.locator('.grid-area').evaluate((el) => {
    el.scrollTop = el.scrollHeight
    el.dispatchEvent(new Event('scroll'))
  })

  await expect(page.locator('.product-card')).toHaveCount(25)
  expect(requestedPages).toContain(2)
})

test('catalog renders cached products before the network refresh finishes', async ({ page }) => {
  await grantCatalogAccess(page)
  await mockFilters(page)
  await page.addInitScript(() => {
    localStorage.setItem('catalog:products:v1:all|all|all|all|all|all', JSON.stringify({
      savedAt: Date.now(),
      total: 1,
      current: 1,
      pages: 1,
      products: [{
        id: 9001,
        productCode: 'CACHE-9001',
        name: 'CACHE-9001',
        categoryId: null,
        categoryName: null,
        mainImageUrl: null,
        imageUrls: [],
        hasImage: false,
        hasStock: true,
        stockStatus: '有现货',
        tags: null,
        colors: [],
        sizes: [],
        skus: [],
        createTime: '2026-06-04T00:00:00',
      }],
    }))
  })
  await page.route('**/api/catalog/products**', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1200))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [product(9002, 'NETWORK-9002')],
        },
      }),
    })
  })

  await page.goto('/catalog')
  await expect(page.getByText('CACHE-9001').first()).toBeVisible()
  await expect(page.getByText('NETWORK-9002').first()).toBeVisible()
})

test('catalog detail carousel and fullscreen viewer support swipe navigation', async ({ page }) => {
  await grantCatalogAccess(page)
  await mockFilters(page)
  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [{
            ...product(7001, 'SWIPE-7001'),
            mainImageUrl: '/swipe-cover.jpg',
            imageUrls: ['/swipe-a.jpg', '/swipe-b.jpg'],
            hasImage: true,
          }],
        },
      }),
    })
  })
  await page.route('**/api/catalog/products/7001', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          ...product(7001, 'SWIPE-7001'),
          mainImageUrl: '/swipe-cover.jpg',
          imageUrls: ['/swipe-a.jpg', '/swipe-b.jpg'],
          hasImage: true,
        },
      }),
    })
  })

  await page.goto('/catalog')
  await page.getByText('SWIPE-7001').first().click()
  await expect(page.locator('.detail-panel .carousel-main')).toHaveAttribute('data-active-index', '0')

  const carouselBox = await page.locator('.detail-panel .carousel-main').boundingBox()
  expect(carouselBox).toBeTruthy()
  await page.mouse.move(carouselBox!.x + carouselBox!.width - 20, carouselBox!.y + carouselBox!.height / 2)
  await page.mouse.down()
  await page.mouse.move(carouselBox!.x + 20, carouselBox!.y + carouselBox!.height / 2)
  await page.mouse.up()
  await expect(page.locator('.detail-panel .carousel-main')).toHaveAttribute('data-active-index', '1')

  await page.locator('.detail-panel .carousel-main').click()
  await expect(page.locator('.fullscreen-overlay .fs-image-wrap')).toHaveAttribute('data-active-index', '1')

  const fullscreenBox = await page.locator('.fs-image-wrap').boundingBox()
  expect(fullscreenBox).toBeTruthy()
  await page.mouse.move(fullscreenBox!.x + 20, fullscreenBox!.y + fullscreenBox!.height / 2)
  await page.mouse.down()
  await page.mouse.move(fullscreenBox!.x + fullscreenBox!.width - 20, fullscreenBox!.y + fullscreenBox!.height / 2)
  await page.mouse.up()
  await expect(page.locator('.fullscreen-overlay .fs-image-wrap')).toHaveAttribute('data-active-index', '0')
})

test('catalog requests card, thumb, and original images for their intended layers', async ({ page }) => {
  await grantCatalogAccess(page)
  await mockFilters(page)
  const imageRequests: string[] = []
  const pixel = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
  const catalogProduct = {
    ...product(7201, 'VARIANT-7201'),
    mainImageUrl: '/api/files/101/preview',
    imageUrls: ['/api/files/102/preview', '/api/files/104/preview'],
    hasImage: true,
    skus: [{
      id: 1,
      skuCode: 'VARIANT-7201-RED-M',
      colorId: 1,
      colorName: '红色',
      sizeId: 1,
      sizeCode: 'M',
      imageUrls: ['/api/files/103/preview'],
      hasStock: true,
      stockStatus: '有现货',
    }],
  }

  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [catalogProduct],
        },
      }),
    })
  })
  await page.route('**/api/catalog/products/7201', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: catalogProduct }),
    })
  })
  await page.route('**/api/files/**', async (route) => {
    const url = new URL(route.request().url())
    imageRequests.push(`${url.pathname}?type=${url.searchParams.get('type') || 'original'}`)
    await route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
  })

  await page.goto('/catalog')
  await expect.poll(() => imageRequests).toContain('/api/files/101/variant?type=card')

  await page.getByText('VARIANT-7201').first().click()
  await expect(page.locator('.detail-panel .carousel-main')).toBeVisible()
  await expect.poll(() => imageRequests).toContain('/api/files/102/variant?type=card')
  await expect.poll(() => imageRequests).toContain('/api/files/102/variant?type=thumb')
  expect(imageRequests).not.toContain('/api/files/103/variant?type=card')
  expect(imageRequests).not.toContain('/api/files/103/variant?type=thumb')

  await page.locator('.detail-panel .carousel-main').click()
  await expect(page.locator('.fullscreen-overlay')).toBeVisible()
  await expect.poll(() => imageRequests).toContain('/api/files/102/preview?type=original')
  expect(imageRequests).not.toContain('/api/files/101/preview?type=original')
})

test('catalog product gallery excludes duplicated main image and sku images', async ({ page }) => {
  await grantCatalogAccess(page)
  await mockFilters(page)
  const imageRequests: string[] = []
  const pixel = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
  const catalogProduct = {
    ...product(7301, 'GALLERY-7301'),
    mainImageUrl: '/api/files/201/preview',
    imageUrls: ['/api/files/201/preview', '/api/files/202/preview'],
    hasImage: true,
    skus: [{
      id: 1,
      skuCode: 'GALLERY-7301-BLUE-M',
      colorId: 1,
      colorName: '蓝色',
      sizeId: 1,
      sizeCode: 'M',
      imageUrls: ['/api/files/203/preview'],
      hasStock: true,
      stockStatus: '有现货',
    }],
  }

  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [catalogProduct],
        },
      }),
    })
  })
  await page.route('**/api/catalog/products/7301', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: catalogProduct }),
    })
  })
  await page.route('**/api/files/**', async (route) => {
    const url = new URL(route.request().url())
    imageRequests.push(`${url.pathname}?type=${url.searchParams.get('type') || 'original'}`)
    await route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
  })

  await page.goto('/catalog')
  await expect.poll(() => imageRequests).toContain('/api/files/201/variant?type=card')

  await page.getByText('GALLERY-7301').first().click()
  await expect(page.locator('.detail-panel .carousel-main')).toBeVisible()
  await expect.poll(() => imageRequests).toContain('/api/files/202/variant?type=card')
  expect(imageRequests).not.toContain('/api/files/203/variant?type=card')

  await page.locator('.detail-panel .carousel-main').click()
  await expect(page.locator('.fullscreen-overlay')).toBeVisible()
  await expect.poll(() => imageRequests).toContain('/api/files/202/preview?type=original')
  expect(imageRequests.filter((request) => request === '/api/files/201/preview?type=original')).toHaveLength(0)
})

test('catalog phone layout uses portrait-only browsing on iPhone 14 Pro size', async ({ page }) => {
  await page.setViewportSize({ width: 393, height: 852 })
  await grantCatalogAccess(page)
  await mockFilters(page)
  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 2,
          pages: 1,
          records: [product(8101, 'PHONE-8101'), product(8102, 'PHONE-8102')],
        },
      }),
    })
  })

  await page.goto('/catalog')
  await expect(page.getByText('PHONE-8101').first()).toBeVisible()
  await expect(page.locator('.mobile-action-bar')).toBeVisible()

  const columnCount = await page.locator('.product-grid').evaluate((el) => {
    return getComputedStyle(el).gridTemplateColumns.split(' ').filter(Boolean).length
  })
  expect(columnCount).toBe(2)

  await page.setViewportSize({ width: 852, height: 393 })
  await expect(page.getByText('请切回竖屏浏览')).toBeVisible()
  await expect(page.locator('.detail-panel')).toHaveCount(0)
})

test('catalog does not reset page scroll during visual viewport pinch resize', async ({ page }) => {
  await grantCatalogAccess(page)
  await mockFilters(page)
  await page.addInitScript(() => {
    ;(window as any).__scrollToCalls = 0
    const originalScrollTo = window.scrollTo.bind(window)
    window.scrollTo = ((...args: Parameters<typeof window.scrollTo>) => {
      ;(window as any).__scrollToCalls += 1
      return originalScrollTo(...args)
    }) as typeof window.scrollTo
  })
  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [product(8201, 'PINCH-8201')],
        },
      }),
    })
  })

  await page.goto('/catalog')
  await expect(page.getByText('PINCH-8201').first()).toBeVisible()
  await page.evaluate(() => {
    window.visualViewport?.dispatchEvent(new Event('resize'))
  })

  await expect.poll(() => page.evaluate(() => (window as any).__scrollToCalls)).toBe(0)
})

test('catalog search input focuses from touch on iPad viewport', async ({ page }) => {
  await page.setViewportSize({ width: 820, height: 1180 })
  await grantCatalogAccess(page)
  await mockFilters(page)
  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [product(8301, 'SEARCH-8301')],
        },
      }),
    })
  })

  await page.goto('/catalog')
  await expect(page.getByText('SEARCH-8301').first()).toBeVisible()

  await page.locator('.search-box').dispatchEvent('touchstart', {
    touches: [{ identifier: 1, target: await page.locator('.search-box').elementHandle(), clientX: 300, clientY: 60 }],
    changedTouches: [{ identifier: 1, target: await page.locator('.search-box').elementHandle(), clientX: 300, clientY: 60 }],
  })

  await expect(page.locator('.search-input .el-input__inner')).toBeFocused()
  await expect(page.locator('.search-input .el-input__inner')).toHaveCSS('user-select', 'text')
})

test('catalog fullscreen viewer clips adjacent slides on iPad portrait', async ({ page }) => {
  await page.setViewportSize({ width: 820, height: 1180 })
  await grantCatalogAccess(page)
  await mockFilters(page)
  const galleryProduct = {
    ...product(8401, 'CLIP-8401'),
    mainImageUrl: '/clip-cover.jpg',
    imageUrls: ['/clip-a.jpg', '/clip-b.jpg', '/clip-c.jpg'],
    hasImage: true,
  }
  await page.route('**/api/catalog/products**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
          records: [galleryProduct],
        },
      }),
    })
  })

  await page.goto('/catalog')
  await page.locator('.card-image').first().click()
  await expect(page.locator('.fullscreen-overlay')).toBeVisible()
  await page.locator('.fs-thumb').nth(1).click()
  await expect(page.locator('.fs-image-wrap')).toHaveAttribute('data-active-index', '1')

  await expect.poll(() => page.locator('.fs-image-slide').evaluateAll((slides) => {
    return slides.map((slide) => {
      const rect = slide.getBoundingClientRect()
      return { left: rect.left, right: rect.right }
    })
  })).toEqual([
    { left: -820, right: 0 },
    { left: 0, right: 820 },
    { left: 820, right: 1640 },
  ])
})
