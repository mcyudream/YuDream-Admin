import type { Router } from 'vue-router'
import { useNProgress } from '@vueuse/integrations/useNProgress'
import { warnKeepAliveComponentNameMissing } from 'virtual:fantastic-admin/turbo-console'
import apiSetup from '@/api/modules/setup'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { refreshDynamicRoutes } from './dynamic'
import '@/assets/styles/nprogress.css'

// 系统初始化状态缓存
let setupStatus: boolean | null = null

async function checkSetupStatus(): Promise<boolean> {
  // 开发环境优先读取 localStorage 缓存，减少每次刷新都请求 setup/status
  const cached = import.meta.env.DEV ? localStorage.getItem('setupCompleted') : null
  if (cached !== null) {
    setupStatus = cached === 'true'
    return setupStatus
  }
  if (setupStatus !== null) {
    return setupStatus
  }
  try {
    const res = await apiSetup.status()
    setupStatus = res.data.setupCompleted
    return setupStatus
  }
  catch {
    // 接口异常时保守处理，认为已完成，避免用户被锁在初始化页
    setupStatus = true
    return setupStatus
  }
}

function setupRoutes(router: Router) {
  router.beforeEach(async (to) => {
    // 系统初始化拦截：未完成且非 setup 页则跳转 setup；已完成进入 setup 则显示 404
    const setupCompleted = await checkSetupStatus()
    if (!setupCompleted && to.name !== 'setup') {
      return {
        name: 'setup',
        replace: true,
      }
    }
    if (setupCompleted && to.name === 'setup') {
      return {
        name: 'notFound',
        params: { all: ['setup'] },
        replace: true,
      }
    }

    const appSettingsStore = useAppSettingsStore()
    const appAccountStore = useAppAccountStore()
    const appRouteStore = useAppRouteStore()
    const appMenuStore = useAppMenuStore()
    // 是否已登录
    if (appAccountStore.isLogin) {
      // 是否已根据权限动态生成并注册路由
      if (appRouteStore.isGenerate) {
        // 导航菜单如果不是 single 模式，则需要根据 path 定位主导航菜单的选中状态
        appSettingsStore.settings.menu.mode !== 'single' && appMenuStore.setActived(to.path)
        // 如果已登录状态下，进入登录页会强制跳转到主页
        if (to.name === 'login') {
          return {
            path: appSettingsStore.settings.app.home.fullPath,
            replace: true,
          }
        }
        // 如果未开启主页，但进入的是主页，则会进入侧边栏导航第一个模块
        else if (!appSettingsStore.settings.app.home.enable && to.fullPath === appSettingsStore.settings.app.home.fullPath && appMenuStore.sidebarMenus.length > 0) {
          return {
            path: appMenuStore.sidebarMenusFirstDeepestPath,
            replace: true,
          }
        }
      }
      else {
        try {
          await refreshDynamicRoutes(router)
        }
        catch {}
        // 动态路由生成并注册后，重新进入当前路由
        return {
          path: to.path,
          query: to.query,
          replace: true,
        }
      }
    }
    else {
      if (to.name === 'setup' || to.name === 'verifyEmail') {
        return
      }
      const appFeatureStore = useAppFeatureStore()
      await appFeatureStore.load()
      if ((to.name === 'publicSiteHome' || to.name === 'publicSitePage') && !appFeatureStore.cmsEnabled) {
        return {
          name: 'login',
          replace: true,
        }
      }
      if (to.name === 'publicDynamicForm' && !appFeatureStore.formEnabled) {
        return {
          name: 'login',
          replace: true,
        }
      }
      if (to.name !== 'login' && !to.meta?.public) {
        return {
          name: appFeatureStore.cmsEnabled ? 'publicSiteHome' : 'login',
          replace: true,
        }
      }
    }
  })
}

// 当父级路由未配置重定向时，自动重定向到有访问权限的子路由
function setupRedirectAuthChildrenRoute(router: Router) {
  router.beforeEach((to) => {
    const { auth } = useAppAuth()
    const currentRoute = router.getRoutes().find(route => route.path === (to.matched.at(-1)?.path ?? ''))
    if (!currentRoute?.redirect) {
      const findAuthRoute = currentRoute?.children?.find(route => route.meta?.menu !== false && auth(route.meta?.auth ?? ''))
      if (findAuthRoute) {
        return findAuthRoute
      }
    }
  })
}

