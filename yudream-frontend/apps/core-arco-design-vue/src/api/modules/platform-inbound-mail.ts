import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface InboundMailPageParams {
  page: number
  size: number
  keyword?: string
}

export interface InboundMailSummary {
  uid: string
  subject: string
  from: string
  to?: string
  receivedAt?: string
  seen: boolean
  hasAttachment: boolean
}

export interface InboundMailAttachment {
  filename: string
  contentType?: string
  size?: number | string
  inline?: boolean
}

export interface InboundMailDetail extends InboundMailSummary {
  cc?: string
  textBody?: string
  htmlBody?: string
  attachments: InboundMailAttachment[]
}

export default {
  page: (params: InboundMailPageParams) =>
    systemClient.get<unknown, ApiResponse<PageResult<InboundMailSummary>>>('api/platform/inbound-mail/messages', { params }),
  detail: (uid: string) =>
    systemClient.get<unknown, ApiResponse<InboundMailDetail>>(`api/platform/inbound-mail/messages/${uid}`),
}
