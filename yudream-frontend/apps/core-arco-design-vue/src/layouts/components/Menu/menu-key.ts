import type { MenuRecordRaw } from '@fantastic-admin/types'

function hasVisibleChildren(menu: MenuRecordRaw) {
  return menu.children?.some(item => item.meta?.menu !== false) ?? false
}

export function getMenuNodeKey(menu: MenuRecordRaw, fallback: (menu: MenuRecordRaw) => string): string {
  if (!hasVisibleChildren(menu)) {
    return menu.path ?? fallback(menu)
  }

  const menuCode = (menu.meta as Record<string, unknown> | undefined)?.menuCode
  return typeof menuCode === 'string' && menuCode.length > 0 ? menuCode : fallback(menu)
}
