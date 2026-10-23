import { h } from 'vue'
import { useYdModal } from '@yudream/components'
import Profile from '@/components/AppAccountButton/profile.vue'
import { useAppAccountStore } from '@/store/modules/app/account'
import { useAppSettingsStore } from '@/store/modules/app/settings'
import { siteAccountAction } from '@/utils/site-studio-links'

/**
 * 公开站账号动作（个人资料弹窗、退出登录）：与宿主 chrome（site-chrome.vue）同一套行为。
 * beforeAction 用于关闭调用方的移动端菜单/浮层。
 */
export function useSiteAccountActions(beforeAction?: () => void) {
  const appAccountStore = useAppAccountStore()
  const appSettingsStore = useAppSettingsStore()
  const profileModal = useYdModal().create({
    alignCenter: true,
    header: false,
    footer: false,
    closeOnClickOverlay: false,
    closeOnPressEscape: false,
    class: 'profile-account-modal overflow-hidden',
    contentClass: 'min-h-full p-0 flex',
    content: () => h(Profile),
  })

  function openProfile() {
    beforeAction?.()
    profileModal.open()
  }

  function logout() {
    beforeAction?.()
    appAccountStore.logout(appSettingsStore.settings.app.home.fullPath)
  }

  /** 命中账号动作链接则执行并返回 true（调用方跳过路由跳转）；href 取锚点原始值。 */
  function handleAccountAction(href?: string | null) {
    const action = siteAccountAction(href)
    if (action === 'profile') {
      openProfile()
    }
    else if (action === 'logout') {
      logout()
    }
    return action !== undefined
  }

  return { openProfile, logout, handleAccountAction }
}
