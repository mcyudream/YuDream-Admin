import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw, RouterMatcher } from 'vue-router'
import type { AuthRouteNode } from './route-auth'
import type { PluginFrontendModule } from '@/api/modules/platform-plugin'
import { cloneDeep } from 'es-toolkit'
import { createRouterMatcher } from 'vue-router'
import apiApp from '@/api/modules/app'
import apiPlugin from '@/api/modules/platform-plugin'
import { systemRoutes as systemRoutesRaw } from '@/router/routes'
import { indexPluginRuntimeModules, resolvePluginRuntimeRoute } from './plugin-route-runtime'
import { collectBackendMatcherRoutes, flattenBackendRouteGroups, mergeBackendStructuralRoutes, stripBackendStructuralAuth } from './route-auth'

// 管理端布局的统一引用：后端 'Layout' 转换与叶子包装层共用，供 deleteMiddleRouteComponent
// 以身份比较识别「布局提供层」（es-toolkit cloneDeep 保函数引用）
const adminLayoutComponent = () => import('@/layouts/index.vue')

export const useAppRouteStore = defineStore(
  'appRoute',
  () => {
    const appSettingsStore = useAppSettingsStore()
    const isGenerate = ref(false)
    // 原始路由
    const routesRaw = ref<RouteRecordMainRaw[]>([])
    // 已注册的路由，用于登出时删除路由
    const currentRemoveRoutes = ref<(() => void)[]>([])

    // 实际路由
    const routes = computed(() => {
      const returnRoutes: RouteRecordRaw[] = []
      if (routesRaw.value) {
        routesRaw.value.forEach((item) => {
          const tmpRoutes = cloneDeep(item.children) as RouteRecordRaw[]
          if (appSettingsStore.settings.app.routeBaseOn === 'backend') {
            returnRoutes.push(...flattenBackendRouteGroups([{
              ...cloneDeep(item),
              children: tmpRoutes as unknown as AuthRouteNode[],
            }]) as RouteRecordRaw[])
          }
          else {
            tmpRoutes.forEach((v) => {
              if (!v.meta) {
                v.meta = {}
              }
              v.meta.auth = item.meta?.auth ?? v.meta?.auth
            })
            returnRoutes.push(...tmpRoutes)
          }
        })
        returnRoutes.forEach((item) => {
          if (item.children) {
            if (!hasDefaultPageChild(item.children)) {
              item.redirect ||= resolveFirstVisibleChildPath(item.children)
            }
            item.children = deleteMiddleRouteComponent(item.children, item.component === adminLayoutComponent)
          }
          return item
        })
      }
      return returnRoutes
    })
    // 系统路由
    const systemRoutes = computed(() => {
      const routes = [...systemRoutesRaw]
      routes.forEach((item) => {
        if (item.children) {
          if (!hasDefaultPageChild(item.children)) {
            item.redirect ||= resolveFirstVisibleChildPath(item.children)
          }
          item.children = deleteMiddleRouteComponent(item.children)
        }
      })
      return routes
    })
    // 删除路由中间层级对应的组件。仅当祖先已提供管理端布局时才剥组件：
    // 挂在无组件分类节点下的叶子包装层（adminLayoutComponent）必须保留，
    // 否则插件页会失去侧栏布局整页裸渲染
    function deleteMiddleRouteComponent(routes: RouteRecordRaw[], hasLayoutAncestor = false) {
      const res: RouteRecordRaw[] = []
      routes.forEach((route) => {
        if (route.children?.length) {
          if (!hasDefaultPageChild(route.children)) {
            route.redirect ||= resolveFirstVisibleChildPath(route.children)
          }
          const providesLayout = route.component === adminLayoutComponent
          if (hasLayoutAncestor || !providesLayout) {
            delete route.component
          }
          route.children = deleteMiddleRouteComponent(route.children, hasLayoutAncestor || providesLayout)
        }
        else {
          delete route.children
        }
        res.push(route)
      })
      return res
    }
    function hasDefaultPageChild(routes: RouteRecordRaw[]) {
      return routes.some(route => route.path === '')
    }
    // 中间目录节点没有实际页面时，访问目录路径应进入第一个可见子页面，避免内容区空白。
    function resolveFirstVisibleChildPath(routes: RouteRecordRaw[]): string | undefined {
      for (const route of routes) {
        if (route.meta?.menu === false) {
          continue
        }
        if (route.children?.length) {
          const childPath = resolveFirstVisibleChildPath(route.children)
          if (childPath) {
            return childPath
          }
        }
        if (typeof route.path === 'string' && route.path) {
          return route.path
        }
      }
      return undefined
    }

    // 路由匹配器
    const routesMatcher = ref<RouterMatcher>()
    // 根据路径获取匹配的路由
    function getRouteMatchedByPath(path: string) {
      return routesMatcher.value?.resolve({ path }, undefined!)?.matched ?? []
    }

    // 路由排序，sort 越大越靠前
    function sortAsyncRoutes<T extends RouteRecordMainRaw[] | RouteRecordRaw[]>(routes: T): T {
      routes.sort((a, b) => (b.meta?.sort ?? 0) - (a.meta?.sort ?? 0))
      routes.forEach((route) => {
        if (route.children) {
          route.children = sortAsyncRoutes(route.children)
        }
      })
      return routes
    }

    // 生成路由（前端生成）
    function generateRoutesAtFront(asyncRoutes: RouteRecordMainRaw[]) {
      // 设置 routes 数据
      routesRaw.value = sortAsyncRoutes(cloneDeep(asyncRoutes) as any)
      // 创建路由匹配器
      const routes: RouteRecordRaw[] = []
      routesRaw.value.forEach((route) => {
        if (route.children) {
          routes.push(...route.children)
        }
      })
      routesMatcher.value = createRouterMatcher(routes, {})
      isGenerate.value = true
    }
    // 格式化后端路由数据
    function formatBackRoutes(
      routes: any,
      pluginModules: Map<string, PluginFrontendModule>,
      pluginSdkVersion?: string,
      views = import.meta.glob('@/views/**/*.vue'),
    ): RouteRecordMainRaw[] {
      return routes.map((route: any) => {
        localizeSystemMonitorRoute(route)
        const originalComponent = route.component
        const pluginRuntime = resolvePluginRuntimeRoute(route.meta, originalComponent, pluginModules, pluginSdkVersion)
        if (pluginRuntime) {
          route.component = () => import('@/views/platform/plugin/runtime-page.vue')
          route.meta = {
            ...route.meta,
            plugin: pluginRuntime,
          }
        }
        else if (typeof route.component === 'string') {
          switch (route.component) {
            case 'Layout':
              route.component = adminLayoutComponent
              break
            default:
              route.component = views[`/src/views/${route.component}`]
          }
        }
        else if (!route.component) {
          delete route.component
        }
        if (typeof originalComponent === 'string' && originalComponent !== 'Layout' && route.component && !route.children?.length) {
          const pageComponent = route.component
          const pageName = route.name
          route.component = adminLayoutComponent
          route.name = `${pageName || route.path}-layout`
          route.children = [{
            path: '',
            name: pageName,
            component: pageComponent,
            meta: {
              ...route.meta,
              menu: false,
              breadcrumb: false,
            },
          }]
        }
        if (route.children) {
          route.children = formatBackRoutes(route.children, pluginModules, pluginSdkVersion, views)
        }
        return route
      })
    }
    function localizeSystemMonitorRoute(route: any) {
      const titleMap: Record<string, string> = {
        'Online users': '在线用户',
        'API logs': '接口日志',
        'Login logs': '登录日志',
      }
      const pathMap: Record<string, string> = {
        '/system/online-user': '在线用户',
        '/system/api-log': '接口日志',
        '/system/login-log': '登录日志',
      }
      if (!route.meta) {
        route.meta = {}
      }
      const currentTitle = route.meta.title
      if (typeof currentTitle === 'string' && titleMap[currentTitle]) {
        route.meta.title = titleMap[currentTitle]
      }
      if (typeof route.path === 'string' && pathMap[route.path]) {
        route.meta.title = pathMap[route.path]
      }
    }
    // 生成路由（后端获取）
    async function loadPluginRuntimeManifest() {
      try {
        const res = await apiPlugin.frontendManifest()
        useTourStore().setPluginGuides(res.data.guides || [])
        return {
          modules: indexPluginRuntimeModules(res.data.modules || []),
          sdkVersion: res.data.sdkVersion,
        }
      }
      catch {
        useTourStore().setPluginGuides([])
        return {
          modules: new Map<string, PluginFrontendModule>(),
          sdkVersion: undefined,
        }
      }
    }
    async function generateRoutesAtBack() {
      const [res, pluginManifest] = await Promise.all([
        apiApp.routeList(),
        loadPluginRuntimeManifest(),
      ])
      const staticRoutes = formatBackRoutes(
        mergeBackendStructuralRoutes(stripBackendStructuralAuth(res.data)),
        pluginManifest.modules,
        pluginManifest.sdkVersion,
      ) as any
      // 设置 routes 数据
      routesRaw.value = sortAsyncRoutes(staticRoutes)
      routesMatcher.value = createRouterMatcher(
        collectBackendMatcherRoutes(routesRaw.value as unknown as AuthRouteNode[]) as RouteRecordRaw[],
        {},
      )
      isGenerate.value = true
    }
    function setCurrentRemoveRoutes(routes: (() => void)[]) {
      currentRemoveRoutes.value = routes
    }
    // 清空动态路由
    function removeRoutes() {
      isGenerate.value = false
      routesRaw.value = []
      currentRemoveRoutes.value.forEach((removeRoute) => {
        removeRoute()
      })
      currentRemoveRoutes.value = []
    }

    return {
      isGenerate,
      routesRaw,
      currentRemoveRoutes,
      routes,
      systemRoutes,
      getRouteMatchedByPath,
      generateRoutesAtFront,
      generateRoutesAtBack,
      setCurrentRemoveRoutes,
      removeRoutes,
    }
  },
)
