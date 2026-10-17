<script setup lang="ts">
import type { YdFilePickerInstance, YdListToolbarStatusItem } from '@yudream/components'
import type { PluginDependencyStatus, PluginDependents, PluginModule, PluginModulePageParams, PluginModuleSummary, PluginStatus } from '@/api/modules/platform-plugin'
import type { PluginMarketplaceBatchInstallItem, PluginMarketplaceUpdatePlan } from '@/api/modules/platform-plugin-marketplace'
import { YdFilterSelect, YdListToolbar } from '@yudream/components'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'
import { refreshPluginThemes } from '@/plugins/theme-runtime'
import router from '@/router'
import { refreshDynamicRoutes } from '@/router/dynamic'
import DependentsConfirmDialog from './dependents-confirm-dialog.vue'
import EnableDependencyDialog from './enable-dependency-dialog.vue'

type PluginFilterStatus = 'ALL' | PluginStatus
type PluginUpdateFilter = NonNullable<PluginModulePageParams['updateState']>
type PluginSortKey = NonNullable<PluginModulePageParams['sort']>

interface PluginDependencyRow {
  code: string
  name: string
  icon: string
  required: boolean
  available: boolean
  loaded: boolean
  enabled: boolean
  version?: string
}

const toast = useYdToast()
const modal = useYdModal()
const loading = ref(false)
const uploading = ref(false)
const checkingUpdates = ref(false)
const updatingAll = ref(false)
/** 更新计划已检查过（含进页面时的静默检查）：未检查前不谎报「可更新」数量。 */
const updatesChecked = ref(false)
const actionLoading = ref('')
const rows = ref<PluginModule[]>([])
const summary = ref<PluginModuleSummary>({ total: 0, statusCounts: { INSTALLED: 0, LOADED: 0, ENABLED: 0, DISABLED: 0, ERROR: 0 } })
const updatePlans = ref<PluginMarketplaceUpdatePlan[]>([])
const selectedCode = ref('')
/** 选中插件的最后已知快照：翻页或筛选后目标不在当前页时，详情仍保持可用。 */
const selectedSnapshot = ref<PluginModule | null>(null)
const detailOpen = ref(false)
const keyword = ref('')
const filterStatus = ref<PluginFilterStatus>('ALL')
const updateFilter = ref<PluginUpdateFilter>('ALL')
const sortKey = ref<PluginSortKey>('DEFAULT')
const pagination = reactive({ page: 1, size: 10, total: 0 })
const uploadInput = ref<YdFilePickerInstance | null>(null)
const enableDialogOpen = ref(false)
const enableDependencyStatus = ref<PluginDependencyStatus | null>(null)
const enableLoading = ref(false)
const dependentsDialogOpen = ref(false)
const dependentsInfo = ref<PluginDependents | null>(null)
const dependentsAction = ref<'unload' | 'delete'>('unload')

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value) ?? selectedSnapshot.value)
const catalogRef = ref<HTMLElement>()
const workbenchRef = ref<HTMLElement>()
const hasMoreBelow = ref(false)
let keywordTimer: ReturnType<typeof setTimeout> | undefined

/**
 * 两栏高度按真实位置算：页头 + 工具栏的行数会随视口宽度变化（按钮换行、状态胶囊换行），
 * 用固定减法会让工作台超出可视区，底部翻页条被挤到屏幕外。
 */
function syncPaneHeight() {
  const element = workbenchRef.value
  if (!element) {
    return
  }
  if (window.innerWidth <= 900) {
    element.style.removeProperty('--plugin-pane-max-height')
    return
  }
  // 预留页面容器自身的内边距与外边距（YdPageMain 的 p-4/m-4），否则文档仍会多出一小段滚动。
  const available = Math.round(window.innerHeight - element.getBoundingClientRect().top - 34)
  element.style.setProperty('--plugin-pane-max-height', `${Math.max(320, available)}px`)
}

const availableUpdateCount = computed(() => updatePlans.value.filter(plan => !plan.blockedReason).length)
const blockedUpdateCount = computed(() => updatePlans.value.filter(plan => Boolean(plan.blockedReason)).length)
const updatablePlans = computed(() => updatePlans.value.filter(plan => !plan.blockedReason))
const errorCount = computed(() => summary.value.statusCounts?.ERROR || 0)

/** 状态计数胶囊：计数即筛选，一行取代四张统计卡；六个取值互斥，比例条不会被重复计数。 */
const statusChips = computed<YdListToolbarStatusItem[]>(() => {
  const counts = summary.value.statusCounts
  return [
    { value: 'ALL', label: '全部', count: summary.value.total, tone: 'neutral' },
    { value: 'ENABLED', label: '运行中', count: counts.ENABLED || 0, tone: 'ok' },
    { value: 'LOADED', label: '已加载', count: counts.LOADED || 0, tone: 'muted' },
    { value: 'INSTALLED', label: '已安装', count: counts.INSTALLED || 0, tone: 'muted' },
    { value: 'DISABLED', label: '已禁用', count: counts.DISABLED || 0, tone: 'muted' },
    { value: 'ERROR', label: '异常', count: counts.ERROR || 0, tone: errorCount.value > 0 ? 'bad' : 'muted' },
  ]
})

const updateOptions = computed(() => [
  { label: `全部更新状态 (${updatePlans.value.length})`, value: 'ALL' as const },
  { label: `可更新 (${availableUpdateCount.value})`, value: 'AVAILABLE' as const },
  { label: `更新受阻 (${blockedUpdateCount.value})`, value: 'BLOCKED' as const },
])
const sortOptions: { label: string, value: PluginSortKey }[] = [
  { label: '默认顺序', value: 'DEFAULT' },
  { label: '按状态', value: 'STATUS' },
  { label: '按名称', value: 'NAME' },
  { label: '最近加载', value: 'RECENT' },
]

const hasActiveFilters = computed(() =>
  Boolean(keyword.value.trim())
  || filterStatus.value !== 'ALL'
  || updateFilter.value !== 'ALL',
)

/** 依赖（硬依赖 + 软依赖）：标注是否已安装/启用，缺失项排前面。 */
const dependencyRows = computed<PluginDependencyRow[]>(() => {
  const item = selected.value
  if (!item) {
    return []
  }
  const build = (code: string, required: boolean): PluginDependencyRow => {
    const dependency = rows.value.find(row => row.code === code)
    return {
      code,
      required,
      name: dependency?.name || code,
      icon: dependency ? pluginIcon(dependency) : 'morph:puzzle',
      available: Boolean(dependency),
      loaded: Boolean(dependency?.loaded),
      enabled: Boolean(dependency?.enabled),
      version: dependency?.version,
    }
  }
  return [
    ...(item.dependencies || []).map(code => build(code, true)),
    ...(item.softDependencies || []).map(code => build(code, false)),
  ].sort((a, b) => dependencyWeight(a) - dependencyWeight(b) || a.name.localeCompare(b.name, 'zh-Hans-CN'))
})

function dependencyWeight(row: PluginDependencyRow) {
  if (!row.available) {
    return 0
  }
  return row.enabled ? 2 : 1
}

const dependencyReady = computed(() => dependencyRows.value.filter(item => item.enabled).length)
const missingRequired = computed(() => dependencyRows.value.filter(item => item.required && !item.enabled))

