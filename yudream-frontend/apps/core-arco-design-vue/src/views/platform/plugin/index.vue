<script setup lang="ts">
import type { TableColumn, YdFilePickerInstance } from '@yudream/components'
import type { PluginDependencyStatus, PluginDependents, PluginModule, PluginStatus } from '@/api/modules/platform-plugin'
import type { PluginMarketplaceBatchInstallItem, PluginMarketplaceUpdatePlan } from '@/api/modules/platform-plugin-marketplace'
import { YdFilterSelect } from '@yudream/components'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'
import { refreshPluginThemes } from '@/plugins/theme-runtime'
import router from '@/router'
import { refreshDynamicRoutes } from '@/router/dynamic'
import DependentsConfirmDialog from './dependents-confirm-dialog.vue'
import EnableDependencyDialog from './enable-dependency-dialog.vue'

type PluginFilterStatus = 'all' | PluginStatus | 'update'

const toast = useYdToast()
const modal = useYdModal()
const loading = ref(false)
const uploading = ref(false)
const checkingUpdates = ref(false)
const actionLoading = ref('')
const rows = ref<PluginModule[]>([])
const updatePlans = ref<PluginMarketplaceUpdatePlan[]>([])
const selectedCode = ref('')
const keyword = ref('')
const filterStatus = ref<PluginFilterStatus>('all')
const pagination = reactive({ page: 1, size: 12, total: 0 })
const uploadInput = ref<YdFilePickerInstance | null>(null)
const enableDialogOpen = ref(false)
const enableDependencyStatus = ref<PluginDependencyStatus | null>(null)
const enableLoading = ref(false)
const dependentsDialogOpen = ref(false)
const dependentsInfo = ref<PluginDependents | null>(null)
const dependentsAction = ref<'unload' | 'delete'>('unload')

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value))
const detailOpen = ref(false)
const columns: TableColumn<PluginModule>[] = [
  { accessorKey: 'name', header: '插件', minWidth: 300 },
  { accessorKey: 'version', header: '版本', width: 125 },
  { accessorKey: 'status', header: '运行状态', width: 130 },
  { id: 'update', header: '更新', width: 155 },
  { id: 'actions', header: '操作', width: 160 },
]
const filterOptions: { label: string, value: PluginFilterStatus }[] = [
  { label: '全部状态', value: 'all' },
  { label: '可更新', value: 'update' },
  { label: '运行中', value: 'ENABLED' },
  { label: '已加载', value: 'LOADED' },
  { label: '已安装', value: 'INSTALLED' },
  { label: '已禁用', value: 'DISABLED' },
  { label: '异常', value: 'ERROR' },
]
const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    const matchesKeyword = !value || [item.name, item.code, item.version, item.description]
      .some(field => field?.toLowerCase().includes(value))
    const matchesStatus = filterStatus.value === 'all'
      || (filterStatus.value === 'update' && Boolean(updatePlanFor(item.code)))
      || item.status === filterStatus.value
    return matchesKeyword && matchesStatus
  })
})
const pagedRows = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRows.value.slice(start, start + pagination.size)
})
const hasActiveFilters = computed(() => Boolean(keyword.value.trim()) || filterStatus.value !== 'all')
const summary = computed(() => {
  const enabled = rows.value.filter(item => item.enabled).length
  const loaded = rows.value.filter(item => item.loaded).length
  const error = rows.value.filter(item => item.status === 'ERROR').length
  return [
    { label: '插件总数', value: rows.value.length, icon: 'morph:puzzle' },
    { label: '已加载', value: loaded, icon: 'morph:cloud-download' },
    { label: '已启用', value: enabled, icon: 'morph:circle-check', tone: 'ok' },
    { label: '异常', value: error, icon: 'morph:triangle-alert', tone: error > 0 ? 'bad' : 'ok' },
  ]
})

onMounted(load)

watch([keyword, filterStatus], () => {
  pagination.page = 1
})
watch(filteredRows, clampPage, { immediate: true })
watch(() => pagination.size, clampPage)
watch([pagedRows, selectedCode], () => {
  if (detailOpen.value && !pagedRows.value.some(item => item.code === selectedCode.value)) {
    detailOpen.value = false
    selectedCode.value = ''
  }
}, { flush: 'sync' })

function clampPage() {
  pagination.total = filteredRows.value.length
  const maxPage = Math.max(1, Math.ceil(pagination.total / pagination.size))
  pagination.page = Math.min(Math.max(1, pagination.page), maxPage)
}

function resetFilters() {
  keyword.value = ''
  filterStatus.value = 'all'
}

