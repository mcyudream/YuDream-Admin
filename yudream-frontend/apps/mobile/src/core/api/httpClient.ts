/**
 * HTTP 客户端：对齐 web 侧双 token 语义——
 * 401 先刷新再重试一次；刷新失败统一登出。未登录/失效必须按 401 处理，
 * 不得降级成业务错误（否则动态内容失败后会呈现空白页）。
 */
import { getServerUrl } from '@/core/config/env';
import { clearTokens, loadTokens, saveTokens } from '@/core/auth/tokenStore';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: number,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }
}

interface ResultEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  /** 内部递归标记，防止 401 -> 刷新 -> 重试 死循环 */
  retried?: boolean;
}

let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const tokens = await loadTokens();
      if (!tokens) {
        return false;
      }
      const res = await fetch(`${getServerUrl()}/api/user/token/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: tokens.refreshToken }),
      });
      if (!res.ok) {
        return false;
      }
      const envelope = (await res.json()) as ResultEnvelope<{
        token: string;
        refreshToken: string;
      }>;
      if (envelope.code !== 200) {
        return false;
      }
      await saveTokens({
        accessToken: envelope.data.token,
        refreshToken: envelope.data.refreshToken,
      });
      return true;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const tokens = await loadTokens();
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.headers,
  };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (tokens) {
    headers.Authorization = `Bearer ${tokens.accessToken}`;
  }

  const res = await fetch(`${getServerUrl()}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (res.status === 401 && !options.retried) {
    if (await tryRefresh()) {
      return request<T>(path, { ...options, retried: true });
    }
    await clearTokens();
    throw new ApiError('登录状态已失效，请重新登录', 401, 401);
  }

  if (!res.ok) {
    throw new ApiError(`请求失败（HTTP ${res.status}）`, res.status, res.status);
  }

  const envelope = (await res.json()) as ResultEnvelope<T>;
  if (envelope.code !== 200) {
    throw new ApiError(envelope.message ?? '请求失败', envelope.code, res.status);
  }
  return envelope.data;
}
