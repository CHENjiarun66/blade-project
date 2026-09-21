// 档口选项结构化契约（与后端 OutletOptionsVO 对齐）
export interface OutletOptionVO {
  id: number
  outletCode: string
  outletName: string
  status: number
}

export interface OutletOptionsVO {
  scopeType: 'ALL' | 'ASSIGNED' | 'NONE'
  peopleScope: 'ALL_USERS' | 'SELF' | string
  /** 单档口锁定时前端只读展示 */
  locked: boolean
  defaultOutletId: number | null
  items: OutletOptionVO[]
}
