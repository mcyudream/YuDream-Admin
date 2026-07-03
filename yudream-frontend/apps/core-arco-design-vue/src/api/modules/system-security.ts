import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type CredentialStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'
export type OAuthClientAuthMethod = 'CLIENT_SECRET_BASIC' | 'CLIENT_SECRET_POST' | 'NONE'
export type OAuthGrantType = 'AUTHORIZATION_CODE' | 'REFRESH_TOKEN' | 'CLIENT_CREDENTIALS'
export type OAuthRegistrationStatus = 'ACTIVE' | 'DISABLED'

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

export interface OAuthClient {
  id: number | string
  clientId: string
  clientName: string
  authMethod: OAuthClientAuthMethod
  grantTypes: OAuthGrantType[]
  redirectUris: string[]
  scopes: string[]
  accessTokenTtlSeconds: number
  refreshTokenTtlSeconds: number
  status: OAuthRegistrationStatus
  createTime?: string
  updateTime?: string
}

export interface OAuthClientPayload {
  clientId: string
  clientName: string
  authMethod: OAuthClientAuthMethod
  grantTypes: OAuthGrantType[]
  redirectUris: string[]
  scopes: string[]
  accessTokenTtlSeconds: number
  refreshTokenTtlSeconds: number
  status: OAuthRegistrationStatus
}

export interface OAuthClientCreateResult {
  client: OAuthClient
  clientSecret: string
}

export interface OAuthProvider {
  id: number | string
  code: string
  name: string
  issuerUri?: string
  authorizationUri?: string
  tokenUri?: string
  userInfoUri?: string
  clientId?: string
  authMethod: OAuthClientAuthMethod
  scopes: string[]
  redirectUri?: string
  status: OAuthRegistrationStatus
  createTime?: string
  updateTime?: string
}

export interface OAuthProviderPayload {
  code: string
  name: string
  issuerUri?: string
  authorizationUri?: string
  tokenUri?: string
  userInfoUri?: string
  clientId?: string
  clientSecret?: string
  authMethod: OAuthClientAuthMethod
  scopes: string[]
  redirectUri?: string
  status: OAuthRegistrationStatus
}

export interface PasskeyCredential {
  id: number | string
  userId: number | string
  credentialId: string
  deviceName?: string
  status: CredentialStatus
  signCount?: number
  lastUsedTime?: string
  createTime?: string
  updateTime?: string
}

export default {
  policy: () => systemClient.get<unknown, ApiResponse<ApiSecurityPolicy>>('api/system/security/policy'),
  updatePolicy: (data: ApiSecurityPolicy) => systemClient.put<unknown, ApiResponse<ApiSecurityPolicy>>('api/system/security/policy', data),
  pageApiKeys: (params: ApiKeyPageParams) => systemClient.get<unknown, ApiResponse<PageResult<ApiKeyCredential>>>('api/system/security/api-keys', { params }),
  createApiKey: (data: ApiKeyCreatePayload) => systemClient.post<unknown, ApiResponse<ApiKeyCreateResult>>('api/system/security/api-keys', data),
  revokeApiKey: (id: number | string) => systemClient.post<unknown, ApiResponse<ApiKeyCredential>>(`api/system/security/api-keys/${id}/revoke`),
  oauthClients: () => systemClient.get<unknown, ApiResponse<OAuthClient[]>>('api/system/security/oauth/clients'),
  createOAuthClient: (data: OAuthClientPayload) => systemClient.post<unknown, ApiResponse<OAuthClientCreateResult>>('api/system/security/oauth/clients', data),
  updateOAuthClient: (id: number | string, data: OAuthClientPayload) => systemClient.put<unknown, ApiResponse<OAuthClient>>(`api/system/security/oauth/clients/${id}`, data),
  disableOAuthClient: (id: number | string) => systemClient.delete<unknown, ApiResponse<void>>(`api/system/security/oauth/clients/${id}`),
  oauthProviders: () => systemClient.get<unknown, ApiResponse<OAuthProvider[]>>('api/system/security/oauth/providers'),
  createOAuthProvider: (data: OAuthProviderPayload) => systemClient.post<unknown, ApiResponse<OAuthProvider>>('api/system/security/oauth/providers', data),
  updateOAuthProvider: (id: number | string, data: OAuthProviderPayload) => systemClient.put<unknown, ApiResponse<OAuthProvider>>(`api/system/security/oauth/providers/${id}`, data),
  disableOAuthProvider: (id: number | string) => systemClient.delete<unknown, ApiResponse<void>>(`api/system/security/oauth/providers/${id}`),
  passkeys: (userId: number | string) => systemClient.get<unknown, ApiResponse<PasskeyCredential[]>>('api/system/security/passkeys', { params: { userId } }),
  revokePasskey: (id: number | string) => systemClient.post<unknown, ApiResponse<PasskeyCredential>>(`api/system/security/passkeys/${id}/revoke`),
}
