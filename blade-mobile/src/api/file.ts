import client from './client'
import type { R } from '@/types/auth'

export interface FileUploadVO {
  id: number
  originalName: string
  contentType: string
  fileSize: number
  url: string
}

export function filePreviewUrl(id: number | string) {
  return `/api/files/${id}/preview`
}

export async function uploadFile(file: File, businessType: string, businessId?: number): Promise<R<FileUploadVO>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('businessType', businessType)
  if (businessId) {
    formData.append('businessId', String(businessId))
  }
  const response = await client.post<R<FileUploadVO>>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}
