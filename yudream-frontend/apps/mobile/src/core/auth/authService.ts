/**
 * 登录/登出/账户摘要。端点与后端 /api/user/login、/api/user/token/refresh、
 * /api/user/me 对齐。所有操作作用于激活域。
 * 注意 userId 等 Long 字段经全局 Jackson 序列化为字符串。
 */
import { ApiError } from '@/core/api/httpClient';
import { clearTokens, loadTokens, saveTokens } from '@/core/auth/tokenStore';
import {
  getActiveDomain,
  updateDomainAccount,
  type DomainAccount,
} from '@/core/domains/store';
import type { ResultEnvelope } from '@/core/api/envelope';

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

export interface MeInfo {
  id: string;
  username: string;
  nickname: string;
  avatar: string | null;
  /** 管理员（决定「域管理」入口可见性） */
  admin?: boolean;
}

export async function login(username: string, password: string): Promise<LoginResult> {
  const domain = getActiveDomain();
  if (!domain) {
    throw new ApiError('尚未接入任何站点域', 0, 0);
  }
  const res = await fetch(`${domain.serverUrl}/api/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const envelope = (await res.json()) as ResultEnvelope<LoginResult>;
  if (!res.ok || envelope.code !== 200) {
    throw new ApiError(envelope.message ?? '登录失败', envelope.code ?? res.status, res.status);
  }
  await saveTokens(domain.id, {
    accessToken: envelope.data.token,
    refreshToken: envelope.data.refreshToken,
  });
  const account: DomainAccount = {
    userId: envelope.data.userId,
    username: envelope.data.username,
    nickname: envelope.data.nickname || envelope.data.username,
    avatar: envelope.data.avatar ?? null,
  };
  await updateDomainAccount(domain.id, account);
  // 登录后立即取 /me 补齐管理员标志（login 响应不含角色信息）
  void fetchMe().catch(() => undefined);
  return envelope.data;
}

export async function logout(): Promise<void> {
  const domain = getActiveDomain();
  if (!domain) {
    return;
  }
  await clearTokens(domain.id);
  await updateDomainAccount(domain.id, null);
}

export async function isAuthenticated(): Promise<boolean> {
  const domain = getActiveDomain();
  if (!domain) {
    return false;
  }
  return (await loadTokens(domain.id)) !== null;
}

/** 拉取当前账户摘要（登录后调用）。 */
export async function fetchMe(): Promise<MeInfo | null> {
  const domain = getActiveDomain();
  if (!domain) {
    return null;
  }
  try {
    const { request } = await import('@/core/api/httpClient');
    const me = await request<MeInfo>('/api/user/me');
    await updateDomainAccount(domain.id, {
      userId: me.id,
      username: me.username,
      nickname: me.nickname || me.username,
      avatar: me.avatar ?? null,
      admin: me.admin ?? false,
    });
    return me;
  } catch {
    // /api/user/me 不可用时退回登录时缓存的摘要
    return domain.account
      ? {
          id: domain.account.userId,
          username: domain.account.username,
          nickname: domain.account.nickname,
          avatar: domain.account.avatar,
        }
      : null;
  }
}

/** 供插件/桥层取当前 access token；无登录态返回 null，由调用方决定降级。 */
export async function currentAccessToken(): Promise<string | null> {
  const domain = getActiveDomain();
  if (!domain) {
    return null;
  }
  return (await loadTokens(domain.id))?.accessToken ?? null;
}

/** 供上层在 401 时跳转登录 */
export function isAuthError(e: unknown): boolean {
  return e instanceof ApiError && e.isUnauthorized;
}
