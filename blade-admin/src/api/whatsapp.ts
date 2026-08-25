import client from './client'

export interface ApiResult<T> { code: number; message: string; data: T }
export interface PageResult<T> { records: T[]; total: number; size: number; current: number; pages: number }
export interface WhatsappAccount { id: number; displayName?: string; accountRef: string; phoneNormalized?: string; lastSyncTime?: string; status: number }
export interface IssueSummary { open: number; resolved: number; missingPath: number; missingFile: number; image: number; video: number; audio: number; lastScanAt?: string; lastScanStatus?: string }
export interface CollectionIssue {
  id: number; accountId: number; conversationId?: number; conversationTitle?: string; customerId?: number; customerName?: string; conversationJid?: string
  messageId?: number; messageTime?: string; direction?: string; issueType: string; status: string
  severity: string; mediaType?: string; occurrenceCount: number; firstDetectedAt: string; lastDetectedAt: string; resolvedAt?: string
}
export interface IssueChat {
  accountId: number; conversationId?: number; conversationTitle?: string; customerId?: number; customerName?: string; conversationJid?: string; phoneNormalized?: string
  issueCount: number; imageCount: number; videoCount: number; audioCount: number; openCount: number; resolvedCount: number
  latestMessageTime?: string; lastDetectedAt?: string
}
export interface ScanJob {
  id: number; accountId: number; accountName?: string; scopeType: 'ACCOUNT' | 'CONTACT'; targetPhoneNormalized?: string; targetConversationJid?: string
  status: string; requestedAt: string; claimedAt?: string; completedAt?: string; resultBatchId?: number; errorSummary?: string
}
export interface BindingCandidate { id: number; contactId: number; contactName?: string; phoneNormalized?: string; customerId: number; customerName?: string; matchMethod: string; status: string; createTime: string }
export interface CollectorCredential { accountId: number; keyId: number; collectorKey: string; keyPrefix: string; scopes: string[] }
export interface WhatsappInsight {
  recommendationId: number; analysisId: number; customerId: number; customerName: string; status: string; dueAt?: string
  summary: string; preferences: Record<string, unknown>; intentStage: string; sentiment: string; churnRisk: string
  recommendedAction: string; confidence: number; evidenceMessageIds: number[]; model: string; analyzedAt: string; handledAt?: string; handleNote?: string
}
export interface InsightEvidence { messageId: number; sentAt: string; direction: string; excerpt: string }
export interface ArchiveChat {
  accountId: number; identityKey: string; displayName: string; phoneNormalized?: string; messageCount: number
  lastMessageId?: number; lastMessageAt?: string; lastDirection?: string; lastMessageType?: string; lastText?: string
}
export interface ArchiveMedia {
  id?: number; fileId?: number; mediaType: string; mimeType?: string; originalName?: string; fileSize?: number; caption?: string
  durationMs?: number; width?: number; height?: number; downloadStatus: string; issueType?: string
}
export interface ArchiveMessage {
  id: number; sentAt: string; direction: string; messageType: string; textContent?: string; status?: string; starred: boolean; media: ArchiveMedia[]
}

export const getWhatsappAccounts = () => client.get<any, ApiResult<WhatsappAccount[]>>('/whatsapp/accounts')
export const getIssueSummary = () => client.get<any, ApiResult<IssueSummary>>('/whatsapp/issues/summary')
export const getIssueChats = (params: Record<string, unknown>) => client.get<any, ApiResult<PageResult<IssueChat>>>('/whatsapp/issues/chats', { params })
export const getIssues = (params: Record<string, unknown>) => client.get<any, ApiResult<PageResult<CollectionIssue>>>('/whatsapp/issues', { params })
export const requestWhatsappScan = (accountId: number, target?: { phoneNormalized?: string; conversationJid?: string }) => client.post<any, ApiResult<ScanJob>>('/whatsapp/scan-jobs', null, {
  params: target ? { accountId, scopeType: 'CONTACT', targetPhoneNormalized: target.phoneNormalized, targetConversationJid: target.conversationJid } : { accountId, scopeType: 'ACCOUNT' },
})
export const getLatestWhatsappScan = () => client.get<any, ApiResult<ScanJob | null>>('/whatsapp/scan-jobs/latest')
export const getPendingBindings = () => client.get<any, ApiResult<BindingCandidate[]>>('/whatsapp/bindings/pending')
export const refreshBindingCandidates = () => client.post<any, ApiResult<BindingCandidate[]>>('/whatsapp/bindings/refresh')
export const decideBinding = (id: number, status: 'CONFIRMED' | 'REJECTED', note?: string) => client.put(`/whatsapp/bindings/${id}`, { status, note })
export const createCollector = (payload: { name: string; accountRef: string; displayName?: string; phoneNormalized?: string; sourceInstanceHash?: string }) =>
  client.post<any, ApiResult<CollectorCredential>>('/whatsapp/collectors', payload)
export const getWhatsappInsights = (params: Record<string, unknown>) => client.get<any, ApiResult<PageResult<WhatsappInsight>>>('/whatsapp/insights', { params })
export const getInsightEvidence = (analysisId: number) => client.get<any, ApiResult<InsightEvidence[]>>(`/whatsapp/insights/${analysisId}/evidence`)
export const decideWhatsappRecommendation = (id: number, status: 'ADOPTED' | 'DISMISSED' | 'COMPLETED', note?: string) =>
  client.put(`/whatsapp/recommendations/${id}`, { status, note })
export const getArchiveChats = (params: Record<string, unknown>) => client.get<any, ApiResult<PageResult<ArchiveChat>>>('/whatsapp/archive/chats', { params })
export const getArchiveMessages = (params: Record<string, unknown>) => client.get<any, ApiResult<PageResult<ArchiveMessage>>>('/whatsapp/archive/messages', { params })