// 路由级权限兜底。后端动态路由会按权限裁剪；本地隐藏路由也需要同一套 auth 判断。
function setupRoutePermission(router: Router) {
  router.beforeEach((to) => {
    const authValue = to.matched
      .map(route => route.meta?.auth)
      .filter(Boolean)
      .at(-1) ?? to.meta?.auth
    if (!authValue) {
      return
    }
    const { auth } = useAppAuth()
    if (!auth(authValue as string | string[])) {
      return {
        name: 'notFound',
        params: { all: to.path.replace(/^\/+/, '').split('/') },
        replace: true,
      }
    }
  })
}

// 进度条
function setupProgress(router: Router) {
  const { isLoading } = useNProgress()
  router.beforeEach(() => {
    const appSettingsStore = useAppSettingsStore()
    if (appSettingsStore.settings.page.progress) {
      isLoading.value = true
    }
  })
  router.afterEach(() => {
    const appSettingsStore = useAppSettingsStore()
    if (appSettingsStore.settings.page.progress) {
      isLoading.value = false
    }
  })
}
// 标题
function setupTitle(router: Router) {
  router.afterEach((to) => {
    const appSettingsStore = useAppSettingsStore()
    appSettingsStore.setTitle(to.matched?.at(-1)?.meta?.title ?? to.meta.title)
  })
}

// 页面保活
function setupKeepAlive(router: Router) {
  router.afterEach(async (to, from) => {
    const appKeepAliveStore = useAppKeepAliveStore()
    if (to.meta.keepAlive) {
      const componentName = to.matched.at(-1)?.components?.default.name
      if (componentName) {
        // 保活当前页面前，先判断是否需要清除保活，判断依据：
        // 1. 如果 to.meta.keepAlive 为 boolean 类型，并且不为 true，则需要清除保活
        // 2. 如果 to.meta.keepAlive 为 string 类型，并且与 from.name 不一致，则需要清除保活
        // 3. 如果 to.meta.keepAlive 为 array 类型，并且不包含 from.name，则需要清除保活
        // 4. 如果 to.meta.noKeepAlive 为 string 类型，并且与 from.name 一致，则需要清除保活
        // 5. 如果 to.meta.noKeepAlive 为 array 类型，并且包含 from.name，则需要清除保活
        // 6. 如果是刷新页面，则需要清除保活
        let shouldClear = false
        if (typeof to.meta.keepAlive === 'boolean') {
          shouldClear = !to.meta.keepAlive
        }
        else if (typeof to.meta.keepAlive === 'string') {
          shouldClear = to.meta.keepAlive !== from.name
        }
        else if (Array.isArray(to.meta.keepAlive)) {
          shouldClear = !to.meta.keepAlive.includes(from.name as string)
        }
        if (to.meta.noKeepAlive) {
          if (typeof to.meta.noKeepAlive === 'string') {
            shouldClear = to.meta.noKeepAlive === from.name
          }
          else if (Array.isArray(to.meta.noKeepAlive)) {
            shouldClear = to.meta.noKeepAlive.includes(from.name as string)
          }
        }
        if (from.name === 'reload') {
          shouldClear = true
        }
        if (shouldClear) {
          appKeepAliveStore.remove(componentName)
          await nextTick()
        }
        appKeepAliveStore.add(componentName)
      }
      else if (import.meta.env.DEV) {
        warnKeepAliveComponentNameMissing((to.matched.at(-1)?.components?.default as any).__file)
      }
    }
  })
}

// 其他
function setupOther(router: Router) {
  router.afterEach(() => {
    document.documentElement.scrollTop = 0
  })
}

export default function setupGuards(router: Router) {
  setupRoutes(router)
  setupRedirectAuthChildrenRoute(router)
  setupRoutePermission(router)
  setupProgress(router)
  setupTitle(router)
  setupKeepAlive(router)
  setupOther(router)
}
