<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, StatusColumn, TableColumn, UserColumn, YdTablePageAction } from '@yudream/components'
import type { MessagingIdentity } from '@/api/modules/profile'
import type { DeptManageItem } from '@/api/modules/system-dept'
import type { OptionItem } from '@/api/modules/system-role'
import type { IdValue, UserCreatePayload, UserDeptAssign, UserManageItem, UserStats, UserTagItem, UserUpdatePayload } from '@/api/modules/system-user'
import { useTablePage } from '@yudream/components'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiRole from '@/api/modules/system-role'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import apiUser from '@/api/modules/system-user'
import { useAiAssistantAction } from '@/composables/ai-assistant-actions'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { deptFallbackIcon } from '@/utils/dept-visual'
import { entityTagStyle, entityTintStyle, normalizeEntityColor } from '@/utils/entity-visual'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'
import UserStatsPanel from './components/UserStatsPanel.vue'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()
const route = useRoute()
const router = useRouter()
const appAccountStore = useAppAccountStore()
const assistantCreateUserRef = useAiAssistantAction('system:user:create', '新增用户', 'system:user:create')
const assistantExportUsersRef = useAiAssistantAction('system:user:export', '导出用户', 'system:user:export')

const roles = ref<OptionItem[]>([])
const depts = ref<DeptManageItem[]>([])
/** 角色名 → 代表色映射：用户列表按角色名渲染彩色标签（接口只给 roleNames）。 */
const roleColorByName = shallowRef<Map<string, string>>(new Map())

const formVisible = ref(false)
const assignVisible = ref(false)
const passwordVisible = ref(false)
const assignMode = ref<'roles' | 'depts'>('roles')
const editing = ref<UserManageItem | null>(null)
const selectedRoleIds = ref<IdValue[]>([])
const selectedDeptIds = ref<IdValue[]>([])
const defaultDeptId = ref<IdValue>()
const passwordForm = reactive({
  password: '',
  confirmPassword: '',
})

const form = reactive<UserCreatePayload & UserUpdatePayload>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  qq: '',
  password: '',
  emailVerified: false,
  roleIds: [],
  depts: [],
})

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]
const emailVerifiedOptions = [
  { label: '已验证', value: 'true' },
  { label: '未验证', value: 'false' },
]
const deptOptions = computed(() => flattenDepts(depts.value).map(item => ({ label: item.name, value: item.id })))
const roleOptions = computed(() => roles.value.map(item => ({
  label: item.deptName ? `${item.deptName} / ${item.label}` : item.label,
  value: item.id,
})))

const optionsReloadKey = ref(0)

const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<UserManageItem>({
  queryClass: 'online.yudream.base.application.system.user.query.UserPageQuery',
  loadApi: async (query) => {
    const res = await apiUser.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      roleId: query.roleId || undefined,
      deptId: query.deptId || undefined,
      emailVerified: resolveEmailVerifiedFilter(query.emailVerified),
      status: query.status || undefined,
    })
    return {
      records: res.data.records.map(item => ({
        ...item,
        avatar: item.avatar ? toBackendAssetUrl(item.avatar) : item.avatar,
        defaultDeptAvatar: item.defaultDeptAvatar ? toBackendAssetUrl(item.defaultDeptAvatar) : item.defaultDeptAvatar,
      })),
      total: res.data.total,
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
  autoLoad: false,
})

/** 统计情报区数据：仓储全量聚合，不受当前筛选/分页影响 */
const userStats = ref<UserStats>()
const statsLoading = ref(false)

async function loadStats() {
  if (!auth('system:user:view')) {
    return
  }
  statsLoading.value = true
  try {
    const res = await apiUser.stats()
    userStats.value = res.data
  }
  catch {
    // 统计是"情报板"，不可用时收起即可，不打断列表主流程
    userStats.value = undefined
  }
  finally {
    statsLoading.value = false
  }
}

