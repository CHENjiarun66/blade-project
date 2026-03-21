// ==================== Product 类型 ====================

export interface ProductVO {
  id: number
  name: string
  category: string
  price: number
  remark?: string
  colors: ColorVO[]
  sizes: SizeVO[]
  skus: SkuVO[]
  createTime: string
  updateTime: string
}

export interface ColorVO {
  id: number
  name: string
  colorCode: string
}

export interface SizeVO {
  id: number
  name: string
  sort: number
}

export interface SkuVO {
  id: number
  skuCode: string
  productId: number
  colorId: number
  sizeId: number
  colorName: string
  sizeName: string
  price: number
}

export interface ProductCreateDTO {
  name: string
  category: string
  price?: number
  remark?: string
  colorIds: number[]
  sizeIds: number[]
}

export interface ProductUpdateDTO extends ProductCreateDTO {
  id: number
}

export interface ProductPageDTO {
  current?: number
  size?: number
  name?: string
  category?: string
}
