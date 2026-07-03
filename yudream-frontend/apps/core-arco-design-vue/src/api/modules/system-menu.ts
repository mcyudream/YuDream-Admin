import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type MenuNodeType = 'CATEGORY' | 'LAYOUT' | 'MENU' | 'LINK' | 'BUTTON'
export type MenuStatus = 'ACTIVE' | 'DISABLED'

export interface MenuManageItem {
  code: string
  name: string
  type: MenuNodeType
  parentCode?: string
  module?: string
  icon?: string
  path?: string
  component?: string
  link?: string
  sort?: number
  permission?: string
  status: MenuStatus
  children?: MenuManageItem[]
}

export interface MenuTreeParams {
  keyword?: string
  type?: MenuNodeType
  status?: MenuStatus
}

export interface MenuPayload {
  code?: string
  name: string
  type: MenuNodeType
  parentCode?: string
  module?: string
  icon?: string
  path?: string
  component?: string
  link?: string
  sort?: number
  permission?: string
  status?: MenuStatus
}

export default {
  tree: (params?: MenuTreeParams) => systemClient.get<unknown, ApiResponse<MenuManageItem[]>>('api/system/menus', { params }),
  create: (data: MenuPayload) => systemClient.post<unknown, ApiResponse<MenuManageItem>>('api/system/menus', data),
  update: (code: string, data: MenuPayload) => systemClient.put<unknown, ApiResponse<MenuManageItem>>(`api/system/menus/${encodeURIComponent(code)}`, data),
  disable: (code: string) => systemClient.delete<unknown, ApiResponse<null>>(`api/system/menus/${encodeURIComponent(code)}`),
}