/** 列表与统计一起刷新：任何写操作后两处都要跟着变 */
async function refreshAll() {
  await Promise.all([load(), loadStats()])
}

/** 搜索选项数据源：部门/角色从接口拉取（角色按当前部门收敛），状态词表本地定义 */
const optionsSources: SearchOptionsSource[] = [
  {
    id: 'deptOptions',
    loader: async () => {
      const res = await apiDept.tree()
      return flattenDepts(res.data).map(item => ({ label: item.name, value: item.id }))
    },
  },
  {
    id: 'roleOptions',
    loader: () => searchQuery.deptId == null
      ? roleOptions.value
      : roleOptionsByDept(searchQuery.deptId),
  },
  { id: 'emailVerifiedOptions', loader: () => emailVerifiedOptions },
  { id: 'userStatusOptions', loader: () => statusOptions },
]

const defaultDeptOptions = computed(() => selectedDeptIds.value.map(id => ({
  label: deptOptions.value.find(item => sameId(item.value, id))?.label || String(id),
  value: id,
})))
const selectedDeptRoleSections = computed(() => selectedDeptIds.value.map(id => ({
  deptId: id,
  deptName: deptOptions.value.find(item => sameId(item.value, id))?.label || String(id),
  roleOptions: roleOptionsByDept(id),
})))
const canAssignRole = computed(() => auth('system:user:assign-role'))
const canAssignDept = computed(() => auth('system:user:assign-dept'))
const canImpersonate = computed(() => auth('system:user:impersonate'))

/** 身份列：用户名 + 邮箱组合，卡片头部与桌面首列共用 */
const userColumn: UserColumn<UserManageItem> = {
  nameKey: 'username',
  emailKey: 'email',
  avatarKey: 'avatar',
  header: '用户',
  width: 240,
}

const userStatusColumn: StatusColumn<UserManageItem> = {
  statusKey: 'status',
  header: '状态',
  width: 90,
  // 语义色胶囊：长列表里能一眼扫出停用/异常行（tone 见 YD 状态令牌说明）
  statusMap: {
    ACTIVE: { label: '启用', variant: 'default', tone: 'success' },
    DISABLED: { label: '停用', variant: 'secondary', tone: 'neutral' },
  },
}

/** 页头操作：新增为主操作，导入导出类收进「更多」；ref 供 AI 助手动作定位按钮 */
const userPageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('system:user:create')
    ? [{ label: '新增用户', icon: 'morph:user-plus', ref: assistantCreateUserRef, onClick: openCreate }]
    : []),
  ...(auth('system:user:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, ref: assistantExportUsersRef, onClick: exportUsers }]
    : []),
  ...(auth('system:user:import')
    ? [
        { label: '模板', icon: 'morph:download', variant: 'outline' as const, autoFold: true, onClick: downloadUserTemplate },
        { label: '导入', icon: 'morph:upload', variant: 'outline' as const, autoFold: true, onClick: importUsers },
      ]
    : []),
])

/** 行操作统一收进下拉，按权限与行状态决定是否出现 */
const userActions: ActionMenuItem<UserManageItem>[] = [
  { label: '编辑', icon: 'morph:pencil', visible: () => auth('system:user:edit'), onClick: openEdit },
  { label: '重置密码', icon: 'morph:key-round', visible: () => auth('system:user:edit'), onClick: openPassword },
  { label: '部门与角色', icon: 'morph:network', visible: () => canAssignDept.value || canAssignRole.value, onClick: row => openAssign(row, 'depts') },
  { label: '伪装登录', icon: 'morph:user-cog', visible: () => auth('system:user:impersonate'), disabled: row => !canImpersonateRow(row), onClick: confirmImpersonate },
  { label: '启用', icon: 'morph:circle-check', visible: row => row.status === 'DISABLED' && auth('system:user:edit'), onClick: confirmEnable },
  { label: '停用', icon: 'morph:ban', danger: true, visible: row => row.status !== 'DISABLED' && auth('system:user:delete'), onClick: confirmDisable },
]

