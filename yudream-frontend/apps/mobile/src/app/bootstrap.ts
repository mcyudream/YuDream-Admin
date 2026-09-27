/**
 * 启动编排：桥 -> 域恢复 -> 激活域快照与主题缓存先绘 -> 鉴权判定。
 * 插件更新/主题刷新在登录后后台并行，不阻塞首帧。
 */
import { installScriptManager } from '@/core/plugins/scriptManager';
import { syncPlugins, warmupFromCache } from '@/core/plugins/pluginLoader';
import { loadCachedThemeOverride, fetchThemeOverride } from '@/core/theme/themeLoader';
import { isAuthenticated } from '@/core/auth/authService';
import { getActiveDomain, loadDomains } from '@/core/domains/store';

export interface BootstrapResult {
  /** 是否已有接入的域 */
  hasDomain: boolean;
  /** 激活域是否已登录 */
  authenticated: boolean;
  themeOverride: unknown | null;
}

export async function bootstrap(): Promise<BootstrapResult> {
  installScriptManager();
  await loadDomains();

  const domain = getActiveDomain();
  if (!domain) {
    return { hasDomain: false, authenticated: false, themeOverride: null };
  }

  // 缓存先绘：快照与主题覆盖层都是上次成功值，离线也成立。
  const themeOverride = await loadCachedThemeOverride(domain.id);
  await warmupFromCache();

  // 后台刷新主题（匿名）；插件同步需要登录态，登录成功后由首页触发。
  void fetchThemeOverride(domain.id, domain.serverUrl).catch(() => undefined);

  const authenticated = await isAuthenticated();
  return { hasDomain: true, authenticated, themeOverride };
}

/** 登录成功后的后台同步（首页也会下拉触发，这里保证启动即拉）。 */
export function startBackgroundSync(): void {
  void syncPlugins().catch((e) => console.warn('[bootstrap] 插件同步失败', e));
}
