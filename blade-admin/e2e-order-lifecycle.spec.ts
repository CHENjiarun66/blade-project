import { test, expect, type APIRequestContext } from '@playwright/test'

const API_BASE = process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api'
// 凭据从环境变量注入，不写入源码（本地运行示例：E2E_PASSWORD=*** npx playwright test ...）
const E2E_TENANT = process.env.E2E_TENANT_CODE || 'test_tenant'
const E2E_USER = process.env.E2E_USERNAME || 'admin'
const E2E_PASSWORD = process.env.E2E_PASSWORD || ''

type LoginResult = { token: string }

async function apiLogin(request: APIRequestContext): Promise<LoginResult> {
  expect(E2E_PASSWORD, 'E2E_PASSWORD 环境变量必须注入测试凭据').toBeTruthy()
  const loginRes = await request.post(`${API_BASE}/auth/login`, {
    data: {
      tenantCode: E2E_TENANT,
      username: E2E_USER,
      password: E2E_PASSWORD,
    },
  })
  expect(loginRes.ok()).toBeTruthy()
  const loginJson = await loginRes.json()
  const token = loginJson.accessToken || loginJson.token
  expect(token).toBeTruthy()
  return { token }
}

async function createSeedProductAndInventory(request: APIRequestContext, token: string) {
  const unique = Date.now()
  const createProductRes = await request.post(`${API_BASE}/products`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: `生命周期E2E商品-${unique}`,
      productCode: `LC-E2E-${unique}`,
      categoryId: 1,
      unit: '件',
      price: 200,
      description: '订单生命周期 E2E 测试商品',
      colorIds: [1],
      sizeIds: [1],
    },
  })
  expect(createProductRes.ok()).toBeTruthy()
  const productId = (await createProductRes.json()).data

  const detail = await (await request.get(`${API_BASE}/products/${productId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })).json()
  const sku = detail.data.skus[0]
  expect(sku).toBeTruthy()

  const inRes = await request.post(`${API_BASE}/inventory/in`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      warehouseId: 1,
      items: [{ skuId: sku.id, quantity: 100, remark: 'E2E 期初' }],
      remark: 'E2E 期初',
    },
  })
  expect(inRes.ok()).toBeTruthy()
  const inJson = await inRes.json()
  expect(inJson.code).toBe(200)
  return { skuId: sku.id as number }
}

async function getOrder(request: APIRequestContext, token: string, id: number) {
  const res = await request.get(`${API_BASE}/orders/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(res.ok()).toBeTruthy()
  return (await res.json()).data
}

test.describe('订单生命周期 E2E（API 关键路径）', () => {
  test('STOCK_LINKED 全链路：创建→收款→结清→履约方式→配货→确认→发货→完成', async ({ request }) => {
    const { token } = await apiLogin(request)
    const { skuId } = await createSeedProductAndInventory(request, token)

    // 创建订单：提交不携带任何最终状态数字
    const createRes = await request.post(`${API_BASE}/orders`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        customerName: '生命周期E2E客户',
        warehouseId: 1,
        items: [{ skuId, quantity: 2, price: 100 }],
      },
    })
    expect(createRes.ok()).toBeTruthy()
    const orderId = (await createRes.json()).data

    let order = await getOrder(request, token, orderId)
    expect(order.fulfillmentStatus).toBe('CONFIRMED')
    expect(order.collectionStatus).toBe('UNPAID')
    expect(order.legacyUnmigrated).toBeFalsy()
    expect(order.allowedActions).toContain('recordPayment')

    // 收款 200 = 全额 → SETTLED
    const payRes = await request.post(`${API_BASE}/orders/confirm-payment`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { orderId, paidAmount: 200 },
    })
    expect(payRes.ok()).toBeTruthy()

    order = await getOrder(request, token, orderId)
    expect(order.collectionStatus).toBe('SETTLED')
    expect(order.settlementMethod).toBe('FULL_RECEIPT')
    expect(order.allowedActions).toContain('chooseFulfillmentMode')

    // 选择关联库存履约
    const modeRes = await request.post(`${API_BASE}/orders/${orderId}/fulfillment-mode`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { mode: 'STOCK_LINKED' },
    })
    expect(modeRes.ok()).toBeTruthy()

    order = await getOrder(request, token, orderId)
    expect(order.fulfillmentStatus).toBe('WAITING_ALLOCATION')

    // 配货计划（startAllocation）→ 确认（confirmAllocation）
    const planRes = await request.post(`${API_BASE}/orders/${orderId}/delivery-plan`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(planRes.ok()).toBeTruthy()

    const confirmPlanRes = await request.post(`${API_BASE}/orders/${orderId}/confirm-adjustment`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(confirmPlanRes.ok()).toBeTruthy()

    order = await getOrder(request, token, orderId)
    expect(order.fulfillmentStatus).toBe('READY_TO_SHIP')

    // 发货 → 已发货；完成 → 已完成
    expect((await request.post(`${API_BASE}/orders/${orderId}/deliver`, {
      headers: { Authorization: `Bearer ${token}` },
    })).ok()).toBeTruthy()
    order = await getOrder(request, token, orderId)
    expect(order.fulfillmentStatus).toBe('SHIPPED')

    expect((await request.post(`${API_BASE}/orders/${orderId}/complete`, {
      headers: { Authorization: `Bearer ${token}` },
    })).ok()).toBeTruthy()
    order = await getOrder(request, token, orderId)
    expect(order.fulfillmentStatus).toBe('COMPLETED')

    // 财务流水：一笔 RECEIPT
    expect(order.financialRecords.filter((r: any) => r.recordType === 'RECEIPT').length).toBe(1)
  })

  test('RECORD_ONLY：仅记录订单完成且零库存流水', async ({ request }) => {
    const { token } = await apiLogin(request)
    const { skuId } = await createSeedProductAndInventory(request, token)

    const createRes = await request.post(`${API_BASE}/orders`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        customerName: '仅记录E2E客户',
        warehouseId: 1,
        items: [{ skuId, quantity: 1, price: 80 }],
      },
    })
    const orderId = (await createRes.json()).data

    // 库存基线
    const invBefore = await (await request.get(`${API_BASE}/inventory/warehouse/1`, {
      headers: { Authorization: `Bearer ${token}` },
    })).json()
    const skuInvBefore = (invBefore.data ?? []).find((i: any) => i.skuId === skuId)?.quantity ?? 0

    // 收款 + 仅记录
    expect((await request.post(`${API_BASE}/orders/confirm-payment`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { orderId, paidAmount: 80 },
    })).ok()).toBeTruthy()
    expect((await request.post(`${API_BASE}/orders/${orderId}/fulfillment-mode`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { mode: 'RECORD_ONLY' },
    })).ok()).toBeTruthy()

    const order = await getOrder(request, token, orderId)
    expect(order.fulfillmentStatus).toBe('COMPLETED')
    expect(order.fulfillmentMode).toBe('RECORD_ONLY')

    // 库存不变
    const invAfter = await (await request.get(`${API_BASE}/inventory/warehouse/1`, {
      headers: { Authorization: `Bearer ${token}` },
    })).json()
    const skuInvAfter = (invAfter.data ?? []).find((i: any) => i.skuId === skuId)?.quantity ?? 0
    expect(skuInvAfter).toBe(skuInvBefore)
  })

  test('短款结清：收款90+核销10 → SETTLED(WRITE_OFF)，实收与核销分开', async ({ request }) => {
    const { token } = await apiLogin(request)
    const { skuId } = await createSeedProductAndInventory(request, token)

    const createRes = await request.post(`${API_BASE}/orders`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        customerName: '短款E2E客户',
        warehouseId: 1,
        items: [{ skuId, quantity: 1, price: 100 }],
      },
    })
    const orderId = (await createRes.json()).data

    // 先收 90（partial）
    expect((await request.post(`${API_BASE}/orders/confirm-payment`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { orderId, paidAmount: 90 },
    })).ok()).toBeTruthy()

    // 标记结清（核销 10）
    expect((await request.post(`${API_BASE}/orders/${orderId}/add-payment`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { additionalAmount: 0, markAsSettled: true, writeOffReason: '客户少付尾款' },
    })).ok()).toBeTruthy()

    const order = await getOrder(request, token, orderId)
    expect(order.collectionStatus).toBe('SETTLED')
    expect(order.settlementMethod).toBe('WRITE_OFF')
    expect(order.grossReceivedAmount).toBe(90)
    expect(order.writeOffAmount).toBe(10)
    expect(order.balanceAmount).toBe(0)
  })
})