const tableColumns = computed<TableColumn<UserManageItem>[]>(() => [
  { accessorKey: 'nickname', header: '昵称', width: 140 },
  { id: 'identities', header: '消息身份', width: 280 },
  { id: 'fields', header: '扩展资料', width: 240 },
  { id: 'tags', header: '标签', width: 220 },
  { id: 'deptNames', header: '部门', width: 220 },
  { id: 'roleNames', header: '角色', width: 220 },
  { id: 'emailVerified', header: '邮箱验证', width: 100, align: 'center' },
])

onMounted(async () => {
  await loadOptions()
  void loadRoleColors()
  await load()
  void loadStats()
  await openLinkedUser()
})

watch(() => route.query.editUser, () => openLinkedUser())
let linkedUserRequest = 0

async function openLinkedUser() {
  const id = route.query.editUser
  if (typeof id !== 'string' || !id || !auth('system:user:view') || !auth('system:user:edit')) {
    return
  }
  const request = ++linkedUserRequest
  try {
    const response = await apiUser.detail(id)
    if (request === linkedUserRequest && route.query.editUser === id) {
      openEdit(response.data)
    }
  }
  finally {
    if (request === linkedUserRequest && route.query.editUser === id) {
      const query = { ...route.query }
      delete query.editUser
      await router.replace({ query })
    }
  }
}

watch([selectedDeptIds, roles], () => {
  normalizeDefaultDept()
  pruneSelectedRoleIdsByDepts()
})

watch(() => searchQuery.deptId, () => {
  // 角色选项按部门收敛：触发选项重载并清掉不再可选的已选角色
  optionsReloadKey.value++
  if (searchQuery.roleId != null && !roleOptionsByDept(searchQuery.deptId).some(item => sameId(item.value, searchQuery.roleId))) {
    searchQuery.roleId = undefined
  }
})

async function loadOptions() {
  const [roleRes, deptRes] = await Promise.all([apiRole.options(), apiDept.tree()])
  roles.value = roleRes.data
  depts.value = deptRes.data
}

/** 角色名 → 代表色：分页拉全量角色后建映射，带短 TTL 缓存避免每次进页重复请求。 */
const ROLE_COLOR_CACHE_TTL = 30_000
let roleColorCache: { at: number, map: Map<string, string> } | undefined

async function loadRoleColors() {
  try {
    if (!roleColorCache || Date.now() - roleColorCache.at > ROLE_COLOR_CACHE_TTL) {
      const map = new Map<string, string>()
      const size = 200
      for (let page = 1; page <= 20; page++) {
        const res = await apiRole.page({ page, size })
        for (const role of res.data.records) {
          const color = normalizeEntityColor(role.color)
          if (color) {
            map.set(role.name, color)
          }
        }
        if (res.data.records.length < size || page * size >= res.data.total) {
          break
        }
      }
      roleColorCache = { at: Date.now(), map }
    }
    roleColorByName.value = roleColorCache.map
  }
  catch {
    // 无角色查看权限或接口失败时保持空映射，标签按中性色渲染
    roleColorByName.value = new Map()
  }
}

/** 部门 ID → 部门数据；用户列表的部门 chip 用它取主色与上传头像。 */
const deptById = computed(() => {
  const map = new Map<string, DeptManageItem>()
  for (const item of flattenDepts(depts.value)) {
    map.set(String(item.id), item)
  }
  return map
})

function roleColorOf(name: string) {
  return roleColorByName.value.get(name)
}

function roleTagStyle(name: string) {
  return entityTagStyle(roleColorOf(name))
}

function deptOf(deptId: IdValue) {
  return deptById.value.get(String(deptId))
}

