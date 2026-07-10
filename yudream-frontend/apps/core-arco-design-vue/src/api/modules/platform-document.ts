import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type TemplateStatus = 'ACTIVE' | 'DISABLED'
export type GenerationStatus = 'SUCCESS' | 'FAILED'
export type WordGenerateData = Record<string, unknown>

export interface DocumentPageParams {
  page: number
  size: number
  keyword?: string
}

export interface WordTemplate {
  id: string
  name: string
  code: string
  templateFileId: string
  templateFileUrl?: string
  originalFilename?: string
  placeholders: Record<string, string>
  description?: string
  status: TemplateStatus
  createTime?: string
  updateTime?: string
}

export interface WordTemplatePayload {
  name: string
  code: string
  placeholders: Record<string, string>
  description?: string
  status: TemplateStatus
}

export interface WordGenerationRecord {
  id: string
  templateId: string
  templateCode: string
  outputFileId?: string
  outputFileUrl?: string
  outputFilename?: string
  data: WordGenerateData
  status: GenerationStatus
  errorMessage?: string
  operatorId?: string
  generatedAt?: string
}

export default {
  pageTemplates: (params: DocumentPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<WordTemplate>>>('api/platform/documents/word-templates', { params })
  },
  uploadTemplate: (file: File, meta: WordTemplatePayload) => {
    return systemClient.post<unknown, ApiResponse<WordTemplate>>('api/platform/documents/word-templates', templateForm(file, meta))
  },
  updateTemplate: (id: string, data: WordTemplatePayload) => {
    return systemClient.put<unknown, ApiResponse<WordTemplate>>(`api/platform/documents/word-templates/${id}`, data)
  },
  replaceTemplateFile: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return systemClient.put<unknown, ApiResponse<WordTemplate>>(`api/platform/documents/word-templates/${id}/file`, form)
  },
  disableTemplate: (id: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/documents/word-templates/${id}`)
  },
  enableTemplate: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/documents/word-templates/${id}/enable`)
  },
  generate: (id: string, data: WordGenerateData) => {
    return systemClient.post<unknown, ApiResponse<WordGenerationRecord>>(`api/platform/documents/word-templates/${id}/generate`, { data })
  },
  pageRecords: (params: DocumentPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<WordGenerationRecord>>>('api/platform/documents/word-records', { params })
  },
}

function templateForm(file: File, meta: WordTemplatePayload) {
  const form = new FormData()
  form.append('file', file)
  form.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }))
  return form
}
