import type { DeptItem, IdValue, LoginData, RoleItem } from '@/api/modules/user'
import apiApp from '@/api/modules/app'
import apiUser from '@/api/modules/user'
import router from '@/router'
import { toBackendAssetUrl } from '@/utils/backend-url'

function parseStoredItem<T>(key: string): T | null {
  const raw = localStorage.getItem(key)
  if (!raw || raw === 'null' || raw === 'undefined') {
    return null
  }
  try {
    return JSON.parse(raw) as T
  }
  catch {
    return null
  }
}

interface AccountSession {
  token: string
  refreshToken?: string
  account: string
  avatar: string
  currentDept: DeptItem | null
  currentRole: RoleItem | null
}

const IMPERSONATOR_SESSION_KEY = 'impersonatorSession'

export const useAppAccountStore = defineStore('appAccount', () => {
  const appSettingsStore = useAppSettingsStore()
  const appTabbarStore = useAppTabbarStore()
  const appRouteStore = useAppRouteStore()
  const appMenuStore = useAppMenuStore()

  // 账号信息
  const token = ref(localStorage.getItem('token') ?? '')
  const refreshToken = ref(localStorage.getItem('refreshToken') ?? '')
  const account = ref(localStorage.getItem('account') ?? '')
  const avatar = ref(localStorage.getItem('avatar') ?? '')

  // 当前部门/角色
  const currentDept = ref<DeptItem | null>(parseStoredItem<DeptItem>('currentDept'))
  const currentRole = ref<RoleItem | null>(parseStoredItem<RoleItem>('currentRole'))

  // 权限信息
  const permissions = ref<string[]>([])
  const impersonatorSession = ref<AccountSession | null>(parseStoredItem<AccountSession>(IMPERSONATOR_SESSION_KEY))

  // 登录状态
  const isLogin = computed(() => {
    if (token.value) {
      return true
    }
    return false
  })
  const isImpersonating = computed(() => !!impersonatorSession.value)
  const impersonatorAccount = computed(() => impersonatorSession.value?.account ?? '')

  // 登录
  async function login(data: {
    account: string
    password: string
  }) {
    const res = await apiUser.login(data)
    clearImpersonatorSession()
    applyLoginData(res.data)
    await loadContext()
  }

  async function refreshAccessToken() {
    if (!refreshToken.value) {
      throw new Error('缺少刷新令牌')
    }
    const res = await apiUser.refreshToken(refreshToken.value)
    localStorage.setItem('token', res.data.token)
    token.value = res.data.token
    if (res.data.refreshToken) {
      localStorage.setItem('refreshToken', res.data.refreshToken)
      refreshToken.value = res.data.refreshToken
    }
    return res.data.token
  }

  function applyLoginData(user: LoginData) {
    const nextAvatar = toBackendAssetUrl(user.avatar)
    localStorage.setItem('account', user.username)
    localStorage.setItem('token', user.token)
    if (user.refreshToken) {
      localStorage.setItem('refreshToken', user.refreshToken)
    }
    else {
      localStorage.removeItem('refreshToken')
    }
    if (nextAvatar) {
      localStorage.setItem('avatar', nextAvatar)
    }
    else {
      localStorage.removeItem('avatar')
    }
    account.value = user.username
    token.value = user.token
    refreshToken.value = user.refreshToken || ''
    avatar.value = nextAvatar
    setCurrentDept(null)
    setCurrentRole(null)
  }

  function getCurrentSession(): AccountSession {
    return {
      token: token.value,
      refreshToken: refreshToken.value,
      account: account.value,
      avatar: avatar.value,
      currentDept: currentDept.value,
      currentRole: currentRole.value,
    }
  }

  function saveImpersonatorSession(session: AccountSession) {
    impersonatorSession.value = session
    localStorage.setItem(IMPERSONATOR_SESSION_KEY, JSON.stringify(session))
  }

  function clearImpersonatorSession() {
    impersonatorSession.value = null
    localStorage.removeItem(IMPERSONATOR_SESSION_KEY)
  }

  async function reloadWithFreshRoutes(path = appSettingsStore.settings.app.home.fullPath) {
    appTabbarStore.clean()
    appRouteStore.removeRoutes()
    appMenuStore.setActived(0)
    await router.replace(path)
    window.location.reload()
  }

  async function impersonate(user: LoginData) {
    if (!isImpersonating.value) {
      saveImpersonatorSession(getCurrentSession())
    }
    applyLoginData(user)
    await loadContext()
    await reloadWithFreshRoutes()
  }

  async function exitImpersonation() {
    const session = impersonatorSession.value
    if (!session) {
      return
    }
    localStorage.setItem('token', session.token)
    if (session.refreshToken) {
      localStorage.setItem('refreshToken', session.refreshToken)
    }
    else {
      localStorage.removeItem('refreshToken')
    }
    localStorage.setItem('account', session.account)
    if (session.avatar) {
      localStorage.setItem('avatar', session.avatar)
    }
    else {
      localStorage.removeItem('avatar')
    }
    token.value = session.token
    refreshToken.value = session.refreshToken || ''
    account.value = session.account
    avatar.value = session.avatar
    setCurrentDept(session.currentDept)
    setCurrentRole(session.currentRole)
    clearImpersonatorSession()
    await reloadWithFreshRoutes()
  }

  // 注册
  async function register(data: {
    username: string
    email: string
    password: string
    nickname?: string
  }) {
    await apiUser.register(data)
  }

  // 加载当前部门/角色上下文
  async function loadContext() {
    const res = await apiUser.getContext()
    const ctx = res.data
    setCurrentDept(ctx.currentDept)
    setCurrentRole(ctx.currentRole)
  }

  function setCurrentDept(dept: DeptItem | null) {
    currentDept.value = dept
    if (dept) {
      localStorage.setItem('currentDept', JSON.stringify(dept))
    }
    else {
      localStorage.removeItem('currentDept')
    }
  }

  function setCurrentRole(role: RoleItem | null) {
    currentRole.value = role
    if (role) {
      localStorage.setItem('currentRole', JSON.stringify(role))
    }
    else {
      localStorage.removeItem('currentRole')
    }
  }

  // 切换部门
  async function switchDept(deptId: IdValue) {
    await apiUser.switchDept(deptId)
    await loadContext()
  }

  // 切换角色
  async function switchRole(roleId: IdValue) {
    await apiUser.switchRole(roleId)
    await loadContext()
  }

  // 手动登出
  function logout(redirect = router.currentRoute.value.fullPath) {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    clearImpersonatorSession()
    token.value = ''
    refreshToken.value = ''
    router.push({
      name: 'login',
      query: {
        ...(redirect !== appSettingsStore.settings.app.home.fullPath && router.currentRoute.value.name !== 'login' && { redirect }),
      },
    }).then(logoutCleanStatus)
  }

  // 请求登出
  function requestLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    clearImpersonatorSession()
    token.value = ''
    refreshToken.value = ''
    router.push({
      name: 'login',
      query: {
        ...(
          router.currentRoute.value.fullPath !== appSettingsStore.settings.app.home.fullPath
          && router.currentRoute.value.name !== 'login'
          && {
            redirect: router.currentRoute.value.fullPath,
          }
        ),
      },
    }).then(logoutCleanStatus)
  }

  // 登出后清除状态
  function logoutCleanStatus() {
    localStorage.removeItem('account')
    localStorage.removeItem('avatar')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('currentDept')
    localStorage.removeItem('currentRole')
    account.value = ''
    avatar.value = ''
    currentDept.value = null
    currentRole.value = null
    permissions.value = []
    appSettingsStore.updateSettings({}, true)
    appTabbarStore.clean()
    appRouteStore.removeRoutes()
    appMenuStore.setActived(0)
  }

  // 获取权限
  async function getPermissions() {
    const res = await apiApp.permission()
    permissions.value = res.data.permissions
  }

  // 修改密码
  async function editPassword(data: {
    password: string
    newPassword: string
  }) {
    await apiApp.passwordEdit(data)
  }

  function setAvatar(value?: string) {
    avatar.value = toBackendAssetUrl(value)
    if (avatar.value) {
      localStorage.setItem('avatar', avatar.value)
    }
    else {
      localStorage.removeItem('avatar')
    }
  }

  return {
    token,
    refreshToken,
    account,
    avatar,
    currentDept,
    currentRole,
    permissions,
    isLogin,
    isImpersonating,
    impersonatorAccount,
    login,
    refreshAccessToken,
    impersonate,
    exitImpersonation,
    register,
    loadContext,
    switchDept,
    switchRole,
    logout,
    requestLogout,
    getPermissions,
    editPassword,
    setAvatar,
  }
})