/** 部门头像：默认部门优先用接口给的 defaultDeptAvatar，其余部门取部门树里的上传头像。 */
function deptAvatarOf(row: UserManageItem, deptId: IdValue) {
  if (sameId(deptId, row.defaultDeptId) && row.defaultDeptAvatar) {
    return toBackendAssetUrl(row.defaultDeptAvatar)
  }
  const dept = deptOf(deptId)
  return dept?.avatar ? toBackendAssetUrl(dept.avatar) : ''
}

function deptChipStyle(deptId: IdValue) {
  return entityTintStyle(deptOf(deptId)?.color)
}

function deptChipIcon(deptId: IdValue, deptName?: string) {
  return deptFallbackIcon(deptName || deptOf(deptId)?.name)
}

function deptChipFallback(deptId: IdValue, deptName?: string) {
  const name = (deptName || deptOf(deptId)?.name || '').trim()
  return name ? name.slice(0, 2) : '部门'
}

function resolveEmailVerifiedFilter(value?: string) {
  if (value === 'true') {
    return true
  }
  if (value === 'false') {
    return false
  }
  return undefined
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    qq: '',
    password: '',
    emailVerified: false,
    roleIds: [],
    depts: [],
  })
  selectedRoleIds.value = []
  selectedDeptIds.value = []
  defaultDeptId.value = undefined
  formVisible.value = true
}

function openEdit(row: UserManageItem) {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    qq: row.qq,
    password: '',
    emailVerified: row.emailVerified,
    roleIds: row.roleIds,
    depts: [],
  })
  selectedRoleIds.value = [...row.roleIds]
  selectedDeptIds.value = [...row.deptIds]
  defaultDeptId.value = row.defaultDeptId ?? row.deptIds[0]
  normalizeDefaultDept()
  formVisible.value = true
}

async function saveForm() {
  if (editing.value) {
    const updateRes = await apiUser.update(editing.value.id, {
      username: form.username,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      qq: form.qq,
      emailVerified: form.emailVerified,
    })
    if (sameId(updateRes.data.id, appAccountStore.userId)) {
      appAccountStore.setAccount(updateRes.data.username)
    }
    const deptsPayload = canAssignDept.value ? buildDeptPayload() : []
    if (canAssignDept.value && !sameDeptAssigns(deptsPayload, editing.value)) {
      await apiUser.assignDepts(editing.value.id, deptsPayload)
    }
    if (canAssignRole.value && !sameIdArray(selectedRoleIds.value, editing.value.roleIds)) {
      await apiUser.assignRoles(editing.value.id, selectedRoleIds.value)
    }
  }
  else {
    const deptsPayload = buildDeptPayload()
    await apiUser.create({ ...form, roleIds: selectedRoleIds.value, depts: deptsPayload })
  }
  toast.success(editing.value ? '编辑成功' : '新增成功')
  formVisible.value = false
  await refreshAll()
}

function openAssign(row: UserManageItem, mode: 'roles' | 'depts') {
  editing.value = row
  assignMode.value = mode
  selectedRoleIds.value = [...row.roleIds]
  selectedDeptIds.value = [...row.deptIds]
  defaultDeptId.value = row.defaultDeptId ?? row.deptIds[0]
  assignVisible.value = true
}

async function saveAssign() {
  if (!editing.value) {
    return
  }
  if (!canAssignDept.value && !canAssignRole.value) {
    toast.warning('没有分配部门角色权限')
    return
  }
  if (canAssignDept.value) {
    await apiUser.assignDepts(editing.value.id, buildDeptPayload())
  }
  if (canAssignRole.value) {
    await apiUser.assignRoles(editing.value.id, selectedRoleIds.value)
  }
  toast.success('分配成功')
  assignVisible.value = false
  await refreshAll()
}

function canImpersonateRow(row: UserManageItem) {
  return canImpersonate.value && row.status === 'ACTIVE' && row.username !== appAccountStore.account && !appAccountStore.isImpersonating
}

