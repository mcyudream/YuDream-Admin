import type { Router } from 'vue-router'
import { asyncRoutes } from './routes'

export async function refreshDynamicRoutes(router: Router) {
  const appSettingsStore = useAppSettingsStore()
  const appAccountStore = useAppAccountStore()
  const appRouteStore = useAppRouteStore()

  appRouteStore.removeRoutes()
  appSettingsStore.settings.app.account.auth && await appAccountStore.getPermissions()
  switch (appSettingsStore.settings.app.routeBaseOn) {
    case 'frontend':
      appRouteStore.generateRoutesAtFront(asyncRoutes)
      break
    case 'backend':
      await appRouteStore.generateRoutesAtBack()
      break
  }

  const removeRoutes: (() => void)[] = []
  appRouteStore.routes.forEach((route) => {
    if (!/^(?:https?:|mailto:|tel:)/.test(route.path)) {
      removeRoutes.push(router.addRoute(route))
    }
  })
  appRouteStore.systemRoutes.forEach((route) => {
    removeRoutes.push(router.addRoute(route))
  })
  appRouteStore.setCurrentRemoveRoutes(removeRoutes)
}