onMounted(async () => {
  await Promise.all([loadPage(), loadSummary()])
  syncPaneHeight()
  window.addEventListener('resize', syncPaneHeight)
  // 更新检查只读市场源索引，进页面就静默跑一次：可更新徽标与筛选计数无需先点按钮。
  await checkUpdates({ silent: true })
})

onBeforeUnmount(() => {
  clearTimeout(keywordTimer)
  window.removeEventListener('resize', syncPaneHeight)
})

// 关键词输入防抖：服务端搜索不必每敲一个字就打一次接口。
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    pagination.page = 1
    loadPage()
  }, 300)
})

watch([filterStatus, updateFilter, sortKey], () => {
  pagination.page = 1
  loadPage()
})

watch([() => pagination.page, () => pagination.size], () => {
  loadPage({ keepSelection: true })
})

function pageParams(): PluginModulePageParams {
  return {
    page: pagination.page,
    size: pagination.size,
    keyword: keyword.value.trim() || undefined,
    status: filterStatus.value,
    updateState: updateFilter.value,
    sort: sortKey.value,
  }
}

/** 目录按页拉取：筛选、排序与分页都在服务端完成，前端只渲染当前页。 */
async function loadPage(options: { keepSelection?: boolean } = {}) {
  loading.value = true
  try {
    const res = await apiPlugin.page(pageParams())
    rows.value = res.data.records
    // 全局 Long -> String 序列化：分页数字必须转回 number，否则计数与比例条会拼字符串。
    pagination.total = Number(res.data.total)
    pagination.page = Number(res.data.page) || 1
    pagination.size = Number(res.data.size) || pagination.size
    if (!options.keepSelection || !selectedCode.value) {
      const target = rows.value[0]
      if (target) {
        // 自动选中只换详情内容，不切窄屏窗格：移动端进页面仍先看到列表。
        applySelection(target)
      }
      else if (!options.keepSelection) {
        // 筛选后无结果：详情清空，避免展示与列表无关的插件。
        selectedCode.value = ''
        selectedSnapshot.value = null
        detailOpen.value = false
      }
    }
    syncSelectedSnapshot()
    nextTick(syncCatalogOverflow)
  }
  finally {
    loading.value = false
  }
}

async function loadSummary() {
  const res = await apiPlugin.summary()
  summary.value = {
    total: Number(res.data.total) || 0,
    statusCounts: {
      ENABLED: Number(res.data.statusCounts?.ENABLED) || 0,
      LOADED: Number(res.data.statusCounts?.LOADED) || 0,
      INSTALLED: Number(res.data.statusCounts?.INSTALLED) || 0,
      DISABLED: Number(res.data.statusCounts?.DISABLED) || 0,
      ERROR: Number(res.data.statusCounts?.ERROR) || 0,
    },
  }
}

async function checkUpdates(options: { silent?: boolean } = {}) {
  checkingUpdates.value = true
  try {
    const res = await apiPluginMarketplace.updatePlans()
    updatePlans.value = res.data
    updatesChecked.value = true
    if (!options.silent) {
      toast.success(updatePlans.value.length
        ? `已生成 ${updatePlans.value.length} 个插件更新计划`
        : '已检查，没有可用更新计划')
    }
  }
  catch (error) {
    updatePlans.value = []
    updatesChecked.value = false
    // 静默检查失败不打断进页面：仍可用按钮手动重试并看到失败原因。
    if (!options.silent) {
      throw error
    }
  }
  finally {
    checkingUpdates.value = false
  }
}

function updatePlanFor(code: string) {
  return updatePlans.value.find(item => item.code === code)
}

const pluginIconFallbacks: Record<string, string> = {
  'ai-chatbot': 'morph:bot',
  'yudream-alipay': 'i-ri:alipay-line',
  'authlib-injector': 'morph:shield',
  'mc-wiki': 'morph:book-open',
  'mcguess': 'morph:messages-square',
  'minecraft-server': 'morph:server',
  'project-progress': 'morph:list-todo',
  'qq-binding': 'morph:link',
  'qqbot-automation': 'i-ri:chat-settings-line',
  'yudream-student-info': 'morph:graduation-cap',
  'yudream-wallet': 'morph:wallet',
  'web-card': 'morph:layout-dashboard',
  'world-map': 'morph:map',
  'yudream-skin': 'morph:shirt',
}

function pluginIcon(item: PluginModule) {
  const icon = item.icon || pluginIconFallbacks[item.code] || 'morph:puzzle'
  if (/^https?:\/\//.test(icon) || icon.includes(':')) {
    return icon
  }
  return `/api/platform/plugins/${item.code}/assets/${icon.replace(/^\.\//, '')}`
}

function canConfirmUpdate(item: PluginModule, plan?: PluginMarketplaceUpdatePlan) {
  return !!plan && !plan.blockedReason && actionLoading.value !== `${item.code}:update`
}

function confirmRollback(item = selected.value) {
  if (!item || !item.rollbackAvailable) {
    return
  }
  const cascade = Boolean(item.loaded || item.enabled)
  modal.confirm({
    title: '确认回滚插件',
    content: cascade
      ? `确认将运行中的插件「${item.name}」回滚到 ${item.rollbackVersion || '本地备份'} 吗？将按依赖顺序级联停机（含硬/软依赖方），回滚后自动恢复原先启用的软依赖方；硬依赖方若与降级版本不兼容会标记异常。回滚不会自动启用目标插件。`
      : `确认将插件「${item.name}」回滚到 ${item.rollbackVersion || '本地备份'} 吗？回滚后需重启服务，且不会自动启用。`,
    onConfirm: async () => {
      actionLoading.value = `${item.code}:rollback`
      try {
        await apiPluginMarketplace.rollback(item.code, cascade)
        await refreshAfterMutation()
        toast.success('插件已回滚，请重启服务后生效；未自动启用插件')
      }
      finally {
        actionLoading.value = ''
      }
    },
  })
}

function confirmUpdate(item = selected.value) {
  const plan = item && updatePlanFor(item.code)
  if (!item || !plan || !canConfirmUpdate(item, plan)) {
    return
  }
  modal.confirm({
    title: '确认更新插件',
    content: `确认将插件「${item.name}」从 ${plan.fromVersion} 更新至 ${plan.toVersion} 吗？系统会受控停用并卸载目标插件及其依赖方；必须重启，且仅恢复更新前已启用的插件。`,
    onConfirm: async () => {
      actionLoading.value = `${item.code}:update`
      try {
        await apiPluginMarketplace.update(item.code, { releaseVersion: plan.toVersion })
        await refreshAfterMutation()
        toast.success('插件已更新，必须重启服务后生效')
      }
      finally {
        actionLoading.value = ''
      }
    },
  })
}

