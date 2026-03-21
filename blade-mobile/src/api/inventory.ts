import client from './client'
import type { R, PageResult } from '@/types/auth'
import type {
  InventoryVO,
  InventoryInDTO,
  InventoryOutDTO,
  InventoryAdjustDTO,
  InventoryPageDTO,
  WarehouseVO
} from '@/types/inventory'

export async function getInventoryList(params: InventoryPageDTO): Promise<R<PageResult<InventoryVO>>> {
  const response = await client.get<R<PageResult<InventoryVO>>>('/inventory', { params })
  return response.data
}

export async function getInventoryById(id: number): Promise<R<InventoryVO>> {
  const response = await client.get<R<InventoryVO>>(`/inventory/${id}`)
  return response.data
}

export async function inventoryIn(data: InventoryInDTO): Promise<R<void>> {
  const response = await client.post<R<void>>('/inventory/in', data)
  return response.data
}

export async function inventoryOut(data: InventoryOutDTO): Promise<R<void>> {
  const response = await client.post<R<void>>('/inventory/out', data)
  return response.data
}

export async function inventoryAdjust(data: InventoryAdjustDTO): Promise<R<void>> {
  const response = await client.post<R<void>>('/inventory/adjust', data)
  return response.data
}

export async function getInventoryAlerts(): Promise<R<InventoryVO[]>> {
  const response = await client.get<R<InventoryVO[]>>('/inventory/alerts')
  return response.data
}

export async function getWarehouseList(): Promise<R<WarehouseVO[]>> {
  const response = await client.get<R<WarehouseVO[]>>('/warehouse')
  return response.data
}

export async function getWarehouseById(id: number): Promise<R<WarehouseVO>> {
  const response = await client.get<R<WarehouseVO>>(`/warehouse/${id}`)
  return response.data
}

export async function createWarehouse(data: WarehouseVO): Promise<R<number>> {
  const response = await client.post<R<number>>('/warehouse', data)
  return response.data
}

export async function updateWarehouse(data: WarehouseVO): Promise<R<void>> {
  const response = await client.put<R<void>>('/warehouse', data)
  return response.data
}

export async function deleteWarehouse(id: number): Promise<R<void>> {
  const response = await client.delete<R<void>>(`/warehouse/${id}`)
  return response.data
}