async function load() {
  loading.value = true
  try {
    const res = await apiPlugin.list()
    rows.value = res.data
    updatePlans.value = []
    syncSelectedCode()
  }
  finally {
    loading.value = false
  }
}

async function checkUpdates() {
  checkingUpdates.value = true
  try {
    const res = await apiPluginMarketplace.updatePlans()
    updatePlans.value = res.data
    toast.success(updatePlans.value.length ? `已生成 ${updatePlans.value.length} 个插件更新计划` : '已检查，没有可用更新计划')
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
        const res = await apiPluginMarketplace.rollback(item.code, cascade)
        rows.value = res.data.modules
        syncSelectedCode()
        await refreshDynamicRoutes(router)
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
        const res = await apiPluginMarketplace.update(item.code, { releaseVersion: plan.toVersion })
        rows.value = res.data.modules
        syncSelectedCode()
        await checkUpdates()
        toast.success(res.data.requiresRestart ? '插件已更新，相关插件已尝试自动恢复；如仍有插件未恢复，请重启服务' : '插件已更新')
      }
      finally {
        actionLoading.value = ''
      }
    },
  })
}

async function refresh() {
  loading.value = true
  try {
    const res = await apiPlugin.refresh()
    rows.value = res.data
    updatePlans.value = []
    syncSelectedCode()
    await refreshDynamicRoutes(router)
    toast.success('插件目录已扫描')
  }
  finally {
    loading.value = false
  }
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
    const res = await apiPlugin.upload(data)
    rows.value = res.data
    updatePlans.value = []
    syncSelectedCode()
    await refreshDynamicRoutes(router)
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
    toast.success(actionText(action))
  }
  finally {
    actionLoading.value = ''
  }
}