/** 一键更新全部：只提交更新计划中未受阻的插件，逐项更新，单项失败不中断其余。 */
function confirmUpdateAll() {
  const plans = updatablePlans.value
  if (!plans.length || updatingAll.value) {
    return
  }
  const preview = plans.slice(0, 4).map(plan => `${plan.code} ${plan.fromVersion} → ${plan.toVersion}`).join('、')
  modal.confirm({
    title: `一键更新 ${plans.length} 个插件`,
    content: `确认把 ${plans.length} 个插件更新到市场最新版本吗？涉及：${preview}${plans.length > 4 ? ' 等' : ''}。系统会受控停用并卸载目标插件及其依赖方；必须重启，且仅恢复更新前已启用的插件；单项失败不会中断其余插件，更新受阻的插件不会包含在内。`,
    onConfirm: async () => {
      updatingAll.value = true
      try {
        const res = await apiPluginMarketplace.updateBatch(
          plans.map(plan => ({ code: plan.code, releaseVersion: plan.toVersion })),
        )
        const { updatedCodes, failures, requiresRestart } = res.data
        await refreshAfterMutation()
        if (failures.length) {
          toast.error(`已更新 ${updatedCodes.length} 个插件，${failures.length} 个未完成`, {
            description: failures.map(failure => `${failure.code}：${failure.reason}`).join('；'),
          })
        }
        else {
          toast.success(requiresRestart
            ? `已更新 ${updatedCodes.length} 个插件，必须重启服务后生效`
            : '插件已是市场最新版本')
        }
      }
      finally {
        updatingAll.value = false
      }
    },
  })
}

/** 目录类操作后的统一刷新：当前页、状态概览与更新计划一起对齐。 */
async function refreshAfterMutation() {
  await Promise.all([loadPage({ keepSelection: true }), loadSummary(), checkUpdates({ silent: true })])
  await refreshDynamicRoutes(router)
}

async function refresh() {
  loading.value = true
  try {
    await apiPlugin.refresh()
  }
  finally {
    loading.value = false
  }
  pagination.page = 1
  await refreshAfterMutation()
  toast.success('插件目录已扫描')
}

async function uploadJar(files: File[]) {
  const file = files[0]
  uploadInput.value?.reset()
  if (!file) {
    return
  }
  if (!file.name.toLowerCase().endsWith('.jar')) {
    toast.error('请选择 .jar 插件文件')
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    const uploaded = await apiPlugin.upload(data)
    pagination.page = 1
    await refreshAfterMutation()
    // 上传会替换同编码插件：直接把详情切到刚上传的那个，省去在目录里再找。
    const baseName = file.name.replace(/\.jar$/i, '')
    const target = uploaded.data.find(item => item.code === baseName)
      ?? uploaded.data.find(item => item.jarPath?.replace(/\\/g, '/').endsWith(`/${file.name}`))
    if (target) {
      selectPlugin(target)
    }
    toast.success('插件 JAR 已上传并扫描')
  }
  finally {
    uploading.value = false
  }
}

async function runAction(code: string, action: 'load' | 'enable' | 'disable' | 'unload') {
  if (action === 'enable') {
    await previewEnable(code)
    return
  }
  if (action === 'unload') {
    await previewUnload(code)
    return
  }
  actionLoading.value = `${code}:${action}`
  try {
    const res = await apiPlugin[action](code)
    replaceItem(res.data)
    if (['enable', 'disable', 'unload'].includes(action)) {
      await refreshDynamicRoutes(router)
      await refreshPluginThemes()
    }
    await loadSummary()
    toast.success(actionText(action))
  }
  finally {
    actionLoading.value = ''
  }
}

/** 启用前依赖预览：有依赖时弹窗（硬缺失引导安装、软依赖可选启用），无依赖直接启用。 */
async function previewEnable(code: string) {
  const item = rows.value.find(row => row.code === code) ?? (selected.value?.code === code ? selected.value : undefined)
  if (!item || item.enabled) {
    return
  }
  const hasDependencies = Boolean(item.dependencies?.length || item.softDependencies?.length)
  if (!hasDependencies) {
    await enablePlugin(code, [])
    return
  }
  actionLoading.value = `${code}:enable`
  try {
    const res = await apiPlugin.dependencies(code)
    enableDependencyStatus.value = res.data
    enableDialogOpen.value = true
  }
  catch {
    toast.error('获取插件依赖状态失败')
  }
  finally {
    actionLoading.value = ''
  }
}

async function confirmEnable(payload: { includeSoft: string[], installItems: PluginMarketplaceBatchInstallItem[] }) {
  const code = enableDependencyStatus.value?.code
  if (!code) {
    return
  }
  enableLoading.value = true
  try {
    if (payload.installItems.length) {
      try {
        await apiPluginMarketplace.installBatch(payload.installItems)
      }
      catch {
        toast.error('依赖下载失败，已整体回滚；插件未启用')
        return
      }
    }
    await enablePlugin(code, payload.includeSoft)
    enableDialogOpen.value = false
  }
  finally {
    enableLoading.value = false
  }
}

async function enablePlugin(code: string, includeSoft: string[]) {
  actionLoading.value = `${code}:enable`
  try {
    const res = await apiPlugin.enable(code, includeSoft)
    replaceItem(res.data)
    await refreshDynamicRoutes(router)
    await refreshPluginThemes()
    await loadSummary()
    toast.success(includeSoft.length
      ? `插件已启用，并一并启用了 ${includeSoft.length} 个软依赖`
      : '插件已启用，动态菜单已同步')
  }
  catch {
    await loadPage({ keepSelection: true })
  }
  finally {
    actionLoading.value = ''
  }
}

/** 卸载前依赖方预览：有运行中依赖方时弹窗提供级联，否则直接卸载。 */
async function previewUnload(code: string) {
  const item = rows.value.find(row => row.code === code) ?? (selected.value?.code === code ? selected.value : undefined)
  if (!item || !item.loaded) {
    return
  }
  actionLoading.value = `${code}:unload`
  try {
    const res = await apiPlugin.dependents(code)
    const running = res.data.dependents.filter(dependent => dependent.loaded || dependent.enabled)
    if (!running.length) {
      await unloadPlugin(code, false)
      return
    }
    dependentsInfo.value = res.data
    dependentsAction.value = 'unload'
    dependentsDialogOpen.value = true
  }
  catch {
    toast.error('获取插件依赖方失败')
  }
  finally {
    actionLoading.value = ''
  }
}

async function unloadPlugin(code: string, cascade: boolean) {
  actionLoading.value = `${code}:unload`
  try {
    const res = await apiPlugin.unload(code, cascade)
    replaceItem(res.data)
    await refreshDynamicRoutes(router)
    await refreshPluginThemes()
    await loadSummary()
    toast.success(cascade ? '插件已级联卸载；软依赖方已自动降级恢复，硬依赖方保持禁用' : '插件已卸载，动态菜单已同步')
  }
  catch {
    await loadPage({ keepSelection: true })
  }
  finally {
    actionLoading.value = ''
  }
}

function confirmRemove(item = selected.value) {
  if (!item) {
    return
  }
  modal.confirm({
    title: '确认删除插件记录',
    content: `确认删除插件「${item.name}」的管理记录吗？该操作不会删除磁盘 JAR；如果 JAR 仍在插件目录中，扫描后会重新出现。`,
    onConfirm: async () => {
      // 有运行中依赖方时改走级联确认弹窗
      actionLoading.value = `${item.code}:remove`
      try {
        const res = await apiPlugin.dependents(item.code)
        const running = res.data.dependents.filter(dependent => dependent.loaded || dependent.enabled)
        if (running.length) {
          dependentsInfo.value = res.data
          dependentsAction.value = 'delete'
          dependentsDialogOpen.value = true
          return
        }
        await removePluginRecord(item, false)
      }
      catch {
        toast.error('获取插件依赖方失败')
      }
      finally {
        actionLoading.value = ''
      }
    },
  })
}

