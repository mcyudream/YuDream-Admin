import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface SystemCommand { pluginCode: string; code: string; command: string; name: string; permission: string; description: string; allowAnonymous: boolean }
export interface QqBindingPolicy { requireBoundQq: boolean; lockProfileQq: boolean }
export interface QqBindingCode { code: string; expiresAt: string }

export interface MessagingBindingTarget {
  connectionId: string
  name: string
  protocol?: string
  botName?: string
  appId?: string
  bound: boolean
}

export interface MessagingBindingCode {
  code: string
  expiresAt: string
  connectionId?: string
  connectionName?: string
  protocol?: string
  botName?: string
}

export default {
  list: () => systemClient.get<unknown, ApiResponse<SystemCommand[]>>('api/system/commands'),
  policy: () => systemClient.get<unknown, ApiResponse<QqBindingPolicy>>('api/system/commands/qq-binding-policy'),
  updatePolicy: (requireBoundQq: boolean) => systemClient.put<unknown, ApiResponse<QqBindingPolicy>>('api/system/commands/qq-binding-policy', null, { params: { requireBoundQq } }),
  issueQqBindingCode: (userId: string) => systemClient.post<unknown, ApiResponse<QqBindingCode>>('api/system/commands/qq-binding-codes', null, { params: { userId } }),
  listMessagingBindingTargets: (userId: string) => systemClient.get<unknown, ApiResponse<MessagingBindingTarget[]>>('api/system/commands/messaging-binding-targets', { params: { userId } }),
  issueMessagingBindingCode: (userId: string, connectionId: string) => systemClient.post<unknown, ApiResponse<MessagingBindingCode>>('api/system/commands/messaging-binding-codes', null, { params: { userId, connectionId } }),
}