/** 启用前依赖预览：有依赖时弹窗（硬缺失引导安装、软依赖可选启用），无依赖直接启用。 */
async function previewEnable(code: string) {
  const item = rows.value.find(row => row.code === code)
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
    toast.success(includeSoft.length
      ? `插件已启用，并一并启用了 ${includeSoft.length} 个软依赖`
      : '插件已启用，动态菜单已同步')
  }
  catch {
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

/** 卸载前依赖方预览：有运行中依赖方时弹窗提供级联，否则直接卸载。 */
async function previewUnload(code: string) {
  const item = rows.value.find(row => row.code === code)
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
    toast.success(cascade ? '插件已级联卸载；软依赖方已自动降级恢复，硬依赖方保持禁用' : '插件已卸载，动态菜单已同步')
  }
  catch {
    await load()
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
    rows.value = rows.value.filter(row => row.code !== item.code)
    syncSelectedCode()
    await refreshDynamicRoutes(router)
    await refreshPluginThemes()
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

function selectPlugin(item: PluginModule) {
  selectedCode.value = item.code
  detailOpen.value = true
}

/** 卸载/删除依赖方弹窗确认：级联执行并自动恢复软依赖方。 */
async function handleDependentsConfirm(cascade: boolean) {
  const code = dependentsInfo.value?.code
  if (!code) {
    return
  }
  dependentsDialogOpen.value = false
  if (dependentsAction.value === 'delete') {
    const item = rows.value.find(row => row.code === code)
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
}

function syncSelectedCode() {
  if (selectedCode.value && !rows.value.some(item => item.code === selectedCode.value)) {
    detailOpen.value = false
    selectedCode.value = ''
  }
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

function statusVariant(status: PluginStatus) {
  return status === 'ENABLED' ? 'default' : status === 'ERROR' ? 'destructive' : 'secondary'
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
      <div class="plugin-toolbar">
        <div class="plugin-toolbar-actions">
          <YdButton v-auth="'platform:plugin:manage'" variant="outline" :loading="uploading" @click="uploadInput?.open()">
            <YdIcon name="morph:cloud-upload" />
            上传 JAR
          </YdButton>
          <YdFilePicker ref="uploadInput" accept=".jar,application/java-archive" label="选择插件 JAR" @select="uploadJar">
            <template #default />
          </YdFilePicker>
          <YdButton variant="outline" :loading="checkingUpdates" :disabled="loading" @click="checkUpdates">
            <YdIcon name="morph:circle-arrow-up" />
            检查更新
          </YdButton>
          <YdButton v-auth="'platform:plugin:manage'" variant="outline" :loading="loading" @click="refresh">
            <YdIcon name="morph:folder-open" />
            扫描目录
          </YdButton>
        </div>
        <div class="plugin-filters">
          <YdInput v-model="keyword" clearable placeholder="搜索名称、编码、版本或描述" class="plugin-search" />
          <YdFilterSelect v-model="filterStatus" :options="filterOptions" :width="150" placeholder="全部状态" />
          <YdButton v-if="hasActiveFilters" variant="link" @click="resetFilters">
            重置筛选
          </YdButton>
        </div>
      </div>

      <div class="summary-grid">
        <div v-for="item in summary" :key="item.label" class="summary-item">
          <div class="summary-icon" :class="{ ok: item.tone === 'ok', bad: item.tone === 'bad' }">
            <YdIcon :name="item.icon" />
          </div>
          <div class="summary-body">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>

      <div class="plugin-browser">
        <div class="plugin-browser-header">
          <div>
            <h2>已发现的插件</h2>
            <p>共 {{ pagination.total }} 个插件，可查看运行状态和更新计划。</p>
          </div>
        </div>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveTable
            row-key="code"
            :columns="columns"
            :data="pagedRows"
            :empty-text="rows.length ? '暂无符合当前筛选条件的插件' : '暂无插件；将 JAR 放入 plugins 目录后扫描目录'"
            table-root-class="plugin-table-root"
            table-class="plugin-table"
            stripe
          >
            <template #cell-name="{ row }">
              <div class="plugin-identity">
                <YdIcon :name="pluginIcon(row.original)" class="plugin-identity-icon" />
                <div class="plugin-identity-text">
                  <strong>{{ row.original.name }}</strong>
                  <span class="plugin-code">{{ row.original.code }}</span>
                  <span class="plugin-description">{{ row.original.description || '暂无描述' }}</span>
                </div>
              </div>
            </template>
            <template #cell-version="{ row }">
              {{ row.original.version || '未知版本' }}
            </template>
            <template #cell-status="{ row }">
              <YdTag :variant="statusVariant(row.original.status)">
                {{ statusText(row.original.status) }}
              </YdTag>
            </template>
            <template #cell-update="{ row }">
              <YdTag v-if="updatePlanFor(row.original.code)?.blockedReason" variant="destructive">
                更新受阻
              </YdTag>
              <YdTag v-else-if="updatePlanFor(row.original.code)" variant="secondary">
                可更新至 {{ updatePlanFor(row.original.code)?.toVersion }}
              </YdTag>
              <span v-else class="plugin-muted">—</span>
            </template>
            <template #cell-actions="{ row }">
              <YdButton size="sm" variant="outline" @click="selectPlugin(row.original)">
                查看详情
              </YdButton>
            </template>
            <template #card="{ row }">
              <YdCard class="plugin-mobile-card">
                <div class="plugin-card-heading">
                  <div class="plugin-identity">
                    <YdIcon :name="pluginIcon(row)" class="plugin-identity-icon" />
                    <div class="plugin-identity-text">
                      <strong>{{ row.name }}</strong>
                      <span class="plugin-code">{{ row.code }}</span>
                    </div>
                  </div>
                  <YdTag :variant="statusVariant(row.status)">
                    {{ statusText(row.status) }}
                  </YdTag>
                </div>
                <p class="plugin-card-description">
                  {{ row.description || '暂无描述' }}
                </p>
                <div class="plugin-card-meta">
                  <span>版本 {{ row.version || '未知版本' }}</span>
                  <YdTag v-if="updatePlanFor(row.code)?.blockedReason" variant="destructive">
                    更新受阻
                  </YdTag>
                  <YdTag v-else-if="updatePlanFor(row.code)" variant="secondary">
                    可更新至 {{ updatePlanFor(row.code)?.toVersion }}
                  </YdTag>
                </div>
                <YdButton size="sm" variant="outline" @click="selectPlugin(row)">
                  查看详情
                </YdButton>
              </YdCard>
            </template>
          </YdResponsiveTable>
        </YdLoadingOverlay>
        <div v-if="!loading && !pagedRows.length && rows.length" class="plugin-empty-action">
          <YdButton variant="link" @click="resetFilters">
            重置筛选
          </YdButton>
        </div>
        <YdPagination
          v-if="pagination.total > pagination.size"
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          class="plugin-pagination"
        />
      </div>

      <YdDrawer v-model="detailOpen" :title="selected?.name || '插件详情'" :footer="false" content-class="w-full max-w-full sm:w-[760px] sm:max-w-[760px]">
        <section v-if="selected" class="plugin-detail">
          <div class="detail-header">
            <div class="plugin-detail-title">
              <YdIcon :name="pluginIcon(selected)" class="plugin-detail-icon" />
              <div>
                <h2>{{ selected.name }}</h2>
                <p>{{ selected.description || '-' }}</p>
              </div>
            </div>
            <YdTag :variant="statusVariant(selected.status)">
              {{ statusText(selected.status) }}
            </YdTag>
          </div>

          <div class="detail-grid">
            <div>
              <span>插件编码</span>
              <strong>{{ selected.code }}</strong>
            </div>
            <div>
              <span>版本</span>
              <strong>{{ selected.version || '-' }}</strong>
            </div>
            <div v-if="selected.gitUrl">
              <span>源码仓库</span>
              <strong>
                <YdButton variant="link" class="detail-git-link" @click="openGitUrl(selected.gitUrl)">
                  <YdIcon name="i-ri:github-line" />
                  {{ selected.gitUrl }}
                </YdButton>
              </strong>
            </div>
          </div>

          <div v-if="updatePlanFor(selected.code)" class="update-panel" :class="{ blocked: updatePlanFor(selected.code)?.blockedReason }">
            <div class="section-title">
              更新计划（只读）
            </div>
            <p>版本变化：{{ updatePlanFor(selected.code)?.fromVersion || '-' }} → {{ updatePlanFor(selected.code)?.toVersion || '-' }}</p>
            <p>变更类型：{{ updatePlanFor(selected.code)?.changeType || '-' }}</p>
            <p>需要重启：{{ updatePlanFor(selected.code)?.requiresRestart ? '是' : '否' }}</p>
            <p v-if="updatePlanFor(selected.code)?.blockedReason">
              阻断原因：{{ updatePlanFor(selected.code)?.blockedReason }}
            </p>

            <div v-if="updatePlanFor(selected.code)?.requiredDependencies?.length" class="plan-section">
              <strong>必需依赖</strong>
              <div class="dependency-list">
                <YdTag v-for="dependency in updatePlanFor(selected.code)?.requiredDependencies" :key="`required-${dependency.code}`" variant="secondary">
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

            <div v-if="!updatePlanFor(selected.code)?.blockedReason" class="detail-actions">
              <YdButton
                v-if="!updatePlanFor(selected.code)?.blockedReason"
                v-auth="'platform:plugin:manage'"
                :loading="actionLoading === `${selected.code}:update`"
                :disabled="Boolean(actionLoading)"
                @click="confirmUpdate(selected)"
              >
                <YdIcon name="morph:circle-arrow-up" />
                确认更新
              </YdButton>
            </div>
            <p v-if="!updatePlanFor(selected.code)?.blockedReason && (selected.enabled || updatePlanFor(selected.code)?.affectedEnabledPlugins?.length)" class="plan-warning">
              确认更新会受控停用并卸载目标插件及其依赖方；重启后仅恢复更新前已启用的插件。
            </p>
          </div>

          <div v-if="selected.rollbackAvailable" class="detail-actions-group">
            <div class="section-title">
              可用备份
            </div>
            <div class="detail-actions">
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
          </div>

          <div v-if="selected.dependencies?.length" class="dependency-panel">
            <div class="section-title">
              插件依赖
            </div>
            <div class="dependency-list">
              <YdTag v-for="dependency in selected.dependencies" :key="dependency" variant="secondary">
                {{ dependency }}
              </YdTag>
            </div>
          </div>

          <div v-if="selected.errorMessage" class="error-panel">
            {{ selected.errorMessage }}
          </div>

          <div class="detail-actions-group">
            <div class="section-title">
              生命周期动作
            </div>
            <div class="detail-actions">
              <YdButton
                variant="outline"
                :loading="actionLoading === `${selected.code}:open`"
                @click="openPluginPage(selected.code)"
              >
                <YdIcon name="morph:external-link" />
                打开插件页面
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
                v-if="!selected.enabled && selected.loaded"
                v-auth="'platform:plugin:manage'"
                variant="outline"
                :loading="actionLoading === `${selected.code}:unload`"
                @click="runAction(selected.code, 'unload')"
              >
                <YdIcon name="i-ri:eject-line" />
                卸载
              </YdButton>
            </div>
          </div>

          <div class="detail-actions-group detail-actions-group--danger">
            <div class="section-title">
              危险操作
            </div>
            <div class="detail-actions">
              <YdButton
                v-auth="'platform:plugin:manage'"
                variant="destructive"
                :loading="actionLoading === `${selected.code}:remove`"
                @click="confirmRemove(selected)"
              >
                <YdIcon name="morph:trash-2" />
                删除记录
              </YdButton>
              <p class="detail-danger-hint">
                删除只移除管理记录，不删除磁盘 JAR；有运行中依赖方时会先要求级联确认。
              </p>
            </div>
          </div>
        </section>
      </YdDrawer>

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
.plugin-toolbar,
.plugin-toolbar-actions,
.plugin-filters,
.detail-header,
.detail-actions,
.plugin-identity,
.plugin-card-heading,
.plugin-card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.plugin-toolbar {
  flex-wrap: wrap;
  justify-content: space-between;
  margin-bottom: 20px;
}

.plugin-toolbar-actions,
.plugin-filters {
  flex-wrap: wrap;
}

.plugin-search { width: min(100%, 340px); }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
}

.summary-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: none;
  place-items: center;
  border-radius: 8px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 20px;
}

.summary-body { display: grid; gap: 3px; }
.summary-body span,
.detail-grid span,
.plugin-muted { color: var(--color-text-3); font-size: 12px; }
.summary-body strong,
.detail-grid strong { color: var(--color-text-1); font-weight: 700; }

.plugin-browser {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
}

.plugin-browser-header { margin-bottom: 16px; }
.plugin-browser-header h2 { margin: 0 0 4px; color: var(--color-text-1); font-size: 16px; }
.plugin-browser-header p { margin: 0; color: var(--color-text-3); font-size: 13px; }
.plugin-browser :deep(.plugin-table-root) { overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 8px; }
.plugin-browser :deep(.plugin-table) { width: 100%; }

.plugin-identity { min-width: 0; align-items: flex-start; }
.plugin-identity-icon {
  width: 36px;
  height: 36px;
  flex: none;
  padding: 7px;
  border-radius: 8px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
}
.plugin-identity-text { display: grid; min-width: 0; gap: 3px; }
.plugin-identity-text strong { color: var(--color-text-1); font-weight: 600; }
.plugin-code, .plugin-description { color: var(--color-text-3); font-size: 12px; overflow-wrap: anywhere; }
.plugin-description { max-width: 520px; line-height: 1.5; }
.plugin-pagination { margin-top: 20px; }
.plugin-empty-action { display: flex; justify-content: center; padding-top: 8px; }

.plugin-mobile-card { width: 100%; }
.plugin-card-heading { align-items: flex-start; justify-content: space-between; }
.plugin-card-heading .plugin-identity { flex: 1; }
.plugin-card-description { margin: 12px 0; color: var(--color-text-2); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.plugin-card-meta { flex-wrap: wrap; margin-bottom: 14px; color: var(--color-text-3); font-size: 12px; }

.plugin-detail { min-width: 0; padding: 6px 4px 24px; }
.detail-header { align-items: flex-start; justify-content: space-between; }
.plugin-detail-title { display: flex; min-width: 0; align-items: flex-start; gap: 12px; }
.plugin-detail-icon {
  width: 44px;
  height: 44px;
  flex: none;
  padding: 8px;
  border-radius: 8px;
  background: var(--color-fill-2);
}
.detail-header h2 { margin: 0 0 4px; color: var(--color-text-1); font-size: 18px; }
.detail-header p { margin: 0; color: var(--color-text-3); font-size: 13px; line-height: 1.6; }
.detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; margin-top: 20px; }
.detail-grid > div { display: grid; min-width: 0; gap: 6px; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.detail-grid strong { overflow-wrap: anywhere; }
.detail-git-link { justify-self: start; max-width: 100%; padding: 0; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }

.update-panel, .dependency-panel, .detail-actions-group, .error-panel { margin-top: 20px; }
.update-panel { padding: 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.update-panel.blocked { border-color: var(--color-border-3); }
.update-panel p { margin: 6px 0 0; color: var(--color-text-2); line-height: 1.6; }
.plan-section { margin-top: 16px; }
.plan-section strong { display: block; margin-bottom: 8px; }
.plan-warning { color: var(--color-text-2); font-size: 13px; }
.warning-list { margin: 0; padding-left: 20px; color: var(--color-text-2); }
.section-title { margin-bottom: 8px; color: var(--color-text-1); font-weight: 700; }
.dependency-list, .detail-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.detail-actions { margin-top: 16px; }
.detail-actions-group { padding-top: 18px; border-top: 1px solid var(--color-border-2); }
.detail-danger-hint { flex: 1 1 240px; margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.error-panel { padding: 12px 14px; border: 1px solid var(--color-border-3); border-radius: 8px; background: var(--color-bg-1); color: var(--color-text-1); overflow-wrap: anywhere; }

@media (max-width: 768px) {
  .plugin-toolbar, .plugin-toolbar-actions, .plugin-filters, .detail-header { align-items: stretch; flex-direction: column; }
.plugin-search { width: 100%; }
  .plugin-browser { padding: 14px; }
  .plugin-pagination { overflow-x: auto; }
}
</style>
