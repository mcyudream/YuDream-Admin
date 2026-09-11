import type { LoginData } from './user'
import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface ExternalLoginAuthorization {
  authorizationUrl: string
  state: string
}

export interface ExternalLoginBindRequiredResult {
  outcome: 'BIND_REQUIRED'
  bindingToken: string
  providerCode: string
  type: string
  nickname?: string
  avatarUrl?: string
}

export interface ExternalLoginBoundResult {
  outcome: 'BOUND'
  providerCode: string
  type: string
  nickname?: string
  avatarUrl?: string
}

export interface ExternalLoginSucceededResult {
  outcome: 'LOGIN'
  session: LoginData
}

export type ExternalLoginCallbackResult = ExternalLoginSucceededResult | ExternalLoginBindRequiredResult | ExternalLoginBoundResult

export default {
  authorize: (providerCode: string, type: string) => systemClient.get<unknown, ApiResponse<ExternalLoginAuthorization>>(`api/external-login/${providerCode}/${type}/authorize`),
  callback: (providerCode: string, type: string, params: { code: string, state: string }) => systemClient.get<unknown, ApiResponse<ExternalLoginCallbackResult>>(`api/external-login/${providerCode}/${type}/callback`, { params, skipTokenRefresh: true }),
  // 统一回调：provider/type 可缺省，由宿主按 state 票据反查；code 与 CAS ticket 二选一
  callbackByState: (params: { code?: string, ticket?: string, state: string, provider?: string, type?: string }) => systemClient.get<unknown, ApiResponse<ExternalLoginCallbackResult>>('api/external-login/callback', { params, skipTokenRefresh: true }),
}
