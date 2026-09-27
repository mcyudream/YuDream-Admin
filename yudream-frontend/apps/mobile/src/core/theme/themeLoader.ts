/**
 * 主题加载（按域隔离）：缓存先绘（上次激活主题立刻生效，避免闪白/换肤闪烁），
 * 随后拉取该域激活的移动主题覆盖层。端点落地前保持安静降级。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

const cacheKey = (domainId: string) => `theme.mobile.override.${domainId}`;

/** 返回启动时立即可用的缓存覆盖层；无则 null。 */
export async function loadCachedThemeOverride(domainId: string): Promise<unknown | null> {
  const raw = await AsyncStorage.getItem(cacheKey(domainId));
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    await AsyncStorage.removeItem(cacheKey(domainId));
    return null;
  }
}

/**
 * 拉取当前激活的移动主题 token 覆盖层。
 * GET /api/mobile/theme/active 为匿名端点（登录前首页也要按主题渲染）。
 * 404/能力关闭均视为"无远程主题"，沿用内置。
 */
export async function fetchThemeOverride(domainId: string, serverUrl: string): Promise<unknown | null> {
  try {
    const res = await fetch(`${serverUrl}/api/mobile/theme/active`, {
      headers: { Accept: 'application/json' },
    });
    if (res.ok) {
      const body = (await res.json()) as { code?: number; data?: unknown };
      if (body.code === 200 && body.data) {
        await AsyncStorage.setItem(cacheKey(domainId), JSON.stringify(body.data));
        return body.data;
      }
    }
  } catch {
    // 网络失败走缓存
  }
  return loadCachedThemeOverride(domainId);
}

/** 删除域时清空其主题缓存。 */
export async function clearThemeOverride(domainId: string): Promise<void> {
  await AsyncStorage.removeItem(cacheKey(domainId));
}
