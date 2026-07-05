import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw, RouterMatcher } from 'vue-router'
import { cloneDeep } from 'es-toolkit'
import { createRouterMatcher } from 'vue-router'
import apiApp from '@/api/modules/app'
import apiPlugin from '@/api/modules/platform-plugin'
import { systemRoutes as systemRoutesRaw } from '@/router/routes'

export const useAppRouteStore = defineStore(
  'appRoute',
  () => {
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
          tmpRoutes.map((v) => {
            if (!v.meta) {
              v.meta = {}
            }
            v.meta.auth = item.meta?.auth ?? v.meta?.auth
            return v
          })
          returnRoutes.push(...tmpRoutes)
        })
        returnRoutes.forEach((item) => {
          if (item.children) {
            item.children = deleteMiddleRouteComponent(item.children)
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
          item.children = deleteMiddleRouteComponent(item.children)
        }
      })
      return routes
    })
    // 删除路由中间层级对应的组件
    function deleteMiddleRouteComponent(routes: RouteRecordRaw[]) {
      const res: RouteRecordRaw[] = []
      routes.forEach((route) => {
        if (route.children?.length) {
          delete route.component
          route.children = deleteMiddleRouteComponent(route.children)
        }
        else {
          delete route.children
        }
        res.push(route)
      })
      return res
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
    function formatBackRoutes(routes: any, views = import.meta.glob('@/views/**/*.vue')): RouteRecordMainRaw[] {
      return routes.map((route: any) => {
        localizeSystemMonitorRoute(route)
        const originalComponent = route.component
        if (typeof route.component === 'string') {
          switch (route.component) {
            case 'Layout':
              route.component = () => import('@/layouts/index.vue')
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
          route.component = () => import('@/layouts/index.vue')
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
          route.children = formatBackRoutes(route.children, views)
        }
        return route
      })
    }
    function localizeSystemMonitorRoute(route: any) {
      const titleMap: Record<string, string> = {
        'Online users': '\u5728\u7ebf\u7528\u6237',
        'API logs': '\u63a5\u53e3\u65e5\u5fd7',
        'Login logs': '\u767b\u5f55\u65e5\u5fd7',
      }
      const pathMap: Record<string, string> = {
        '/system/online-user': '\u5728\u7ebf\u7528\u6237',
        '/system/api-log': '\u63a5\u53e3\u65e5\u5fd7',
        '/system/login-log': '\u767b\u5f55\u65e5\u5fd7',
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
    async function loadPluginRoutes(): Promise<RouteRecordMainRaw[]> {
      try {
        const res = await apiPlugin.frontendManifest()
        const groups: RouteRecordMainRaw[] = []
        const legacyChildren: RouteRecordRaw[] = []
        res.data.modules?.forEach((module) => {
          const children: RouteRecordRaw[] = []
          module.routes?.forEach((route) => {
            const routeName = route.name || `${module.pluginCode}-${route.path}`
            const routeMeta = {
              title: route.title,
              icon: route.icon || 'i-ri:puzzle-2-line',
              auth: route.permission,
              sort: route.sort,
              plugin: {
                pluginCode: module.pluginCode,
                component: route.component,
                entry: module.entry,
                moduleName: module.moduleName,
                sdkVersion: module.sdkVersion || res.data.sdkVersion,
              },
            } as any
            const routeRecord = {
              path: route.path,
              name: `${routeName}-layout`,
              component: () => import('@/layouts/index.vue'),
              meta: routeMeta,
              children: [{
                path: '',
                name: routeName,
                component: () => import('@/views/platform/plugin/runtime-page.vue'),
                meta: {
                  ...routeMeta,
                  menu: false,
                  breadcrumb: false,
                },
              }],
            }
            children.push(routeRecord)
          })
          if (!children.length) {
            return
          }
          if (module.menuTitle) {
            groups.push({
              meta: {
                title: module.menuTitle,
                icon: module.menuIcon || 'i-ri:puzzle-2-line',
                sort: module.menuSort ?? 20,
              },
              children,
            })
          }
          else {
            legacyChildren.push(...children)
          }
        })
        if (legacyChildren.length) {
          groups.push({
            meta: {
              title: '插件扩展',
              icon: 'i-ri:puzzle-2-line',
              sort: 20,
            },
            children: legacyChildren,
          })
        }
        return groups
      }
      catch {
        return []
      }
    }
    async function generateRoutesAtBack() {
      const res = await apiApp.routeList()
      const pluginRoutes = await loadPluginRoutes()
        // 设置 routes 数据
      routesRaw.value = sortAsyncRoutes([...(formatBackRoutes(res.data) as any), ...pluginRoutes] as any)
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
