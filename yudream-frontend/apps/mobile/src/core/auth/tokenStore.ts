/**
 * 双 token 存储：后端既有双 token 机制（access + refresh）的移动端落地。
 * 敏感值一律走安全存储桥；刷新旋转由 authService 负责。
 */
import { bridges } from '@/bridges';

const KEY_ACCESS = 'token.access';
const KEY_REFRESH = 'token.refresh';

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export async function loadTokens(): Promise<TokenPair | null> {
  const [accessToken, refreshToken] = await Promise.all([
    bridges.secureStorage.get(KEY_ACCESS),
    bridges.secureStorage.get(KEY_REFRESH),
  ]);
  if (!accessToken || !refreshToken) {
    return null;
  }
  return { accessToken, refreshToken };
}

export async function saveTokens(pair: TokenPair): Promise<void> {
  await bridges.secureStorage.set(KEY_ACCESS, pair.accessToken);
  await bridges.secureStorage.set(KEY_REFRESH, pair.refreshToken);
}

export async function clearTokens(): Promise<void> {
  await bridges.secureStorage.remove(KEY_ACCESS);
  await bridges.secureStorage.remove(KEY_REFRESH);
}
