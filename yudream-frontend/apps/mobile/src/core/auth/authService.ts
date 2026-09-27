/**
 * 登录/登出。端点与后端 /api/user/login、/api/user/token/refresh 对齐。
 * 注意 userId 等 Long 字段经全局 Jackson 序列化为字符串。
 */
import { ApiError, request } from '@/core/api/httpClient';
import { clearTokens, loadTokens, saveTokens } from '@/core/auth/tokenStore';
import { getServerUrl } from '@/core/config/env';

export interface LoginResult {
  token: string;
  refreshToken: string;
  dualTokenEnabled: boolean;
  expiresIn: number;
  userId: string;
  username: string;
  nickname: string;
  email: string | null;
  avatar: string | null;
}

interface ResultEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export async function login(username: string, password: string): Promise<LoginResult> {
  const res = await fetch(`${getServerUrl()}/api/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const envelope = (await res.json()) as ResultEnvelope<LoginResult>;
  if (!res.ok || envelope.code !== 200) {
    throw new ApiError(envelope.message ?? '登录失败', envelope.code ?? res.status, res.status);
  }
  await saveTokens({
    accessToken: envelope.data.token,
    refreshToken: envelope.data.refreshToken,
  });
  return envelope.data;
}

export async function logout(): Promise<void> {
  await clearTokens();
}

export async function isAuthenticated(): Promise<boolean> {
  return (await loadTokens()) !== null;
}

/** 供插件/桥层取当前 access token；无登录态返回 null，由调用方决定降级。 */
export async function currentAccessToken(): Promise<string | null> {
  return (await loadTokens())?.accessToken ?? null;
}

/** 供上层在 401 时跳转登录 */
export function isAuthError(e: unknown): boolean {
  return e instanceof ApiError && e.isUnauthorized;
}

export { request };
