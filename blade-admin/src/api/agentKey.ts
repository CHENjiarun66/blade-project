import client from './client'

export type AgentKeyOutletScopeType = 'ALL' | 'ASSIGNED' | 'NONE'

/** Key 已绑定档口摘要；ALL 范围只会出现被标记为默认档口的那一条。 */
export interface AgentKeyOutletSummary {
  id: number
  outletCode: string
  outletName: string
  status: number
  isDefault: boolean
}

/** 管理者可配置档口选项：仅受租户约束，不受当前管理账号自身的档口范围限制。 */
export interface AgentOutletOption {
  id: number
  outletCode: string
  outletName: string
  status: number
  tenantDefault: boolean
}

export interface AgentKeyView {
  id: number
  name: string
  keyPrefix: string
  scopes: string[]
  outletScopeType: AgentKeyOutletScopeType
  defaultOutletId: number | null
  outlets: AgentKeyOutletSummary[]
  status: number
  expiresTime?: string
  expired: boolean
  lastUsedTime?: string
  lastUsedIp?: string
  createdByUserId?: number
  disabledTime?: string
  rotatedFromKeyId?: number
  createTime: string
}

export interface AgentKeyCredential {
  id: number
  name: string
  agentKey: string
  keyPrefix: string
  scopes: string[]
  outletScopeType: AgentKeyOutletScopeType
  defaultOutletId: number | null
  outlets: AgentKeyOutletSummary[]
  expiresTime: string
  rotatedFromKeyId?: number
}

export interface CreateAgentKeyRequest {
  name: string
  scopes: string[]
  expiresInDays: number
  outletScopeType?: AgentKeyOutletScopeType | null
  outletIds?: number[] | null
  defaultOutletId?: number | null
}

export interface RotateAgentKeyRequest {
  scopes: string[]
  expiresInDays: number
  outletScopeType?: AgentKeyOutletScopeType | null
  outletIds?: number[] | null
  defaultOutletId?: number | null
}

export const getAgentKeys = () => client.get<AgentKeyView[]>('/system/agent-keys')
export const getAgentKeyScopes = () => client.get<string[]>('/system/agent-keys/scopes')
export const getAgentKeyOutletOptions = () => client.get<AgentOutletOption[]>('/system/agent-keys/outlets')
export const createAgentKey = (data: CreateAgentKeyRequest) =>
  client.post<AgentKeyCredential>('/system/agent-keys', data)
export const rotateAgentKey = (id: number, data: RotateAgentKeyRequest) =>
  client.post<AgentKeyCredential>(`/system/agent-keys/${id}/rotate`, data)
export const disableAgentKey = (id: number) =>
  client.post(`/system/agent-keys/${id}/disable`)
