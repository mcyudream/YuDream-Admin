/**
 * 启动编排：桥 -> 域恢复 -> 激活域快照与主题缓存先绘 -> 鉴权判定。
 * 插件更新/主题刷新在登录后后台并行，不阻塞首帧。
 */
import { installScriptManager } from '@/core/plugins/scriptManager';
import { syncPlugins, warmupFromCache } from '@/core/plugins/pluginLoader';
import { loadCachedThemeOverride, fetchThemeOverride } from '@/core/theme/themeLoader';
import { fetchMe, isAuthenticated } from '@/core/auth/authService';
import { getActiveDomain, loadDomains, updateDomainBranding } from '@/core/domains/store';
import { discoverDomain } from '@/core/domains/discover';

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

  // 后台刷新主题（匿名）与站点品牌信息（logo/登录页定制可能被管理员更新）。
  void fetchThemeOverride(domain.id, domain.serverUrl)
    .then((override) => {
      if (override?.primaryColor) {
        void updateDomainBranding(domain.id, { themeColor: override.primaryColor });
      }
    })
    .catch(() => undefined);
  void discoverDomain(domain.serverUrl)
    .then((outcome) => {
      if (outcome.kind === 'ok') {
        void updateDomainBranding(domain.id, {
          logo: outcome.info.logo,
          loginHeroImage: outcome.info.loginHeroImage,
          loginHeroBackground: outcome.info.loginHeroBackground,
          homeBanners: outcome.info.homeBanners,
        });
      }
    })
    .catch(() => undefined);

  const authenticated = await isAuthenticated();
  // 已登录的启动同样后台同步（否则重启后永远停留在旧快照，新装的插件不出现）
  if (authenticated) {
    startBackgroundSync();
    // 刷新账户摘要（管理员标志可能变化）
    void fetchMe().catch(() => undefined);
  }
  return { hasDomain: true, authenticated, themeOverride };
}

/** 登录成功后的后台同步（首页也会下拉触发，这里保证启动即拉）。 */
export function startBackgroundSync(): void {
  void syncPlugins().catch((e) => console.warn('[bootstrap] 插件同步失败', e));
}
