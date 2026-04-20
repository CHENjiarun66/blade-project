import client from './client'

// 响应数据结构（与后端 R<T> 格式一致）
export interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

export interface ProductColor {
  id: number
  colorCode: string
  colorName: string
  status?: number
}

export interface ProductSize {
  id: number
  sizeCode: string
  sort: number
  status?: number
}

export interface ProductSku {
  id: number
  skuCode: string
  colorId: number
  colorName: string
  sizeId: number
  sizeName: string
  price: number
  costPrice: number
  barCode?: string
  status: number
}

export interface ProductVO {
  id: number
  productCode: string
  name: string
  categoryId: number
  categoryName: string
  supplierId?: number
  supplierName?: string
  unit: string
  costPrice?: number
  wholesalePrice?: number
  weight?: number
  description: string
  imageUrl?: string
  remark?: string
  status: number
  colors: ProductColor[]
  sizes: ProductSize[]
  skus: ProductSku[]
  createTime?: string
  updateTime?: string
}

export interface ProductPageResponse {
  records: ProductVO[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ProductCreateDTO {
  productCode: string
  name: string
  categoryId?: number
  supplierId?: number
  unit?: string
  costPrice?: number
  wholesalePrice?: number
  weight?: number
  description?: string
  imageUrl?: string
  remark?: string
  status?: number
  colorIds: number[]
  sizeIds: number[]
}

// 商品列表
export function getProductPage(params?: { current?: number; size?: number; keyword?: string; categoryId?: number; status?: number }) {
  return client.get<ApiResponse<ProductPageResponse>>('/products', { params }) as any
}

// 商品详情
export function getProductById(id: number) {
  return client.get<ApiResponse<ProductVO>>(`/products/${id}`) as any
}

// 创建商品
export function createProduct(data: ProductCreateDTO) {
  return client.post<ApiResponse<number>>('/products', data) as any
}

// 更新商品
export function updateProduct(data: ProductCreateDTO & { id: number }) {
  return client.put<ApiResponse<void>>('/products', data) as any
}

// 删除商品
export function deleteProduct(id: number) {
  return client.delete(`/products/${id}`) as any
}

// 获取所有颜色
export function getAllColors() {
  return client.get<ApiResponse<ProductColor[]>>('/products/colors') as any
}

// 获取所有尺码
export function getAllSizes() {
  return client.get<ApiResponse<ProductSize[]>>('/products/sizes') as any
}

// 商品分类
export interface ProductCategory {
  id: number
  categoryName: string
  parentId: number
  sort: number
  status: number
}

// 获取所有分类
export function getAllCategories() {
  return client.get<ApiResponse<ProductCategory[]>>('/product-categories') as any
}

// ========== 颜色 CRUD ==========

export interface ColorCreateDTO {
  colorCode: string
  colorName: string
  status?: number
}

export interface ColorUpdateDTO {
  id: number
  colorCode: string
  colorName: string
  status?: number
}

// 创建颜色
export function createColor(data: ColorCreateDTO) {
  return client.post<ApiResponse<number>>('/products/colors', data) as any
}

// 更新颜色
export function updateColor(data: ColorUpdateDTO) {
  return client.put('/products/colors', data) as any
}

// 删除颜色
export function deleteColor(id: number) {
  return client.delete(`/products/colors/${id}`) as any
}

// ========== 尺码 CRUD ==========

export interface SizeCreateDTO {
  sizeCode: string
  sort?: number
  status?: number
}

export interface SizeUpdateDTO {
  id: number
  sizeCode: string
  sort?: number
  status?: number
}

// 创建尺码
export function createSize(data: SizeCreateDTO) {
  return client.post<ApiResponse<number>>('/products/sizes', data) as any
}

// 更新尺码
export function updateSize(data: SizeUpdateDTO) {
  return client.put('/products/sizes', data) as any
}

// 删除尺码
export function deleteSize(id: number) {
  return client.delete(`/products/sizes/${id}`) as any
}

// ========== 分类 CRUD ==========

export interface CategoryCreateDTO {
  categoryName: string
  parentId?: number
  sort?: number
  status?: number
}

export interface CategoryUpdateDTO {
  id: number
  categoryName: string
  parentId?: number
  sort?: number
  status?: number
}

// 创建分类
export function createCategory(data: CategoryCreateDTO) {
  return client.post<ApiResponse<number>>('/product-categories', data) as any
}

// 更新分类
export function updateCategory(data: CategoryUpdateDTO) {
  return client.put('/product-categories', data) as any
}

// 删除分类
export function deleteCategory(id: number) {
  return client.delete(`/product-categories/${id}`) as any
}
