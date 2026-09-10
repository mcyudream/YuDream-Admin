import type { HomePagePreset } from './platform-cms'
import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface ThemeCenterTheme {
  /** 主题归属插件编码；空表示宿主内置默认主题 */
  pluginCode?: string | null
  code: string
  name: string
  description?: string
  preview?: string
  assetRevision?: string
  hasHomePreset?: boolean
  hasPageSet?: boolean
  /** 主题声明了 homeComponent：公开站首页由插件 Vue 页面承载，首页设计器不再适用 */
  hasHomeComponent?: boolean
  /** 主题声明了 chromeComponent：公开站页头/页脚由插件 Vue 页面承载 */
  hasChromeComponent?: boolean
  /** 主题是否声明了配置 schema（theme-config.json） */
  hasConfigSchema?: boolean
  active?: boolean
  enabled?: boolean
}

export interface ThemeConfigOption {
  label: string
  value: string
}

/** 主题配置字段：type 为 text/textarea/number/switch/select/color/image/list，list 带 itemFields 子字段 */
export interface ThemeConfigField {
  key: string
  label: string
  description?: string
  type: string
  placeholder?: string
  defaultValue?: unknown
  options?: ThemeConfigOption[]
  /** 敏感字段：管理端读取脱敏为空串，留空保存表示不修改 */
  secret?: boolean
  itemFields?: ThemeConfigField[]
}

export interface ThemeConfigSection {
  code: string
  title: string
  description?: string
  fields: ThemeConfigField[]
}

export interface ThemeConfigSchema {
  sections: ThemeConfigSection[]
}

export interface ThemeConfig {
  themeCode: string
  schema: ThemeConfigSchema
  values: Record<string, any>
  /** secret 字段是否已配置（值为脱敏后的空串时依据此标识显示"已配置"） */
  secretConfigured?: Record<string, boolean>
}

export interface ThemeCenterOverview {
  themes: ThemeCenterTheme[]
  presets: HomePagePreset[]
  /** 可编辑内容的主题编码清单（默认主题 + 主题插件卡 + 拥有存量布局的主题） */
  editableThemes: string[]
  cmsEnabled: boolean
}

export default {
  overview: () => {
    return systemClient.get<unknown, ApiResponse<ThemeCenterOverview>>('api/platform/themes/overview')
  },
  activate: (code: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/themes/${code}/activate`)
  },
  deactivate: () => {
    return systemClient.post<unknown, ApiResponse<void>>('api/platform/themes/deactivate')
  },
  config: (theme: string) => {
    return systemClient.get<unknown, ApiResponse<ThemeConfig>>(`api/platform/themes/${theme}/config`)
  },
  saveConfig: (theme: string, values: Record<string, any>) => {
    return systemClient.put<unknown, ApiResponse<ThemeConfig>>(`api/platform/themes/${theme}/config`, { values })
  },
}
