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
  active?: boolean
  enabled?: boolean
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
}
