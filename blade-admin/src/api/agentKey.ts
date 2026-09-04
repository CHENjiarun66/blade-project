import client from './client'

export interface AgentKeyView {
  id: number
  name: string
  keyPrefix: string
  scopes: string[]
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
  expiresTime: string
  rotatedFromKeyId?: number
}

export interface CreateAgentKeyRequest {
  name: string
  scopes: string[]
  expiresInDays: number
}

export const getAgentKeys = () => client.get<AgentKeyView[]>('/system/agent-keys')
export const getAgentKeyScopes = () => client.get<string[]>('/system/agent-keys/scopes')
export const createAgentKey = (data: CreateAgentKeyRequest) =>
  client.post<AgentKeyCredential>('/system/agent-keys', data)
export const rotateAgentKey = (id: number, expiresInDays: number) =>
  client.post<AgentKeyCredential>(`/system/agent-keys/${id}/rotate`, { expiresInDays })
export const disableAgentKey = (id: number) =>
  client.post(`/system/agent-keys/${id}/disable`)
