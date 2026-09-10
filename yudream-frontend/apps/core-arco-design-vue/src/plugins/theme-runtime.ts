import type { Router } from 'vue-router'
import type { PluginRemoteModuleLease } from './remote-loader'
import type { PluginTheme, PluginThemeScope } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import { pluginFrontendAssetUrl } from './frontend-assets'
import { acquirePluginRemoteModuleByCode } from './remote-loader'

const SCOPES: PluginThemeScope[] = ['SITE', 'ADMIN']
const SCOPE_ATTRIBUTE = 'data-yudream-theme-scope'
const PLUGIN_ATTRIBUTE = 'data-yudream-theme-plugin'

let currentRoutePublic = false
let activeThemePlugins = new Map<string, string>()
const themeModuleLeases = new Map<string, PluginRemoteModuleLease>()
const pendingThemeModules = new Set<string>()
const activeThemesSnapshot = shallowRef<Partial<Record<PluginThemeScope, PluginTheme>>>({})

/** 当前激活的插件主题（按 scope），供主题首页挂载等场景读取 homeComponent/moduleName。 */
export function useActivePluginTheme(scope: PluginThemeScope) {
  return computed(() => activeThemesSnapshot.value[scope])
}

/**
 * 启动时拉取已激活的插件主题，并在应用挂载前注入常驻样式，避免公开站主题闪烁。
 * 失败时静默回落宿主内置主题。
 */
export async function bootstrapPluginThemes() {
  try {
    const res = await apiPlugin.activeThemes()
    applyActiveThemes(res.data || {})
  }
  catch {
    // 主题加载失败时回落宿主内置主题
  }
}

/** 启用/禁用主题插件后无刷新重载激活主题。 */
export async function refreshPluginThemes() {
  await bootstrapPluginThemes()
}

/** 公开路由启用 site 主题并禁用 admin 主题，后台路由相反，避免两个 scope 的变量互相污染。 */
export function watchPluginThemeScope(router: Router) {
  const sync = () => {
    currentRoutePublic = router.currentRoute.value.meta?.public === true
    syncScopeVisibility()
  }
  router.afterEach(sync)
  sync()
}

function applyActiveThemes(themes: Partial<Record<PluginThemeScope, PluginTheme>>) {
  activeThemesSnapshot.value = themes
  const active = new Map<string, string>()
  for (const scope of SCOPES) {
    const theme = themes[scope]
    if (theme) {
      active.set(theme.pluginCode, theme.assetRevision || '')
    }
    const desired = theme ? theme.styles.map(path => pluginFrontendAssetUrl(theme.pluginCode, path, theme.assetRevision)) : []
    const existing = themeLinks(scope)
    const unchanged = existing.length === desired.length
      && existing.every((link, index) => link.getAttribute('href') === desired[index])
    if (unchanged) {
      continue
    }
    for (const link of existing) {
      link.remove()
    }
    if (!theme) {
      continue
    }
    for (const path of theme.styles) {
      const link = document.createElement('link')
      link.rel = 'stylesheet'
      link.href = pluginFrontendAssetUrl(theme.pluginCode, path, theme.assetRevision)
      link.setAttribute(SCOPE_ATTRIBUTE, scope.toLowerCase())
      link.setAttribute(PLUGIN_ATTRIBUTE, theme.pluginCode)
      document.head.appendChild(link)
    }
  }
  activeThemePlugins = active
  syncScopeVisibility()
  syncThemeRuntimeModules()
}

/**
 * 主题插件可携带远程模块（音效、交互动效等）：主题处于激活状态时加载其
 * remoteEntry 并调用 install，取消激活后释放触发 dispose。主题插件没有
 * 前端模块或加载失败时静默跳过，不影响主题样式本身。
 */
function syncThemeRuntimeModules() {
  for (const [code, lease] of themeModuleLeases) {
    if (activeThemePlugins.get(code) !== undefined) {
      continue
    }
    themeModuleLeases.delete(code)
    void lease.release().catch(() => {})
  }
  for (const code of activeThemePlugins.keys()) {
    if (themeModuleLeases.has(code) || pendingThemeModules.has(code)) {
      continue
    }
    pendingThemeModules.add(code)
    acquirePluginRemoteModuleByCode(code)
      .then((lease) => {
        if (activeThemePlugins.has(code)) {
          themeModuleLeases.set(code, lease)
        }
        else {
          void lease.release().catch(() => {})
        }
      })
      .catch(() => {})
      .finally(() => pendingThemeModules.delete(code))
  }
}

function syncScopeVisibility() {
  for (const scope of SCOPES) {
    const enabled = scope === 'SITE' ? currentRoutePublic : !currentRoutePublic
    for (const link of themeLinks(scope)) {
      // 不能用 disabled 开关：加载中途置 disabled 会永久中止样式表加载，
      // 再置回 false 也不会恢复（Chrome 行为），改用 media 开关只控制是否应用。
      link.media = enabled ? '' : 'not all'
    }
  }
}

function themeLinks(scope: PluginThemeScope) {
  return Array.from(document.head.querySelectorAll<HTMLLinkElement>(`link[${SCOPE_ATTRIBUTE}="${scope.toLowerCase()}"]`))
}
