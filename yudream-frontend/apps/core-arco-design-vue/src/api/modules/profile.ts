import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface UserProfile {
  id: string
  username: string
  nickname?: string
  email?: string
  emailVerified: boolean
  phone?: string
  qq?: string
  avatar?: string
  avatarFileId?: string
  createTime?: string
  updateTime?: string
}

export interface UserProfilePayload {
  username?: string
  nickname?: string
  email?: string
  phone?: string
  qq?: string
}

export type PasskeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface PasskeyCredential {
  id: string
  userId: string
  credentialId: string
  deviceName?: string
  status: PasskeyStatus
  signCount: number
  lastUsedTime?: string
  createTime?: string
  updateTime?: string
}

export interface PasskeyRegistrationOptions {
  requestJson: string
  publicKeyJson: string
}

export interface PasskeyRegistrationFinishPayload {
  deviceName?: string
  requestJson: string
  responseJson: string
}

export type CredentialStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface ApiKeyCredential {
  id: string
  name: string
  keyPrefix: string
  maskedValue: string
  creatorUserId: string
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
  get: () => systemClient.get<unknown, ApiResponse<UserProfile>>('api/user/me/profile'),
  update: (data: UserProfilePayload) => systemClient.put<unknown, ApiResponse<UserProfile>>('api/user/me/profile', data),
  uploadAvatar: (data: FormData) => systemClient.post<unknown, ApiResponse<UserProfile>>('api/user/me/avatar', data),
  passkeys: () => systemClient.get<unknown, ApiResponse<PasskeyCredential[]>>('api/user/me/passkeys'),
  startPasskeyRegistration: () => systemClient.post<unknown, ApiResponse<PasskeyRegistrationOptions>>('api/user/me/passkeys/registration/options'),
  finishPasskeyRegistration: (data: PasskeyRegistrationFinishPayload) => systemClient.post<unknown, ApiResponse<PasskeyCredential>>('api/user/me/passkeys/registration', data),
  revokePasskey: (id: string) => systemClient.post<unknown, ApiResponse<PasskeyCredential>>(`api/user/me/passkeys/${id}/revoke`),
  apiKeys: (params: ApiKeyPageParams) => systemClient.get<unknown, ApiResponse<PageResult<ApiKeyCredential>>>('api/user/me/api-keys', { params }),
  createApiKey: (data: ApiKeyCreatePayload) => systemClient.post<unknown, ApiResponse<ApiKeyCreateResult>>('api/user/me/api-keys', data),
  revokeApiKey: (id: string) => systemClient.post<unknown, ApiResponse<ApiKeyCredential>>(`api/user/me/api-keys/${id}/revoke`),
}
