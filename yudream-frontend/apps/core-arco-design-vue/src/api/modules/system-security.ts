import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type CredentialStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface ApiSecurityPolicy {
  id?: number | string
  apiEncryptionEnabled: boolean
  dualTokenEnabled: boolean
  apiKeyEnabled: boolean
  passkeyEnabled: boolean
  oauthServerEnabled: boolean
  oauthClientEnabled: boolean
  accessTokenTtlSeconds: number
  refreshTokenTtlSeconds: number
  refreshRotationEnabled: boolean
  updateTime?: string
}

export interface ApiKeyCredential {
  id: number | string
  name: string
  keyPrefix: string
  maskedValue: string
  creatorUserId: number | string
  permissions: string[]
  expireTime?: string
  status: CredentialStatus
  lastUsedTime?: string
  createTime?: string
  updateTime?: string
}

export interface ApiKeyCreatePayload {
  name: string
  permissions: string[]
  expireTime?: string
}

export interface ApiKeyCreateResult {
  credential: ApiKeyCredential
  plaintext: string
}

export interface ApiKeyPageParams {
  page: number
  size: number
  keyword?: string
}

export default {
  policy: () => systemClient.get<unknown, ApiResponse<ApiSecurityPolicy>>('api/system/security/policy'),
  updatePolicy: (data: ApiSecurityPolicy) => systemClient.put<unknown, ApiResponse<ApiSecurityPolicy>>('api/system/security/policy', data),
  pageApiKeys: (params: ApiKeyPageParams) => systemClient.get<unknown, ApiResponse<PageResult<ApiKeyCredential>>>('api/system/security/api-keys', { params }),
  createApiKey: (data: ApiKeyCreatePayload) => systemClient.post<unknown, ApiResponse<ApiKeyCreateResult>>('api/system/security/api-keys', data),
  revokeApiKey: (id: number | string) => systemClient.post<unknown, ApiResponse<ApiKeyCredential>>(`api/system/security/api-keys/${id}/revoke`),
}
