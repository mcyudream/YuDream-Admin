import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface UserProfile {
  id: string | number
  username: string
  nickname?: string
  email?: string
  phone?: string
  qq?: string
  avatar?: string
  avatarFileId?: string | number
  createTime?: string
  updateTime?: string
}

export interface UserProfilePayload {
  nickname?: string
  email?: string
  phone?: string
  qq?: string
}

export type PasskeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface PasskeyCredential {
  id: string | number
  userId: string | number
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

export default {
  get: () => systemClient.get<unknown, ApiResponse<UserProfile>>('api/user/me/profile'),
  update: (data: UserProfilePayload) => systemClient.put<unknown, ApiResponse<UserProfile>>('api/user/me/profile', data),
  uploadAvatar: (data: FormData) => systemClient.post<unknown, ApiResponse<UserProfile>>('api/user/me/avatar', data),
  passkeys: () => systemClient.get<unknown, ApiResponse<PasskeyCredential[]>>('api/user/me/passkeys'),
  startPasskeyRegistration: () => systemClient.post<unknown, ApiResponse<PasskeyRegistrationOptions>>('api/user/me/passkeys/registration/options'),
  finishPasskeyRegistration: (data: PasskeyRegistrationFinishPayload) => systemClient.post<unknown, ApiResponse<PasskeyCredential>>('api/user/me/passkeys/registration', data),
  revokePasskey: (id: string | number) => systemClient.post<unknown, ApiResponse<PasskeyCredential>>(`api/user/me/passkeys/${id}/revoke`),
}
