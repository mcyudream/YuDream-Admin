/**
 * 主题加载：缓存先绘（上次激活主题立刻生效，避免闪白/换肤闪烁），
 * 随后拉取服务端激活的移动主题覆盖层。端点落地前保持安静降级。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getServerUrl } from '@/core/config/env';

const KEY_THEME_OVERRIDE = 'theme.mobile.override';

/** 返回启动时立即可用的缓存覆盖层；无则 null。 */
export async function loadCachedThemeOverride(): Promise<unknown | null> {
  const raw = await AsyncStorage.getItem(KEY_THEME_OVERRIDE);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    await AsyncStorage.removeItem(KEY_THEME_OVERRIDE);
    return null;
  }
}

/**
 * 拉取当前激活的移动主题 token 覆盖层。
 * GET /api/mobile/theme/active 为匿名端点（登录前首页也要按主题渲染）。
 * 404/能力关闭均视为"无远程主题"，沿用内置。
 */
export async function fetchThemeOverride(): Promise<unknown | null> {
  try {
    const res = await fetch(`${getServerUrl()}/api/mobile/theme/active`, {
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) {
      return await loadCachedThemeOverride();
    }
    const body = (await res.json()) as { code?: number; data?: unknown };
    if (body.code !== 200 || !body.data) {
      return await loadCachedThemeOverride();
    }
    await AsyncStorage.setItem(KEY_THEME_OVERRIDE, JSON.stringify(body.data));
    return body.data;
  } catch {
    return await loadCachedThemeOverride();
  }
}
