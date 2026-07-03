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

export default {
  get: () => systemClient.get<unknown, ApiResponse<UserProfile>>('api/user/me/profile'),
  update: (data: UserProfilePayload) => systemClient.put<unknown, ApiResponse<UserProfile>>('api/user/me/profile', data),
  uploadAvatar: (data: FormData) => systemClient.post<unknown, ApiResponse<UserProfile>>('api/user/me/avatar', data),
}
