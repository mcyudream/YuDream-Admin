import type { Router } from 'vue-router'
import type { SiteDocument, StudioPage } from '@/types/site-studio'
import { computed, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { fetchPublishedStudio } from '@/api/modules/platform-cms-studio'
import { isSiteThemeVisible } from '@/plugins/theme-public-path'
import { compileStudioPublicSkin } from '@/utils/site-studio-skin'
import { validateStudioDocument } from '@/utils/site-studio-validation'

/**
 * 当前激活主题的已发布站点文档（公开站共享，一次页面加载内只取一次）。
 * 宿主公开页据此做公开页适配：换肤样式与站点页头/页脚外壳。取失败或无发布文档时为 null。
 */
const published = shallowRef<SiteDocument | null>(null)
let pending: Promise<SiteDocument | null> | null = null

export function loadPublishedStudioDocument() {
  pending ??= fetchPublishedStudio()
    .then((result) => {
      if (!result?.documentJson) {
        return null
      }
      const parsed: unknown = JSON.parse(result.documentJson)
      validateStudioDocument(parsed)
      return parsed
    })
    .catch(() => null)
    .then((document) => {
      published.value = document
      return document
    })
  return pending
}

export function usePublishedStudioDocument() {
  void loadPublishedStudioDocument()
  return published
}

/**
 * 承载当前宿主公开路由的站点页面：已发布文档开启站点外壳，且某页面 settings.hostRoutes 以最长前缀匹配当前路径。
 * 命中时宿主页面应嵌入该页面渲染（StudioShadowLayout），与站点菜单页同属一个主题。
 */
export function useStudioHostLayout() {
  const route = useRoute()
  const document = usePublishedStudioDocument()
  return computed<{ document: SiteDocument, page: StudioPage, pageTitle: string } | null>(() => {
    const current = document.value
    if (!current?.features?.publicSkin?.chrome) {
      return null
    }
    let best: { page: StudioPage, length: number } | null = null
    for (const page of current.pages) {
      for (const prefix of page.settings?.hostRoutes ?? []) {
        if ((route.path === prefix || route.path.startsWith(`${prefix}/`)) && (!best || prefix.length > best.length)) {
          best = { page, length: prefix.length }
        }
      }
    }
    return best ? { document: current, page: best.page, pageTitle: String(route.meta?.title || '') } : null
  })
}

/**
 * 应用级持久外壳：已发布文档开启「站点外壳」时，所有公开页面（站点页、承载的宿主公开页）共用同一份页头/页脚，
 * 跨路由不重建（导航位置、数据与当前状态一致）。草稿预览与嵌入页不使用。
 */
export function useStudioPublicShell() {
  const route = useRoute()
  const document = usePublishedStudioDocument()
  return computed(() => {
    const current = document.value
    if (!current?.features?.publicSkin?.chrome) {
      return null
    }
    if (route.path.startsWith('/site-preview') || route.path.startsWith('/embed') || !isSiteThemeVisible(route.meta?.public === true)) {
      return null
    }
    return current
  })
}

const SKIN_STYLE_ID = 'yd-studio-public-skin'

/**
 * 公开页适配样式：仅在公开路由启用（与 SITE 主题同一判定），后台路由移除，避免影响管理端。
 * 插件主题激活时 /api/public/cms/studio 返回该主题的文档；未发布时不注入任何样式。
 */
export function installStudioPublicSkin(router: Router) {
  const apply = async () => {
    const visible = isSiteThemeVisible(router.currentRoute.value.meta?.public === true)
    const existing = document.getElementById(SKIN_STYLE_ID)
    if (!visible) {
      existing?.remove()
      return
    }
    const doc = await loadPublishedStudioDocument()
    const css = doc ? compileStudioPublicSkin(doc) : ''
    if (!css) {
      existing?.remove()
      return
    }
    const style = existing ?? Object.assign(document.createElement('style'), { id: SKIN_STYLE_ID })
    if (style.textContent !== css) {
      style.textContent = css
    }
    if (!existing) {
      document.head.appendChild(style)
    }
  }
  router.afterEach(() => void apply())
  void apply()
}
