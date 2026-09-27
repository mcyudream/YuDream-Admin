/**
 * 启动编排：环境 -> 桥 -> 主题缓存先绘 -> 插件快照 -> 鉴权判定。
 * 插件更新/主题刷新在后台并行，不阻塞首帧。
 */
import { initEnv } from '@/core/config/env';
import { installScriptManager } from '@/core/plugins/scriptManager';
import { syncPlugins, warmupFromCache } from '@/core/plugins/pluginLoader';
import { loadCachedThemeOverride, fetchThemeOverride } from '@/core/theme/themeLoader';
import { isAuthenticated } from '@/core/auth/authService';

export interface BootstrapResult {
  authenticated: boolean;
  themeOverride: unknown | null;
}

export async function bootstrap(): Promise<BootstrapResult> {
  await initEnv();
  installScriptManager();

  // 缓存先绘：快照与主题覆盖层都是上次成功值，离线也成立。
  const themeOverride = await loadCachedThemeOverride();
  await warmupFromCache();

  // 后台同步：失败只记日志，快照已保证应用可用。
  void syncPlugins().catch((e) => console.warn('[bootstrap] 插件同步失败', e));
  void fetchThemeOverride().catch(() => undefined);

  const authenticated = await isAuthenticated();
  return { authenticated, themeOverride };
}
