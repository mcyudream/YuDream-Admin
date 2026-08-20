import type {
  QqSandboxCreateSessionPayload,
  QqSandboxMessage,
  QqSandboxPreset,
  QqSandboxSendMessagePayload,
  QqSandboxSession,
} from './platform-devtools-qq-sandbox.types'
import type { ApiResponse } from './system-client'
import { toBackendAssetUrl } from '@/utils/backend-url'
import systemClient from './system-client'

const basePath = 'api/platform/plugin-devtools/qq-sandbox'

export default {
  presets: () =>
    systemClient.get<unknown, ApiResponse<QqSandboxPreset[]>>(`${basePath}/presets`),

  createSession: (data: QqSandboxCreateSessionPayload) =>
    systemClient.post<unknown, ApiResponse<QqSandboxSession>>(`${basePath}/sessions`, data),

  sendMessage: (sessionId: string, data: QqSandboxSendMessagePayload) =>
    systemClient.post<unknown, ApiResponse<QqSandboxMessage>>(`${basePath}/sessions/${sessionId}/messages`, data),

  deleteSession: (sessionId: string) =>
    systemClient.delete<unknown, ApiResponse<unknown>>(`${basePath}/sessions/${sessionId}`),

  streamUrl: (sessionId: string) =>
    toBackendAssetUrl(`/api/platform/plugin-devtools/qq-sandbox/sessions/${sessionId}/events/stream`),
}

export type * from './platform-devtools-qq-sandbox.types'
