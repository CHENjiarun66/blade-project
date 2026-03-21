import client from './client'
import type { R, PageResult } from '@/types/auth'
import type { ProductVO, ProductCreateDTO, ProductUpdateDTO, ProductPageDTO, ColorVO, SizeVO } from '@/types/product'

export async function getProductList(params: ProductPageDTO): Promise<R<PageResult<ProductVO>>> {
  const response = await client.get<R<PageResult<ProductVO>>>('/products', { params })
  return response.data
}

export async function getProductById(id: number): Promise<R<ProductVO>> {
  const response = await client.get<R<ProductVO>>(`/products/${id}`)
  return response.data
}

export async function createProduct(data: ProductCreateDTO): Promise<R<number>> {
  const response = await client.post<R<number>>('/products', data)
  return response.data
}

export async function updateProduct(data: ProductUpdateDTO): Promise<R<void>> {
  const response = await client.put<R<void>>('/products', data)
  return response.data
}

export async function deleteProduct(id: number): Promise<R<void>> {
  const response = await client.delete<R<void>>(`/products/${id}`)
  return response.data
}

export async function getColors(): Promise<R<ColorVO[]>> {
  const response = await client.get<R<ColorVO[]>>('/products/colors')
  return response.data
}

export async function getSizes(): Promise<R<SizeVO[]>> {
  const response = await client.get<R<SizeVO[]>>('/products/sizes')
  return response.data
}