async function removePluginRecord(item: PluginModule, cascade: boolean) {
  actionLoading.value = `${item.code}:remove`
  try {
    await apiPlugin.remove(item.code, cascade)
    if (selectedCode.value === item.code) {
      selectedCode.value = ''
      selectedSnapshot.value = null
      detailOpen.value = false
    }
    await refreshAfterMutation()
    toast.success(cascade ? '插件记录已级联删除；软依赖方已自动降级恢复' : '插件记录已删除')
  }
  finally {
    actionLoading.value = ''
  }
}

function openGitUrl(url?: string) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

/** 一键打开插件注册的后台页面（取第一个可见、非公开访问且带组件的路由）。 */
async function openPluginPage(code: string) {
  actionLoading.value = `${code}:open`
  try {
    const res = await apiPlugin.frontendManifest()
    const mod = res.data.modules.find(item => item.pluginCode === code)
    const routes = (mod?.routes || []).filter(route =>
      !route.hideInMenu
      && !route.publicAccess
      && route.status !== 'DISABLED'
      && Boolean(route.path))
    const target = routes.find(route => route.component) || routes[0]
    if (!target) {
      toast.error('该插件未启用或没有注册后台页面')
      return
    }
    await router.push(target.path)
  }
  catch {
    toast.error('获取插件页面失败')
  }
  finally {
    actionLoading.value = ''
  }
}

/** 只切换详情内容（自动选中用）：窄屏窗格由用户点击决定，不在这里改。 */
function applySelection(item: PluginModule) {
  selectedCode.value = item.code
  selectedSnapshot.value = item
}

function selectPlugin(item: PluginModule) {
  applySelection(item)
  detailOpen.value = true
}

/** 依赖项点击：直接把详情切到被依赖的插件，省去回目录里再找一次。 */
function focusPlugin(code: string) {
  const target = rows.value.find(row => row.code === code)
  if (!target) {
    return
  }
  selectPlugin(target)
  nextTick(() => {
    if (catalogRef.value) {
      catalogRef.value.scrollTop = 0
    }
  })
}

/** 卸载/删除依赖方弹窗确认：级联执行并自动恢复软依赖方。 */
async function handleDependentsConfirm(cascade: boolean) {
  const code = dependentsInfo.value?.code
  if (!code) {
    return
  }
  dependentsDialogOpen.value = false
  if (dependentsAction.value === 'delete') {
    const item = rows.value.find(row => row.code === code) ?? (selected.value?.code === code ? selected.value : null)
    if (item) {
      await removePluginRecord(item, cascade)
    }
    return
  }
  await unloadPlugin(code, cascade)
}

function replaceItem(item: PluginModule) {
  const index = rows.value.findIndex(row => row.code === item.code)
  if (index >= 0) {
    rows.value[index] = item
  }
  if (selectedCode.value === item.code) {
    selectedSnapshot.value = item
  }
}

function syncSelectedSnapshot() {
  const current = rows.value.find(item => item.code === selectedCode.value)
  if (current) {
    selectedSnapshot.value = current
  }
}

function resetFilters() {
  keyword.value = ''
  filterStatus.value = 'ALL'
  updateFilter.value = 'ALL'
}

/** 目录未滚到底时显示底部渐隐，提示还有插件没露出。 */
function syncCatalogOverflow() {
  const element = catalogRef.value
  hasMoreBelow.value = Boolean(element) && element!.scrollHeight - element!.scrollTop - element!.clientHeight > 8
}

function statusText(status: PluginStatus) {
  const map: Record<PluginStatus, string> = {
    INSTALLED: '已安装',
    LOADED: '已加载',
    ENABLED: '运行中',
    DISABLED: '已禁用',
    ERROR: '异常',
  }
  return map[status]
}

/** 状态说明：把「这个状态意味着什么、下一步做什么」写清楚，而不是只给一个词。 */
function statusHint(item?: PluginModule) {
  const map: Record<PluginStatus, string> = {
    ENABLED: '已启用：接口、权限与前端页面已注册，菜单已同步。',
    LOADED: '已加载但未启用：类已进入运行时，启用后才会注册接口与页面。',
    INSTALLED: '已发现并登记，尚未加载到运行时。',
    DISABLED: '已禁用：运行已停止，动态菜单与接口已回收。',
    ERROR: '加载或启用失败：查看下方错误信息，修正后重新加载。',
  }
  return item ? map[item.status] : ''
}

function statusIcon(status: PluginStatus) {
  const map: Record<PluginStatus, string> = {
    ENABLED: 'morph:circle-check',
    LOADED: 'morph:cloud-download',
    INSTALLED: 'morph:package',
    DISABLED: 'morph:circle-pause',
    ERROR: 'morph:triangle-alert',
  }
  return map[status]
}

function statusVariant(status: PluginStatus) {
  return status === 'ENABLED' ? 'default' : status === 'ERROR' ? 'destructive' : 'secondary'
}

function dependencyText(row: PluginDependencyRow) {
  if (!row.available) {
    return '未安装'
  }
  if (row.enabled) {
    return '已启用'
  }
  return row.loaded ? '已加载未启用' : '已安装未加载'
}

function dependencyVariant(row: PluginDependencyRow) {
  if (!row.available) {
    return 'destructive'
  }
  return row.enabled ? 'default' : 'secondary'
}

function dependencySummary() {
  if (!dependencyRows.value.length) {
    return ''
  }
  if (missingRequired.value.length) {
    return `有 ${missingRequired.value.length} 项依赖未启用，启用时会先引导安装或启用。`
  }
  return '依赖已满足，可以直接启用。'
}

/** 加载/启用时间截断到分钟：秒与 7 位小数只会撑破信息格。 */
function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : value
}

function actionText(action: string) {
  const map: Record<string, string> = {
    load: '插件已加载',
    enable: '插件已启用，动态菜单已同步',
    disable: '插件已禁用，动态菜单已同步',
    unload: '插件已卸载，动态菜单已同步',
  }
  return map[action] || '操作完成'
}
</script>

