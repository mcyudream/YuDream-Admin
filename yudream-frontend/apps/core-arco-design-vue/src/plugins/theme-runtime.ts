import type { Router } from 'vue-router'
import type { PluginTheme, PluginThemeScope } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import { pluginFrontendAssetUrl } from './frontend-assets'

const SCOPES: PluginThemeScope[] = ['SITE', 'ADMIN']
const SCOPE_ATTRIBUTE = 'data-yudream-theme-scope'
const PLUGIN_ATTRIBUTE = 'data-yudream-theme-plugin'

let currentRoutePublic = false

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
  for (const scope of SCOPES) {
    const theme = themes[scope]
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
  syncScopeVisibility()
}

function syncScopeVisibility() {
  for (const scope of SCOPES) {
    const enabled = scope === 'SITE' ? currentRoutePublic : !currentRoutePublic
    for (const link of themeLinks(scope)) {
      link.disabled = !enabled
    }
  }
}

function themeLinks(scope: PluginThemeScope) {
  return Array.from(document.head.querySelectorAll<HTMLLinkElement>(`link[${SCOPE_ATTRIBUTE}="${scope.toLowerCase()}"]`))
}