function confirmImpersonate(row: UserManageItem) {
  if (!canImpersonateRow(row)) {
    toast.warning('当前用户不可伪装访问')
    return
  }
  modal.confirm({
    title: '伪装访问',
    content: `确认以「${row.nickname || row.username}」身份访问系统吗？当前管理员会话会被临时保存，可在右上角退出伪装。`,
    onConfirm: async () => {
      const res = await apiUser.impersonate(row.id)
      await appAccountStore.impersonate(res.data)
    },
  })
}

function confirmDisable(row: UserManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认停用「${row.username}」吗？`,
    onConfirm: async () => {
      await apiUser.disable(row.id)
      toast.success('停用成功')
      await refreshAll()
    },
  })
}

function confirmEnable(row: UserManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认启用「${row.username}」吗？`,
    onConfirm: async () => {
      await apiUser.enable(row.id)
      toast.success('启用成功')
      await refreshAll()
    },
  })
}

function openPassword(row: UserManageItem) {
  editing.value = row
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function savePassword() {
  if (!editing.value) {
    return
  }
  if (!passwordForm.password) {
    toast.warning('请输入新密码')
    return
  }
  if (passwordForm.password !== passwordForm.confirmPassword) {
    toast.warning('两次输入的密码不一致')
    return
  }
  await apiUser.changePassword(editing.value.id, passwordForm.password)
  toast.success('密码已修改')
  passwordVisible.value = false
}

function buildDeptPayload(): UserDeptAssign[] {
  normalizeDefaultDept()
  return selectedDeptIds.value.map(id => ({ deptId: id, defaultDept: sameId(id, defaultDeptId.value) }))
}

function sameIdArray(left: IdValue[], right: IdValue[]) {
  const sortedLeft = left.map(String).sort()
  const sortedRight = right.map(String).sort()
  return sortedLeft.length === sortedRight.length && sortedLeft.every((item, index) => item === sortedRight[index])
}

function personnelFields(row: UserManageItem) {
  const result: { key: string, label: string, value: string }[] = []
  for (const [namespace, fields] of Object.entries(row.fields || {})) {
    for (const [code, value] of Object.entries(fields)) {
      if (code.endsWith('__label') || !value) {
        continue
      }
      result.push({
        key: `${namespace}:${code}`,
        label: fields[`${code}__label`] || code,
        value,
      })
    }
  }
  return result
}

function sameId(left?: IdValue, right?: IdValue) {
  return String(left ?? '') === String(right ?? '')
}

function protocolLabel(protocol?: string) {
  if (protocol === 'official') {
    return '官方 QQ'
  }
  if (protocol === 'milky') {
    return 'Milky'
  }
  return protocol || '消息协议'
}

function identityTypeLabel(type?: string) {
  if (type === 'qq') {
    return 'QQ 号'
  }
  if (type === 'user_openid') {
    return '用户 OpenID'
  }
  if (type === 'member_openid') {
    return '群成员 OpenID'
  }
  return type || '身份'
}

/** 长标识（OpenID 等）中间截断，完整值靠 title 悬浮查看 */
function truncateMiddle(value?: string | null, head = 8, tail = 4) {
  if (!value) {
    return ''
  }
  if (value.length <= head + tail + 1) {
    return value
  }
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

function identityKey(item: MessagingIdentity, index: string | number) {
  return [item.protocol, item.identityType, item.identity, item.groupOpenid, item.appId, item.connectionId, String(index)].join(':')
}

function sameDeptAssigns(payload: UserDeptAssign[], row: UserManageItem) {
  return sameIdArray(payload.map(item => item.deptId), row.deptIds) && sameId(defaultDeptId.value, row.defaultDeptId ?? row.deptIds[0])
}

function roleOptionsByDept(deptId: IdValue) {
  return roles.value
    .filter(role => sameId(role.deptId, deptId))
    .map(role => ({ label: role.label, value: role.id }))
}

function roleDeptId(roleId: IdValue) {
  return roles.value.find(role => sameId(role.id, roleId))?.deptId
}

function getDeptRoleIds(deptId: IdValue) {
  return selectedRoleIds.value.filter(roleId => sameId(roleDeptId(roleId), deptId))
}

function setDeptRoleIds(deptId: IdValue, roleIds: IdValue[]) {
  const otherDeptRoleIds = selectedRoleIds.value.filter(roleId => !sameId(roleDeptId(roleId), deptId))
  selectedRoleIds.value = [...otherDeptRoleIds, ...roleIds]
}

function pruneSelectedRoleIdsByDepts() {
  selectedRoleIds.value = selectedRoleIds.value.filter((roleId) => {
    const deptId = roleDeptId(roleId)
    return deptId != null && selectedDeptIds.value.some(selectedDeptId => sameId(selectedDeptId, deptId))
  })
}

function normalizeDefaultDept() {
  if (!selectedDeptIds.value.length) {
    defaultDeptId.value = undefined
    return
  }
  if (!defaultDeptId.value || !selectedDeptIds.value.some(id => sameId(id, defaultDeptId.value))) {
    defaultDeptId.value = selectedDeptIds.value[0]
  }
}

function flattenDepts(items: DeptManageItem[]): DeptManageItem[] {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

function userTagVariant(tag: UserTagItem) {
  if (tag.code === 'status') {
    if (tag.label === '已认证') {
      return 'default'
    }
    if (tag.label === '审核中') {
      return 'outline'
    }
    return 'secondary'
  }
  return 'default'
}

async function exportUsers() {
  const res = await apiExcel.exportUsers({
    keyword: searchQuery.keyword || undefined,
    roleId: searchQuery.roleId || undefined,
    deptId: searchQuery.deptId || undefined,
    emailVerified: resolveEmailVerifiedFilter(searchQuery.emailVerified),
    status: searchQuery.status || undefined,
  })
  saveExcelResponse(res, '用户管理.xlsx')
}

async function downloadUserTemplate() {
  const res = await apiExcel.userTemplate()
  saveExcelResponse(res, '用户导入模板.xlsx')
}

function importUsers() {
  pickExcelFile(async (file) => {
    const res = await apiExcel.importUsers(excelForm(file))
    toast.success(importResultMessage(res.data))
    await refreshAll()
  })
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="用户管理"
      :actions="userPageActions"
      :actions-threshold="3"
    >
      <template #stats="{ narrow, expanded }">
        <UserStatsPanel :stats="userStats" :loading="statsLoading" :narrow="narrow" :expanded="expanded" />
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          :options-sources="optionsSources"
          :reload-key="optionsReloadKey"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1280px]"
            column-visibility
            :user-column="userColumn"
            :status-column="userStatusColumn"
            :actions="userActions"
            :card-hidden-columns="['identities', 'fields']"
            :columns="tableColumns"
            :data="rows"
          >
            <template #cell-identities="{ row }">
              <div v-if="row.original.qq || row.original.messagingIdentities?.length" class="text-sm flex flex-col gap-1">
                <div v-if="row.original.qq" class="flex gap-2">
                  <span class="text-secondary-foreground/60 shrink-0">QQ 号</span>
                  <span class="break-all">{{ row.original.qq }}</span>
                </div>
                <div v-for="(item, index) in row.original.messagingIdentities || []" :key="identityKey(item, index)" class="flex gap-2">
                  <span class="text-secondary-foreground/60 shrink-0">{{ protocolLabel(item.protocol) }}</span>
                  <span class="break-all">
                    {{ identityTypeLabel(item.identityType) }}
                    <span :title="item.identity">{{ truncateMiddle(item.identity) }}</span>{{ item.groupOpenid ? ` / 群 ${truncateMiddle(item.groupOpenid)}` : '' }}
                  </span>
                </div>
              </div>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-fields="{ row }">
              <div v-if="personnelFields(row.original).length" class="text-sm flex flex-col gap-1">
                <div v-for="field in personnelFields(row.original)" :key="field.key" class="flex gap-2">
                  <span class="text-secondary-foreground/60 shrink-0">{{ field.label }}</span>
                  <span class="break-all">{{ field.value }}</span>
                </div>
              </div>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-tags="{ row }">
              <div v-if="row.original.tags?.length" class="flex flex-wrap gap-1">
                <YdTag v-for="tag in row.original.tags" :key="`${tag.namespace}:${tag.code}`" :variant="userTagVariant(tag)">
                  {{ tag.label }}
                </YdTag>
              </div>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-deptNames="{ row }">
              <div v-if="row.original.deptIds?.length" class="flex flex-wrap gap-1">
                <YdTag
                  v-for="(deptId, index) in row.original.deptIds"
                  :key="deptId"
                  :variant="sameId(deptId, row.original.defaultDeptId) ? 'default' : 'secondary'"
                >
                  <YdAvatar
                    v-if="deptAvatarOf(row.original, deptId)"
                    :src="deptAvatarOf(row.original, deptId)"
                    :fallback="deptChipFallback(deptId, row.original.deptNames?.[index])"
                    :alt="row.original.deptNames?.[index] || deptId"
                    class="shrink-0 size-4"
                  />
                  <span v-else class="dept-chip" :style="deptChipStyle(deptId)" aria-hidden="true">
                    <YdIcon :name="deptChipIcon(deptId, row.original.deptNames?.[index])" />
                  </span>
                  {{ row.original.deptNames?.[index] || deptId }}{{ sameId(deptId, row.original.defaultDeptId) ? '（默认）' : '' }}
                </YdTag>
              </div>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-roleNames="{ row }">
              <div v-if="row.original.roleNames?.length" class="flex flex-wrap gap-1 items-center">
                <YdTag :style="roleTagStyle(row.original.roleNames[0])">
                  {{ row.original.roleNames[0] }}
                </YdTag>
                <!-- 多角色只显示第一个 + “+N”，悬浮展开完整清单，避免列被标签撑爆 -->
                <YdTooltip
                  v-if="row.original.roleNames.length > 1"
                  :text="row.original.roleNames.slice(1).join('、')"
                >
                  <YdTag variant="secondary" class="cursor-default">
                    +{{ row.original.roleNames.length - 1 }}
                  </YdTag>
                </YdTooltip>
              </div>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-emailVerified="{ row }">
              <span class="yd-status-tone" :data-tone="row.original.emailVerified ? 'success' : 'warning'">
                {{ row.original.emailVerified ? '已验证' : '未验证' }}
              </span>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>

    <YdModal v-model="formVisible" :title="editing ? '编辑用户' : '新增用户'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <YdForm :model="form" layout="vertical">
        <div class="gap-4 grid grid-cols-1 sm:grid-cols-2">
          <div>
            <YdFormItem label="用户名" required>
              <YdInput v-model="form.username" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="昵称">
              <YdInput v-model="form.nickname" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="邮箱" required>
              <YdInput v-model="form.email" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="手机号">
              <YdInput v-model="form.phone" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="QQ 号">
              <YdInput v-model="form.qq" class="w-full" placeholder="仅 Milky 协议使用真实 QQ 号" />
            </YdFormItem>
          </div>
          <div v-if="!editing">
            <YdFormItem label="密码" required>
              <YdInput v-model="form.password" type="password" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="邮箱状态">
              <div class="flex gap-3 h-9 items-center">
                <YdSwitch v-model="form.emailVerified" />
                <span class="text-sm text-muted-foreground">{{ form.emailVerified ? '已验证' : '未验证' }}</span>
              </div>
            </YdFormItem>
          </div>
        </div>
        <YdFormItem v-auth="'system:user:assign-dept'" label="所属部门">
          <YdSelect v-model="selectedDeptIds" multiple :options="deptOptions" class="w-full" />
        </YdFormItem>
        <YdFormItem v-auth="'system:user:assign-dept'" label="默认部门">
          <YdSelect v-model="defaultDeptId" :disabled="!selectedDeptIds.length" :options="defaultDeptOptions" class="w-full" />
        </YdFormItem>
        <YdFormItem v-auth="'system:user:assign-role'" label="部门角色">
          <div v-if="selectedDeptRoleSections.length" class="w-full space-y-3">
            <div v-for="section in selectedDeptRoleSections" :key="section.deptId" class="p-3 border rounded-md">
              <div class="text-sm font-medium mb-2 flex gap-2 items-center">
                <YdIcon name="morph:network" />
                {{ section.deptName }}
              </div>
              <YdSelect
                v-if="section.roleOptions.length"
                :model-value="getDeptRoleIds(section.deptId)"
                multiple
                :options="section.roleOptions"
                class="w-full"
                @update:model-value="setDeptRoleIds(section.deptId, $event as IdValue[])"
              />
              <div v-else class="text-sm text-muted-foreground">
                该部门暂无可分配角色
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground">
            请先选择所属部门
          </div>
        </YdFormItem>
      </YdForm>
    </YdModal>

    <YdModal v-model="passwordVisible" title="修改密码" show-cancel-button class="sm:max-w-lg" @confirm="savePassword">
      <YdForm :model="passwordForm" layout="vertical">
        <YdFormItem label="新密码" required>
          <YdInput v-model="passwordForm.password" type="password" class="w-full" />
        </YdFormItem>
        <YdFormItem>
          <YdPasswordStrength :password="passwordForm.password" />
        </YdFormItem>
        <YdFormItem label="确认密码" required>
          <YdInput v-model="passwordForm.confirmPassword" type="password" class="w-full" />
        </YdFormItem>
      </YdForm>
    </YdModal>

    <YdModal v-model="assignVisible" title="分配部门角色" show-cancel-button class="sm:max-w-2xl" @confirm="saveAssign">
      <YdForm :model="{ selectedDeptIds, defaultDeptId }" layout="vertical">
        <YdFormItem v-auth="'system:user:assign-dept'" label="部门">
          <YdSelect v-model="selectedDeptIds" multiple :options="deptOptions" class="w-full" />
        </YdFormItem>
        <YdFormItem v-auth="'system:user:assign-dept'" label="默认部门">
          <YdSelect v-model="defaultDeptId" :disabled="!selectedDeptIds.length" :options="defaultDeptOptions" class="w-full" />
        </YdFormItem>
        <YdFormItem v-auth="'system:user:assign-role'" label="部门角色">
          <div v-if="selectedDeptRoleSections.length" class="w-full space-y-3">
            <div v-for="section in selectedDeptRoleSections" :key="section.deptId" class="p-3 border rounded-md">
              <div class="text-sm font-medium mb-2 flex gap-2 items-center">
                <YdIcon name="morph:network" />
                {{ section.deptName }}
              </div>
              <YdSelect
                v-if="section.roleOptions.length"
                :model-value="getDeptRoleIds(section.deptId)"
                multiple
                :options="section.roleOptions"
                class="w-full"
                @update:model-value="setDeptRoleIds(section.deptId, $event as IdValue[])"
              />
              <div v-else class="text-sm text-muted-foreground">
                该部门暂无可分配角色
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground">
            请先选择所属部门
          </div>
        </YdFormItem>
      </YdForm>
    </YdModal>
  </div>
</template>

<style scoped>
/*
  部门 chip 内的兜底图标：部门没有上传头像时按名称映射 Morphicons，
  底色与图标色取部门主色（内联 style 只承载数据色），未设色回退中性语义变量。
*/
.dept-chip {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  font-size: 11px;
  line-height: 1;
  border-radius: 4px;
}
</style>