<template>
  <div>
    <YdPageHeader title="插件管理" class="mb-0">
      <template #description>
        管理插件 JAR 的发现、加载、启用、禁用、卸载和记录删除。插件启用后会动态注册接口、权限和前端页面。
      </template>
    </YdPageHeader>

    <YdPageMain>
      <YdListToolbar
        v-model:keyword="keyword"
        v-model:status="filterStatus"
        class="plugin-toolbar"
        title="插件目录"
        placeholder="搜索名称、编码、版本或描述"
        :status-items="statusChips"
      >
        <template #views>
          <span class="plugin-toolbar__summary">{{ hasActiveFilters ? '筛选出' : '共' }} {{ pagination.total }} 个插件</span>
          <YdTag v-if="errorCount" variant="destructive">
            <YdIcon name="morph:triangle-alert" />{{ errorCount }} 个异常
          </YdTag>
          <YdTag v-else-if="availableUpdateCount" variant="secondary">
            <YdIcon name="morph:circle-arrow-up" />{{ availableUpdateCount }} 个可更新
          </YdTag>
        </template>
        <template #filters>
          <YdFilterSelect v-model="updateFilter" :options="updateOptions" :width="180" :disabled="!updatesChecked && !updatePlans.length" placeholder="全部更新状态" />
          <YdFilterSelect v-model="sortKey" class="plugin-sort-filter" :options="sortOptions" :width="160" placeholder="默认顺序" />
        </template>
        <template #actions>
          <YdButton v-if="hasActiveFilters" variant="ghost" @click="resetFilters">
            重置
          </YdButton>
          <YdButton v-auth="'platform:plugin:manage'" variant="outline" :loading="loading" :disabled="uploading || checkingUpdates" @click="refresh">
            <YdIcon name="morph:folder-open" />
            扫描目录
          </YdButton>
          <YdButton variant="outline" :loading="checkingUpdates" :disabled="loading" @click="checkUpdates()">
            <YdIcon name="morph:circle-arrow-up" />
            检查更新
          </YdButton>
          <YdButton v-auth="'platform:plugin:manage'" variant="outline" :loading="uploading" :disabled="loading" @click="uploadInput?.open()">
            <YdIcon name="morph:cloud-upload" />
            上传 JAR
          </YdButton>
          <YdButton
            v-if="availableUpdateCount"
            v-auth="'platform:plugin:manage'"
            :loading="updatingAll"
            :disabled="Boolean(actionLoading)"
            @click="confirmUpdateAll"
          >
            <YdIcon name="morph:layers" />
            一键更新 {{ availableUpdateCount }} 个
          </YdButton>
          <YdFilePicker ref="uploadInput" class="hidden" accept=".jar,application/java-archive" label="选择插件 JAR" @select="uploadJar">
            <template #default />
          </YdFilePicker>
        </template>
      </YdListToolbar>

      <div ref="workbenchRef" class="plugin-workbench" :class="{ 'is-detail': detailOpen, 'is-single': !selected }">
        <section class="plugin-browser" :class="{ 'has-more': hasMoreBelow }">
          <YdLoadingOverlay :loading="loading">
            <div v-if="rows.length" ref="catalogRef" class="plugin-catalog" @scroll="syncCatalogOverflow">
              <YdAction
                v-for="(item, index) in rows"
                :key="item.code"
                class="plugin-row"
                :class="{ 'is-selected': selected?.code === item.code }"
                :style="{ '--row-index': Math.min(index, 12) }"
                :data-status="item.status"
                :aria-pressed="selected?.code === item.code"
                @click="selectPlugin(item)"
              >
                <span class="plugin-row__icon"><YdIcon :name="pluginIcon(item)" /></span>
                <span class="plugin-row__name">
                  <strong>{{ item.name }}</strong>
                  <small v-if="item.version">v{{ item.version }}</small>
                </span>
                <span class="plugin-row__code">{{ item.code }}</span>
                <span class="plugin-row__description" :class="{ 'is-error': item.status === 'ERROR' && item.errorMessage }">
                  {{ item.status === 'ERROR' && item.errorMessage ? item.errorMessage : (item.description || '暂无描述') }}
                </span>
                <span class="plugin-row__side">
                  <YdTag v-if="updatePlanFor(item.code)?.blockedReason" class="plugin-row__update" variant="destructive">
                    更新受阻
                  </YdTag>
                  <YdTag v-else-if="updatePlanFor(item.code)" class="plugin-row__update" variant="outline">
                    可更新至 {{ updatePlanFor(item.code)?.toVersion }}
                  </YdTag>
                  <span class="plugin-row__state">
                    {{ statusText(item.status) }}
                    <YdIcon name="morph:chevron-right" class="plugin-row__chevron" />
                  </span>
                </span>
              </YdAction>
            </div>
            <YdEmpty v-else-if="hasActiveFilters" title="没有匹配的插件" description="调整搜索词或筛选条件后重试。">
              <YdButton variant="outline" @click="resetFilters">
                清除筛选
              </YdButton>
            </YdEmpty>
            <YdEmpty v-else title="暂无插件" description="把插件 JAR 放入服务端 plugins 目录后扫描目录，或直接上传 JAR。">
              <div class="plugin-empty-actions">
                <YdButton v-auth="'platform:plugin:manage'" variant="outline" :loading="loading" @click="refresh">
                  <YdIcon name="morph:folder-open" />
                  扫描目录
                </YdButton>
                <YdButton v-auth="'platform:plugin:manage'" :loading="uploading" @click="uploadInput?.open()">
                  <YdIcon name="morph:cloud-upload" />
                  上传 JAR
                </YdButton>
              </div>
            </YdEmpty>
          </YdLoadingOverlay>
          <div v-if="pagination.total > 0" class="plugin-pagination">
            <!-- 目录栏比整页窄：去掉「前往页码」，翻页控件才在一条线上排开 -->
            <YdPagination
              v-model:page="pagination.page"
              v-model:size="pagination.size"
              layout="total, sizes, ->, pager"
              :total="pagination.total"
            />
          </div>
        </section>

        <section v-if="selected" class="plugin-detail">
          <header class="detail-header">
            <div class="detail-title">
              <div class="detail-icon">
                <YdIcon :name="pluginIcon(selected)" />
              </div>
              <div class="detail-title__text">
                <h2>
                  {{ selected.name }}
                  <YdTag :variant="statusVariant(selected.status)">
                    {{ statusText(selected.status) }}
                  </YdTag>
                </h2>
                <p>{{ selected.description || '暂无描述' }}</p>
              </div>
            </div>
            <div class="detail-actions">
              <YdButton
                variant="ghost"
                class="detail-back"
                @click="detailOpen = false"
              >
                <YdIcon name="morph:arrow-left" />
                返回列表
              </YdButton>
              <YdButton
                v-if="!selected.enabled"
                v-auth="'platform:plugin:manage'"
                :loading="actionLoading === `${selected.code}:enable`"
                @click="runAction(selected.code, 'enable')"
              >
                <YdIcon name="morph:circle-play" />
                启用
              </YdButton>
              <YdButton
                v-if="selected.enabled"
                v-auth="'platform:plugin:manage'"
                variant="outline"
                :loading="actionLoading === `${selected.code}:disable`"
                @click="runAction(selected.code, 'disable')"
              >
                <YdIcon name="morph:circle-pause" />
                禁用
              </YdButton>
              <YdButton
                v-if="!selected.loaded"
                v-auth="'platform:plugin:manage'"
                variant="outline"
                :loading="actionLoading === `${selected.code}:load`"
                @click="runAction(selected.code, 'load')"
              >
                <YdIcon name="morph:cloud-download" />
                加载
              </YdButton>
              <YdButton
                v-if="!selected.enabled && selected.loaded"
                v-auth="'platform:plugin:manage'"
                variant="outline"
                :loading="actionLoading === `${selected.code}:unload`"
                @click="runAction(selected.code, 'unload')"
              >
                <YdIcon name="i-ri:eject-line" />
                卸载
              </YdButton>
              <YdButton
                variant="ghost"
                :loading="actionLoading === `${selected.code}:open`"
                @click="openPluginPage(selected.code)"
              >
                <YdIcon name="morph:external-link" />
                打开插件页面
              </YdButton>
            </div>
          </header>

          <p class="detail-note" :data-status="selected.status">
            <span class="detail-note__icon"><YdIcon :name="statusIcon(selected.status)" /></span>
            {{ statusHint(selected) }}
          </p>

          <YdAlert
            v-if="selected.errorMessage"
            variant="destructive"
            icon="morph:triangle-alert"
            title="插件运行异常"
          >
            <template #description>
              {{ selected.errorMessage }}
            </template>
          </YdAlert>

          <div class="detail-grid">
            <div class="info-cell">
              <span>运行状态</span>
              <YdTag :variant="statusVariant(selected.status)">
                {{ statusText(selected.status) }}
              </YdTag>
            </div>
            <div class="info-cell">
              <span>版本</span>
              <strong>{{ selected.version || '未知版本' }}</strong>
            </div>
            <div class="info-cell">
              <span>加载时间</span>
              <strong>{{ formatTime(selected.loadedAt) }}</strong>
            </div>
            <div class="info-cell">
              <span>启用时间</span>
              <strong>{{ formatTime(selected.enabledAt) }}</strong>
            </div>
          </div>

          <div class="detail-plain">
            <span>插件编码</span>
            <code>{{ selected.code }}</code>
            <YdCopyButton :text="selected.code" label="" />
          </div>

          <div v-if="selected.gitUrl" class="detail-plain">
            <span>源码仓库</span>
            <YdButton variant="link" class="detail-git-link" @click="openGitUrl(selected.gitUrl)">
              <YdIcon name="i-ri:github-line" />
              {{ selected.gitUrl }}
            </YdButton>
            <YdCopyButton :text="selected.gitUrl" label="" />
          </div>

          <div v-if="updatePlanFor(selected.code)" class="update-panel" :data-blocked="Boolean(updatePlanFor(selected.code)?.blockedReason)">
            <div class="section-title">
              <span>更新计划</span>
              <YdTag :variant="updatePlanFor(selected.code)?.blockedReason ? 'destructive' : 'default'">
                {{ updatePlanFor(selected.code)?.blockedReason ? '更新受阻' : '可更新' }}
              </YdTag>
            </div>
            <div class="update-version">
              <strong>{{ updatePlanFor(selected.code)?.fromVersion || '未知' }}</strong>
              <YdIcon name="morph:arrow-right" />
              <strong>{{ updatePlanFor(selected.code)?.toVersion || '未知' }}</strong>
            </div>
            <ul class="update-facts">
              <li>
                <span>变更类型</span><strong>{{ updatePlanFor(selected.code)?.changeType || '—' }}</strong>
              </li>
              <li>
                <span>需要重启</span><strong>{{ updatePlanFor(selected.code)?.requiresRestart ? '是' : '否' }}</strong>
              </li>
            </ul>

            <div v-if="updatePlanFor(selected.code)?.requiredDependencies?.length" class="plan-section">
              <strong>必需依赖</strong>
              <div class="dependency-list">
                <YdTag v-for="dependency in updatePlanFor(selected.code)?.requiredDependencies" :key="`required-${dependency.code}`" :variant="dependency.warning ? 'destructive' : 'secondary'">
                  {{ dependency.code }}{{ dependency.range ? ` (${dependency.range})` : '' }}{{ dependency.warning ? `：${dependency.warningReason || '存在告警'}` : '' }}
                </YdTag>
              </div>
            </div>

            <div v-if="updatePlanFor(selected.code)?.optionalDependencies?.length" class="plan-section">
              <strong>可选依赖</strong>
              <div class="dependency-list">
                <YdTag v-for="dependency in updatePlanFor(selected.code)?.optionalDependencies" :key="`optional-${dependency.code}`" variant="secondary">
                  {{ dependency.code }}{{ dependency.range ? ` (${dependency.range})` : '' }}{{ dependency.warning ? `：${dependency.warningReason || '存在告警'}` : '' }}
                </YdTag>
              </div>
            </div>

            <div v-if="updatePlanFor(selected.code)?.affectedEnabledPlugins?.length" class="plan-section">
              <strong>受影响的已启用插件</strong>
              <div class="dependency-list">
                <YdTag v-for="code in updatePlanFor(selected.code)?.affectedEnabledPlugins" :key="code" variant="secondary">
                  {{ code }}
                </YdTag>
              </div>
            </div>

            <div v-if="updatePlanFor(selected.code)?.warnings?.length" class="plan-section">
              <strong>警告</strong>
              <ul class="warning-list">
                <li v-for="warning in updatePlanFor(selected.code)?.warnings" :key="warning">
                  {{ warning }}
                </li>
              </ul>
            </div>

            <YdAlert
              v-if="updatePlanFor(selected.code)?.blockedReason"
              variant="destructive"
              icon="morph:ban"
              title="更新被阻断"
            >
              <template #description>
                {{ updatePlanFor(selected.code)?.blockedReason }}
              </template>
            </YdAlert>
            <template v-else>
              <div class="detail-actions detail-actions--inline">
                <YdButton
                  v-auth="'platform:plugin:manage'"
                  :loading="actionLoading === `${selected.code}:update`"
                  :disabled="Boolean(actionLoading) || updatingAll"
                  @click="confirmUpdate(selected)"
                >
                  <YdIcon name="morph:circle-arrow-up" />
                  确认更新至 {{ updatePlanFor(selected.code)?.toVersion }}
                </YdButton>
                <YdButton
                  v-if="availableUpdateCount > 1"
                  v-auth="'platform:plugin:manage'"
                  variant="outline"
                  :disabled="Boolean(actionLoading) || updatingAll"
                  @click="confirmUpdateAll"
                >
                  <YdIcon name="morph:layers" />
                  一并更新其余 {{ availableUpdateCount - 1 }} 个
                </YdButton>
              </div>
              <p class="plan-warning">
                确认更新会受控停用并卸载目标插件及其依赖方；重启后仅恢复更新前已启用的插件。
              </p>
            </template>
          </div>

          <div v-if="selected.rollbackAvailable" class="detail-section">
            <div class="section-title">
              <span>可用备份</span>
              <YdTag variant="secondary">{{ selected.rollbackVersion || '本地备份' }}</YdTag>
            </div>
            <div class="detail-actions detail-actions--inline">
              <YdButton
                v-auth="'platform:plugin:manage'"
                variant="outline"
                :disabled="Boolean(actionLoading)"
                :loading="actionLoading === `${selected.code}:rollback`"
                @click="confirmRollback(selected)"
              >
                <YdIcon name="morph:history" />
                回滚到 {{ selected.rollbackVersion || '备份版本' }}
              </YdButton>
            </div>
            <p class="section-hint">
              回滚会按依赖顺序级联停机，回滚后不会自动启用；需要重启服务生效。
            </p>
          </div>

          <div v-if="dependencyRows.length" class="detail-section">
            <div class="section-title">
              <span>插件依赖</span>
              <YdTag :variant="missingRequired.length ? 'destructive' : 'default'">
                {{ dependencyReady }} / {{ dependencyRows.length }} 已启用
              </YdTag>
            </div>
            <div class="dependency-rows">
              <YdAction
                v-for="dependency in dependencyRows"
                :key="dependency.code"
                class="dependency-row"
                :class="{ 'is-missing': !dependency.available }"
                @click="dependency.available ? focusPlugin(dependency.code) : undefined"
              >
                <span class="dependency-row__icon"><YdIcon :name="dependency.icon" /></span>
                <span class="dependency-row__body">
                  <strong>{{ dependency.name }}</strong>
                  <code>{{ dependency.code }}{{ dependency.version ? ` · v${dependency.version}` : '' }}</code>
                </span>
                <YdTag variant="outline">{{ dependency.required ? '硬依赖' : '软依赖' }}</YdTag>
                <YdTag :variant="dependencyVariant(dependency)">
                  {{ dependencyText(dependency) }}
                </YdTag>
                <YdIcon v-if="dependency.available" name="morph:arrow-right" class="dependency-row__jump" />
              </YdAction>
            </div>
            <p class="section-hint" :class="{ 'is-warning': missingRequired.length }">
              {{ dependencySummary() }}
            </p>
          </div>

          <div class="detail-section detail-section--danger">
            <div class="section-title">
              <span>危险操作</span>
            </div>
            <div class="detail-actions detail-actions--inline">
              <YdButton
                v-auth="'platform:plugin:manage'"
                variant="destructive"
                :loading="actionLoading === `${selected.code}:remove`"
                @click="confirmRemove(selected)"
              >
                <YdIcon name="morph:trash-2" />
                删除记录
              </YdButton>
            </div>
            <p class="section-hint">
              删除只移除管理记录，不删除磁盘 JAR；磁盘上的 JAR 会在下次扫描后重新出现。
            </p>
          </div>
        </section>
      </div>

      <EnableDependencyDialog
        v-model="enableDialogOpen"
        :status="enableDependencyStatus"
        :loading="enableLoading"
        @confirm="confirmEnable"
      />
      <DependentsConfirmDialog
        v-model="dependentsDialogOpen"
        :dependents="dependentsInfo"
        :action="dependentsAction"
        :loading="Boolean(actionLoading)"
        @confirm="handleDependentsConfirm"
      />
    </YdPageMain>
  </div>
