/**
 * 双 token 存储（按域隔离）：后端既有双 token 机制（access + refresh）。
 * 敏感值一律走安全存储桥；刷新旋转由 authService 负责。
 * 兼容单 token 站点（dualTokenEnabled=false，refreshToken 为 null）：跳过存储，
 * 读取时回落空串——刷新端点会自然失败，会话仍以长效 access token 维持。
 */
import { bridges } from '@/bridges';

const accessKey = (domainId: string) => `token.${domainId}.access`;
const refreshKey = (domainId: string) => `token.${domainId}.refresh`;

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export async function loadTokens(domainId: string): Promise<TokenPair | null> {
  const [accessToken, refreshToken] = await Promise.all([
    bridges.secureStorage.get(accessKey(domainId)),
    bridges.secureStorage.get(refreshKey(domainId)),
  ]);
  if (!accessToken) {
    return null;
  }
  return { accessToken, refreshToken: refreshToken ?? '' };
}

export async function saveTokens(
  domainId: string,
  pair: { accessToken: string; refreshToken: string | null },
): Promise<void> {
  await bridges.secureStorage.set(accessKey(domainId), pair.accessToken);
  if (pair.refreshToken) {
    await bridges.secureStorage.set(refreshKey(domainId), pair.refreshToken);
  }
}

export async function clearTokens(domainId: string): Promise<void> {
  await bridges.secureStorage.remove(accessKey(domainId));
  await bridges.secureStorage.remove(refreshKey(domainId));
}

/** 删除整个域时清空其全部凭据。 */
export async function clearAllTokens(domainIds: string[]): Promise<void> {
  await Promise.all(domainIds.map((id) => clearTokens(id)));
}
