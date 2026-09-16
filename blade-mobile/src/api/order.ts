import client from './client'
import type { R, PageResult } from '@/types/auth'
import type { OrderVO, OrderCreateDTO, OrderPageDTO } from '@/types/order'

export async function getOrderList(params: OrderPageDTO): Promise<R<PageResult<OrderVO>>> {
  const response = await client.get<R<PageResult<OrderVO>>>('/orders', { params })
  return response.data
}

export async function getOrderById(id: number): Promise<R<OrderVO>> {
  const response = await client.get<R<OrderVO>>(`/orders/${id}`)
  return response.data
}

export async function createOrder(data: OrderCreateDTO): Promise<R<number>> {
  const response = await client.post<R<number>>('/orders', data)
  return response.data
}

export async function confirmPayment(orderId: number, paidAmount: number): Promise<R<void>> {
  const response = await client.post<R<void>>('/orders/confirm-payment', { orderId, paidAmount })
  return response.data
}

export async function addPayment(orderId: number, additionalAmount: number): Promise<R<void>> {
  const response = await client.post<R<void>>(`/orders/${orderId}/add-payment`, { additionalAmount })
  return response.data
}

export async function confirmSettlement(
  orderId: number,
  data: { finalReceivedAmount: number; writeOffReason?: string; idempotencyKey?: string }
): Promise<R<void>> {
  const response = await client.post<R<void>>(`/orders/${orderId}/confirm-settlement`, data)
  return response.data
}

export async function deliverOrder(id: number): Promise<R<void>> {
  const response = await client.post<R<void>>(`/orders/${id}/deliver`)
  return response.data
}

export async function completeOrder(id: number): Promise<R<void>> {
  const response = await client.post<R<void>>(`/orders/${id}/complete`)
  return response.data
}

export async function cancelOrder(id: number, reason: string): Promise<R<void>> {
  const response = await client.post<R<void>>(`/orders/${id}/cancel`, { reason })
  return response.data
}

export async function deleteOrder(id: number): Promise<R<void>> {
  const response = await client.delete<R<void>>(`/orders/${id}`)
  return response.data
}