</template>

<style scoped>
.plugin-toolbar { margin-bottom: 16px; }
.plugin-toolbar__summary { color: var(--color-text-3); font-size: 13px; }
.plugin-empty-actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }

/* 目录与详情各自限高、独立滚动：列表滚动不再带动详情，页面不超出可视区。 */
.plugin-workbench { display: grid; grid-template-columns: minmax(340px, 1fr) minmax(0, 1.3fr); gap: 16px; align-items: stretch; }
.plugin-workbench > * { max-height: var(--plugin-pane-max-height, calc(100dvh - 272px)); min-height: 320px; }
.plugin-workbench.is-single { grid-template-columns: minmax(0, 1fr); }

.plugin-browser { position: relative; display: grid; grid-template-rows: minmax(0, 1fr) auto; gap: 12px; min-width: 0; min-height: 0; }
/* 底部渐隐：提示目录还有更多插件，滚到底自动消失 */
.plugin-browser::after { position: absolute; right: 0; bottom: 56px; left: 0; height: 36px; background: linear-gradient(to top, var(--color-bg-2), transparent); content: ''; opacity: 0; pointer-events: none; transition: opacity .2s ease; }
.plugin-browser.has-more::after { opacity: 1; }
.plugin-browser :deep(.yd-loading-overlay) { min-height: 0; height: 100%; }
.plugin-browser :deep(.yd-loading-overlay__content) { display: grid; grid-template-rows: minmax(0, 1fr); min-height: 0; height: 100%; }
.plugin-catalog { display: grid; align-content: start; gap: 6px; max-height: 100%; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 2px 8px 2px 2px; }
.plugin-pagination { display: flex; justify-content: center; padding-top: 4px; border-top: 1px solid var(--color-border-2); }

