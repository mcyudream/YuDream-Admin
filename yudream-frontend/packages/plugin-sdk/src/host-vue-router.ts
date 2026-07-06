const vueRouter = shared('vueRouter')

function shared(name: string) {
  const modules = (globalThis as any).__YUDREAM_PLUGIN_SHARED__
  const module = modules?.[name]
  if (!module) {
    throw new Error(`YuDream plugin shared runtime is missing: ${name}`)
  }
  return module
}

export const NavigationFailureType = vueRouter.NavigationFailureType
export const RouterLink = vueRouter.RouterLink
export const RouterView = vueRouter.RouterView
export const START_LOCATION = vueRouter.START_LOCATION
export const createMemoryHistory = vueRouter.createMemoryHistory
export const createRouter = vueRouter.createRouter
export const createRouterMatcher = vueRouter.createRouterMatcher
export const createWebHashHistory = vueRouter.createWebHashHistory
export const createWebHistory = vueRouter.createWebHistory
export const isNavigationFailure = vueRouter.isNavigationFailure
export const loadRouteLocation = vueRouter.loadRouteLocation
export const matchedRouteKey = vueRouter.matchedRouteKey
export const onBeforeRouteLeave = vueRouter.onBeforeRouteLeave
export const onBeforeRouteUpdate = vueRouter.onBeforeRouteUpdate
export const parseQuery = vueRouter.parseQuery
export const routeLocationKey = vueRouter.routeLocationKey
export const routerKey = vueRouter.routerKey
export const routerViewLocationKey = vueRouter.routerViewLocationKey
export const stringifyQuery = vueRouter.stringifyQuery
export const useLink = vueRouter.useLink
export const useRoute = vueRouter.useRoute
export const useRouter = vueRouter.useRouter
export const viewDepthKey = vueRouter.viewDepthKey

export default vueRouter