/* 插件行：左侧色条代表状态，名称行末尾声明状态，第三行放描述/错误信息 */
.plugin-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; grid-template-areas: 'icon name side' 'icon code side' 'icon desc side'; column-gap: 10px; row-gap: 2px; align-items: center; width: 100%; min-width: 0; padding: 10px 12px; border: 1px solid var(--color-border-2); border-left-width: 3px; border-radius: 10px; background: var(--color-bg-1); color: var(--color-text-1); text-align: left; transition: border-color .18s ease, background-color .18s ease, transform .18s cubic-bezier(.32, .72, 0, 1), box-shadow .18s ease; animation: plugin-row-in .32s cubic-bezier(.32, .72, 0, 1) both; animation-delay: calc(var(--row-index, 0) * 22ms); }
.plugin-row:hover { transform: translateY(-2px); background: var(--color-fill-1); box-shadow: 0 6px 18px -12px oklch(0 0 0 / 45%); }
.plugin-row:hover .plugin-row__icon { transform: scale(1.06) rotate(-2deg); }
.plugin-row.is-selected { background: var(--color-fill-2); border-color: var(--color-text-3); }
.plugin-row__icon { display: grid; grid-area: icon; width: 34px; height: 34px; place-items: center; border-radius: 9px; background: var(--color-fill-2); color: var(--color-text-2); font-size: 18px; transition: transform .18s cubic-bezier(.32, .72, 0, 1); }
.plugin-row__name { display: flex; grid-area: name; align-items: baseline; gap: 6px; min-width: 0; }
.plugin-row__name strong { overflow: hidden; color: var(--color-text-1); font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.plugin-row__name small { flex: none; color: var(--color-text-3); font-size: 11px; }
.plugin-row__code { grid-area: code; overflow: hidden; color: var(--color-text-3); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.plugin-row__description { grid-area: desc; overflow: hidden; color: var(--color-text-3); font-size: 12px; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.plugin-row__description.is-error { color: var(--color-danger-6); }
.plugin-row__side { display: flex; grid-area: side; flex-direction: column; align-items: flex-end; align-self: center; gap: 4px; min-width: 0; white-space: nowrap; }
.plugin-row__update { max-width: 132px; overflow: hidden; }
.plugin-row__state { display: inline-flex; align-items: center; gap: 2px; color: var(--color-text-3); font-size: 11.5px; }
.plugin-row__chevron { color: var(--color-text-3); transition: transform .18s cubic-bezier(.32, .72, 0, 1); }
.plugin-row:hover .plugin-row__chevron { transform: translateX(3px); color: var(--color-text-1); }

/* 状态即配色：运行中用强调色，未启用走中性弱化，异常用危险色并轻微染色 */
.plugin-row[data-status='ENABLED'] { border-left-color: var(--yd-emphasis, var(--color-text-1)); }
.plugin-row[data-status='ENABLED'] .plugin-row__state { color: var(--yd-emphasis, var(--color-text-1)); font-weight: 600; }
.plugin-row[data-status='LOADED'] { border-left-color: var(--color-border-3); }
.plugin-row[data-status='INSTALLED'] { border-left-color: var(--color-border-2); }
.plugin-row[data-status='INSTALLED'] .plugin-row__icon { opacity: .78; }
.plugin-row[data-status='DISABLED'] { border-left-color: var(--color-border-2); }
.plugin-row[data-status='DISABLED'] .plugin-row__icon { opacity: .62; }
.plugin-row[data-status='ERROR'] { border-left-color: var(--color-danger-6); background: color-mix(in oklab, var(--color-danger-6) 6%, var(--color-bg-1)); }
.plugin-row[data-status='ERROR'] .plugin-row__state { color: var(--color-danger-6); font-weight: 600; }
@keyframes plugin-row-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }

.plugin-detail { display: grid; gap: 16px; align-content: start; container-type: inline-size; min-width: 0; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 20px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-2); scrollbar-gutter: stable; }
/* 标题与动作各占一行：动作按钮多且互斥，挤在标题右侧会折成两三行 */
.detail-header { display: grid; gap: 14px; }
.detail-title { display: flex; min-width: 0; align-items: center; gap: 12px; }
.detail-title__text { min-width: 0; }
.detail-title h2 { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0 0 4px; color: var(--color-text-1); font-size: 18px; font-weight: 650; }
.detail-title p { margin: 0; color: var(--color-text-3); font-size: 13px; line-height: 1.6; }
.detail-icon { display: grid; width: 46px; height: 46px; flex: none; place-items: center; border-radius: 12px; background: var(--color-fill-2); color: var(--color-text-2); font-size: 24px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.detail-actions--inline { justify-content: flex-start; }
.detail-back { display: none; }

/* 状态说明条：状态词之外补一句「这意味着什么、下一步做什么」 */
.detail-note { display: flex; gap: 8px; align-items: center; margin: 0; padding: 10px 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-fill-1); color: var(--color-text-2); font-size: 13px; line-height: 1.6; }
.detail-note[data-status='ENABLED'] { color: var(--color-text-1); }
.detail-note__icon { display: inline-flex; flex: none; color: var(--color-text-3); font-size: 16px; }
.detail-note[data-status='ENABLED'] .detail-note__icon { color: var(--yd-emphasis, var(--color-text-1)); }
.detail-note[data-status='ERROR'] { border-color: color-mix(in oklab, var(--color-danger-6) 40%, var(--color-border-2)); color: var(--color-text-1); }
.detail-note[data-status='ERROR'] .detail-note__icon { color: var(--color-danger-6); }

.detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr)); gap: 10px; }
@container (min-width: 520px) {
  .detail-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

/* 信息格：值是标签时必须靠上靠左，grid 默认拉伸会把标签撑成大色块 */
.info-cell { display: grid; gap: 8px; align-content: start; justify-items: start; padding: 12px 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.info-cell > span { color: var(--color-text-3); font-size: 12px; }
.info-cell > strong { color: var(--color-text-1); font-size: 15px; font-weight: 600; overflow-wrap: anywhere; }

/* 单值信息行：编码/源码仓库这类长值独占一行，避免撑破格子 */
.detail-plain { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 10px 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.detail-plain > span { color: var(--color-text-3); font-size: 12px; }
.detail-plain code { overflow: hidden; color: var(--color-text-1); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.detail-git-link { justify-self: start; max-width: 100%; padding: 0; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }

.update-panel { display: grid; gap: 12px; padding: 16px; border: 1px solid var(--color-border-2); border-radius: 10px; background: linear-gradient(180deg, var(--color-fill-1), var(--color-bg-1)); }
.update-panel[data-blocked='true'] { border-color: color-mix(in oklab, var(--color-danger-6) 40%, var(--color-border-2)); }
.update-version { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; color: var(--color-text-3); font-size: 13px; }
.update-version strong { color: var(--color-text-1); font-size: 15px; font-weight: 650; }
.update-facts { display: flex; flex-wrap: wrap; gap: 24px; margin: 0; padding: 0; list-style: none; }
.update-facts li { display: grid; gap: 2px; }
.update-facts span { color: var(--color-text-3); font-size: 12px; }
.update-facts strong { color: var(--color-text-1); font-size: 14px; font-weight: 600; }
.plan-section { display: grid; gap: 8px; }
.plan-section > strong { color: var(--color-text-2); font-size: 13px; font-weight: 600; }
.warning-list { display: grid; gap: 4px; margin: 0; padding-left: 20px; color: var(--color-text-2); font-size: 13px; line-height: 1.6; }
.plan-warning { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }

.detail-section { display: grid; gap: 12px; padding-top: 18px; border-top: 1px solid var(--color-border-2); }
.section-title { display: flex; gap: 8px; align-items: center; justify-content: space-between; color: var(--color-text-1); font-weight: 650; }
.section-title span { display: inline-flex; gap: 6px; align-items: center; }
.section-hint { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.section-hint.is-warning { color: var(--color-danger-6); }
.dependency-list { display: flex; flex-wrap: wrap; gap: 8px; }

/* 依赖行：状态一目了然，可直接跳到被依赖插件 */
.dependency-rows { display: grid; gap: 8px; }
.dependency-row { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto auto 16px; gap: 10px; align-items: center; width: 100%; min-width: 0; padding: 9px 12px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); text-align: left; transition: border-color .16s ease, background-color .16s ease, transform .16s cubic-bezier(.32, .72, 0, 1); }
.dependency-row:hover { transform: translateX(2px); border-color: var(--color-border-3); background: var(--color-fill-1); }
.dependency-row:hover .dependency-row__jump { opacity: 1; transform: none; }
.dependency-row.is-missing { border-style: dashed; }
.dependency-row__icon { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 8px; background: var(--color-fill-2); color: var(--color-text-2); font-size: 15px; }
.dependency-row__body { display: grid; gap: 2px; min-width: 0; }
.dependency-row__body strong { color: var(--color-text-1); font-size: 13px; font-weight: 600; }
.dependency-row__body code { color: var(--color-text-3); font-size: 11px; }
.dependency-row__jump { opacity: 0; color: var(--color-text-3); transform: translateX(-3px); transition: opacity .16s ease, transform .16s cubic-bezier(.32, .72, 0, 1); }

/* 雅致/紧凑外观与系统「减少动态效果」下关闭位移与入场动画 */
:global([data-admin-style='calm']) .plugin-row,
:global([data-admin-style='compact']) .plugin-row { animation: none; }
@media (prefers-reduced-motion: reduce) {
  .plugin-row { animation: none; }
  .plugin-row:hover,
  .dependency-row:hover { transform: none; }
  .plugin-row:hover .plugin-row__icon { transform: none; }
  .plugin-row:hover .plugin-row__chevron { transform: none; }
}

/* 窄屏单窗格：选中插件后展示详情并提供返回列表，不再需要长距离滚动 */
@media (max-width: 900px) {
  .plugin-workbench { grid-template-columns: minmax(0, 1fr); }
  .plugin-workbench.is-detail .plugin-browser { display: none; }
  .plugin-workbench:not(.is-detail) .plugin-detail { display: none; }
  .plugin-workbench > * { max-height: none; min-height: 0; }
  .plugin-browser { max-height: none; }
  .plugin-catalog { max-height: min(560px, 58dvh); }
  .plugin-browser::after { bottom: 64px; }
  .detail-back { display: inline-flex; }
  .detail-title { align-items: flex-start; }
  .detail-title__text h2 { overflow-wrap: anywhere; }
  .dependency-row { grid-template-columns: 30px minmax(0, 1fr) auto; row-gap: 6px; }
  .dependency-row__jump { display: none; }
  .plugin-pagination { overflow-x: auto; }
}
@media (max-width: 560px) {
  .plugin-row { grid-template-columns: auto minmax(0, 1fr); grid-template-areas: 'icon name' 'icon code' 'icon desc' 'side side'; row-gap: 3px; }
  .plugin-row__side { flex-direction: row; align-items: center; justify-content: space-between; gap: 8px; padding-top: 6px; border-top: 1px solid var(--color-border-2); }
  .plugin-row__update { max-width: none; }
  .plugin-detail { padding: 16px; }
  .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .update-facts { gap: 16px; }
  .detail-actions > * { flex: 1 1 auto; }
}

/* 窄屏工具栏已经占掉半屏：次级排序筛选收起，把高度让给列表与详情 */
@media (max-width: 768px) {
  .plugin-sort-filter { display: none; }
}
</style>
