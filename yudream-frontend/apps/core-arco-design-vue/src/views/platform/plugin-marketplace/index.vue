<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { PluginMarketSource } from '@/api/modules/platform-plugin-market-source'
import type { PluginMarketplaceBatchInstallItem, PluginMarketplaceInstallPlan, PluginMarketplaceUpdatePlan, PluginStorePlugin, PluginStorePluginDescriptor, PluginStorePluginDetail, PluginStorePluginVersion } from '@/api/modules/platform-plugin-marketplace'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketSource from '@/api/modules/platform-plugin-market-source'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'
import { useAppFeatureStore } from '@/store/modules/app/features'
import InstallDependencyDialog from './install-dependency-dialog.vue'
import { compareSemVer } from './semver'
import VersionCard from './version-card.vue'

type MarketplaceVersion = PluginStorePluginVersion
type MarketplaceStatus = 'all' | 'uninstalled' | 'update' | 'installed' | 'local-newer'

const loading = ref(false)
const keyword = ref('')
const status = ref<MarketplaceStatus>('all')
const sourceFilter = ref('all')
const categoryFilter = ref('all')
const tagFilter = ref<string[]>([])
const publishedAfter = ref('')
const publishedBefore = ref('')
const advancedFiltersOpen = ref(false)
const publishedRange = computed<(string | number)[] | undefined>({
  get: () => publishedAfter.value && publishedBefore.value ? [publishedAfter.value, publishedBefore.value] : undefined,
  set: (value) => {
    publishedAfter.value = typeof value?.[0] === 'string' ? value[0] : ''
    publishedBefore.value = typeof value?.[1] === 'string' ? value[1] : ''
  },
})
const sources = ref<PluginMarketSource[]>([])
const pagination = reactive({ page: 1, size: 12, total: 0 })
const rows = ref<PluginStorePlugin[]>([])
const modules = ref<PluginModule[]>([])
const selectedCode = ref('')
const drawerOpen = ref(false)
const detailLoading = ref(false)
let detailRequest = 0
const detail = ref<PluginStorePluginDetail>()
const installingVersion = ref('')
const updatingVersion = ref('')
const rollingBackCode = ref('')
const installPlan = ref<PluginMarketplaceInstallPlan | null>(null)
const installTargetSourceCode = ref<string>()
const installPlanOpen = ref(false)
const installPlanLoading = ref(false)
const toast = useYdToast()
const modal = useYdModal()
const router = useRouter()
const featureStore = useAppFeatureStore()
const publicMarketEnabled = computed(() => featureStore.publicPluginMarketEnabled)

const statusOptions: { label: string, value: MarketplaceStatus }[] = [
  { label: '全部状态', value: 'all' },
  { label: '未安装', value: 'uninstalled' },
  { label: '可更新', value: 'update' },
  { label: '已安装', value: 'installed' },
  { label: '本地版本较新', value: 'local-newer' },
]

const sourceOptions = computed(() => [
  { label: '全部来源', value: 'all' },
  ...sources.value.filter(item => item.enabled).map(item => ({ label: item.name, value: item.code })),
])
const showSourceFilter = computed(() => sources.value.filter(item => item.enabled).length > 1)

const categoryOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const item of rows.value) {
    const category = getDescriptor(item).category?.trim()
    if (category) {
      counts.set(category, (counts.get(category) || 0) + 1)
    }
  }
  return [
    { label: '全部分类', value: 'all' },
    ...[...counts.entries()]
      .sort((left, right) => right[1] - left[1])
      .map(([name, count]) => ({ label: `${name}（${count}）`, value: name })),
  ]
})

const tagOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const item of rows.value) {
    for (const tag of getDescriptor(item).tags || []) {
      const trimmed = tag.trim()
      if (trimmed) {
        counts.set(trimmed, (counts.get(trimmed) || 0) + 1)
      }
    }
  }
  return [...counts.entries()]
    .sort((left, right) => right[1] - left[1])
    .slice(0, 24)
    .map(([name, count]) => ({ name, count }))
})

const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    const descriptor = getDescriptor(item)
    const matchesKeyword = !value || [item.code, descriptor.code, descriptor.displayName, descriptor.description, descriptor.version]
      .some(field => field?.toLowerCase().includes(value))
    const matchesSource = sourceFilter.value === 'all' || item.sourceCode === sourceFilter.value
    const matchesCategory = categoryFilter.value === 'all' || descriptor.category?.trim() === categoryFilter.value
    const matchesTags = !tagFilter.value.length || (descriptor.tags || []).some(tag => tagFilter.value.includes(tag.trim()))
    const matchesTime = matchesTimeRange(descriptor)
    return matchesKeyword && matchesSource && matchesCategory && matchesTags && matchesTime && (status.value === 'all' || marketplaceStatus(item) === status.value)
  })
})
const pagedRows = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRows.value.slice(start, start + pagination.size)
})
const hasActiveFilters = computed(() => Boolean(keyword.value.trim()) || status.value !== 'all' || sourceFilter.value !== 'all' || categoryFilter.value !== 'all' || Boolean(tagFilter.value.length) || Boolean(publishedAfter.value) || Boolean(publishedBefore.value))

/** 按 descriptor 的发布/更新时间做日期区间过滤（无时间的插件在有界筛选时隐藏）。 */
function matchesTimeRange(descriptor: PluginStorePluginDescriptor) {
  const time = descriptor.updatedAt || descriptor.publishedAt || ''
  const day = time.slice(0, 10)
  if (publishedAfter.value && (!day || day < publishedAfter.value)) {
    return false
  }
  if (publishedBefore.value && (!day || day > publishedBefore.value)) {
    return false
  }
  return true
}

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value))
const localModule = computed(() => modules.value.find(item => item.code === selectedCode.value))
const canRollback = computed(() => Boolean(localModule.value?.rollbackAvailable))

const sortedVersions = computed(() => {
  const versions = detail.value?.versions || []
  return [...versions].sort((left, right) =>
    compareSemVer(right.releaseVersion, left.releaseVersion) ?? right.releaseVersion.localeCompare(left.releaseVersion),
  )
})
const latestVersion = computed(() => sortedVersions.value[0])
const historyVersions = computed(() => sortedVersions.value.slice(1))
const showHistory = ref(false)

watch(selectedCode, () => {
  showHistory.value = false
})

watch([keyword, status, sourceFilter, categoryFilter, tagFilter, publishedAfter, publishedBefore], () => {
  pagination.page = 1
})
watch(filteredRows, clampPage, { immediate: true })
watch(() => pagination.size, clampPage)
watch(pagedRows, (visible) => {
  if (selectedCode.value && !visible.some(item => item.code === selectedCode.value)) {
    drawerOpen.value = false
  }
})
watch(drawerOpen, (open) => {
  if (!open) {
    detailRequest++
    selectedCode.value = ''
    detail.value = undefined
    detailLoading.value = false
    showHistory.value = false
  }
})

onMounted(load)

async function loadSources() {
  try {
    const res = await apiPluginMarketSource.list()
    sources.value = res.data
  }
  catch {
    sources.value = []
  }
}

async function load() {
  loading.value = true
  await loadSources()
  try {
    const [marketplaceRes, pluginsRes] = await Promise.all([
      apiPluginMarketplace.list(),
      apiPlugin.list(),
    ])
    rows.value = marketplaceRes.data
    modules.value = pluginsRes.data
    clampPage()
    if (drawerOpen.value && selectedCode.value && pagedRows.value.some(item => item.code === selectedCode.value)) {
      await loadDetail(selectedCode.value)
    }
    else {
      drawerOpen.value = false
    }
  }
  catch {
    toast.error('加载插件市场或本地插件状态失败')
  }
  finally {
    loading.value = false
  }
}

async function selectPlugin(code: string) {
  selectedCode.value = code
  detail.value = undefined
  drawerOpen.value = true
  await loadDetail(code)
}

async function loadDetail(code: string) {
  const request = ++detailRequest
  detail.value = undefined
  detailLoading.value = true
  try {
    const res = await apiPluginMarketplace.detail(code)
    if (request === detailRequest && drawerOpen.value && selectedCode.value === code) {
      detail.value = res.data
    }
  }
  catch {
    if (request === detailRequest && drawerOpen.value) {
      toast.error('加载插件详情失败')
    }
  }
  finally {
    if (request === detailRequest) {
      detailLoading.value = false
    }
  }
}

function getDescriptor(item: PluginStorePlugin | MarketplaceVersion): PluginStorePluginDescriptor {
  if (item.descriptor) {
    return item.descriptor
  }
  return item as PluginStorePluginDescriptor
}

function marketplaceStatus(item: PluginStorePlugin | MarketplaceVersion): Exclude<MarketplaceStatus, 'all'> {
  const descriptor = getDescriptor(item)
  const code = item.code ?? descriptor.code
  const releaseVersion = item.releaseVersion ?? descriptor.releaseVersion ?? descriptor.version
  const module = modules.value.find(localItem => localItem.code === code)
  if (!module) {
    return 'uninstalled'
  }
  const comparison = compareSemVer(releaseVersion || '', module.version || '')
  if (comparison === undefined || comparison < 0) {
    return 'local-newer'
  }
  return comparison > 0 ? 'update' : 'installed'
}

function marketplaceStatusLabel(item: PluginStorePlugin | MarketplaceVersion) {
  switch (marketplaceStatus(item)) {
    case 'uninstalled': return '未安装'
    case 'update': return '可更新'
    case 'installed': return '已安装'
    case 'local-newer': return '本地版本较新'
  }
}

function clampPage() {
  pagination.total = filteredRows.value.length
  const maxPage = Math.max(1, Math.ceil(pagination.total / pagination.size))
  pagination.page = Math.min(Math.max(1, pagination.page), maxPage)
}

function resetFilters() {
  keyword.value = ''
  status.value = 'all'
  sourceFilter.value = 'all'
  categoryFilter.value = 'all'
  tagFilter.value = []
  publishedAfter.value = ''
  publishedBefore.value = ''
}

function toggleTag(tag: string) {
  const index = tagFilter.value.indexOf(tag)
  if (index >= 0) {
    tagFilter.value.splice(index, 1)
  }
  else {
    tagFilter.value.push(tag)
  }
}

function openPublicMarket() {
  window.open('/market', '_blank')
}

function openAddSource() {
  router.push('/platform/plugin-marketplace/add-source')
}

function openGit(descriptor: PluginStorePluginDescriptor) {
  const url = descriptor.gitUrl || descriptor.source?.repository
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function operationsPending() {
  return Boolean(installingVersion.value || updatingVersion.value || rollingBackCode.value)
}

function versionByRelease(releaseVersion: string) {
  return detail.value?.versions.find(item => item.releaseVersion === releaseVersion)
}

async function install(releaseVersion: string) {
  if (!selectedCode.value || localModule.value || operationsPending()) {
    return
  }
  const code = selectedCode.value
  const sourceCode = versionByRelease(releaseVersion)?.sourceCode
  installingVersion.value = releaseVersion
  installPlanLoading.value = true
  try {
    const res = await apiPluginMarketplace.installPlan(code, releaseVersion, sourceCode)
    if (!drawerOpen.value || selectedCode.value !== code) {
      return
    }
    const plan = res.data
    installTargetSourceCode.value = sourceCode
    const needsAction = plan.entries.some(entry => (!entry.installed || !entry.versionSatisfied))
    if (!needsAction) {
      await installNow(code, releaseVersion, sourceCode)
      return
    }
    installPlan.value = plan
    installPlanOpen.value = true
  }
  catch {
    toast.error('获取安装计划失败，请稍后重试')
  }
  finally {
    installPlanLoading.value = false
    installingVersion.value = ''
  }
}

async function installNow(code: string, releaseVersion: string, sourceCode?: string) {
  installingVersion.value = releaseVersion
  try {
    await apiPluginMarketplace.install(code, { releaseVersion, sourceCode })
    await load()
    toast.success('插件已安装，尚未启用。可在插件管理中启用。')
  }
  catch {
    toast.error('插件安装失败')
  }
  finally {
    installingVersion.value = ''
  }
}

async function confirmInstallWithDependencies(items: PluginMarketplaceBatchInstallItem[]) {
  if (!installPlan.value) {
    return
  }
  const target: PluginMarketplaceBatchInstallItem = {
    code: installPlan.value.code,
    releaseVersion: installPlan.value.releaseVersion,
    sourceCode: installTargetSourceCode.value,
  }
  installPlanLoading.value = true
  try {
    await apiPluginMarketplace.installBatch([...items, target])
    installPlanOpen.value = false
    await load()
    toast.success(items.length
      ? `已安装 ${items.length} 个前置依赖与目标插件，尚未启用。可在插件管理中启用。`
      : '插件已安装，尚未启用。可在插件管理中启用。')
  }
  catch {
    toast.error('批量安装失败，所选内容已整体回滚')
  }
  finally {
    installPlanLoading.value = false
    installingVersion.value = ''
  }
}

async function previewAndConfirmUpdate(releaseVersion: string) {
  if (!selectedCode.value || operationsPending()) {
    return
  }
  const code = selectedCode.value
  updatingVersion.value = releaseVersion
  try {
    const res = await apiPluginMarketplace.updatePlan(code, releaseVersion)
    if (!drawerOpen.value || selectedCode.value !== code) {
      updatingVersion.value = ''
      return
    }
    const plan = res.data
    if (plan.blockedReason) {
      toast.error(`无法更新：${plan.blockedReason}`)
      updatingVersion.value = ''
      return
    }
    const sourceCode = versionByRelease(releaseVersion)?.sourceCode
    modal.confirm({
      title: `确认更新到 ${plan.toVersion}`,
      content: updateConfirmationContent(plan),
      beforeClose: (action, done) => {
        done()
        if (action !== 'confirm') {
          updatingVersion.value = ''
        }
      },
      onConfirm: async () => {
        try {
          const result = await apiPluginMarketplace.update(code, { releaseVersion, sourceCode })
          await load()
          toast.success(result.data.requiresRestart ? '插件已更新并受控停止。重启服务后将恢复此前已启用的状态。' : '插件已更新')
        }
        catch {
          toast.error('插件更新失败，请保持当前页面并重试')
        }
        finally {
          updatingVersion.value = ''
        }
      },
    })
  }
  catch {
    toast.error('获取插件更新计划失败，请稍后重试')
    updatingVersion.value = ''
  }
}

function previewAndConfirmRollback() {
  if (!selectedCode.value || !canRollback.value) {
    return
  }
  const code = selectedCode.value
  const cascade = Boolean(localModule.value?.loaded || localModule.value?.enabled)
  modal.confirm({
    title: '确认回滚本地备份',
    content: rollbackConfirmationContent(cascade),
    onConfirm: async () => {
      rollingBackCode.value = code
      try {
        const result = await apiPluginMarketplace.rollback(code, cascade)
        await load()
        toast.success(result.data.requiresRestart ? '插件已回滚，需重启服务后生效，且不会自动启用。' : '插件已回滚')
      }
      catch {
        toast.error('插件回滚失败')
      }
      finally {
        rollingBackCode.value = ''
      }
    },
  })
}

function updateConfirmationContent(plan: PluginMarketplaceUpdatePlan) {
  const impacts = plan.affectedEnabledPlugins.length
    ? `受影响且当前启用的插件：${plan.affectedEnabledPlugins.join('、')}。`
    : '没有受影响且当前启用的插件。'
  const warnings = plan.warnings.length ? `警告：${plan.warnings.join('；')}。` : ''
  return `将从 ${plan.fromVersion} 更新到 ${plan.toVersion}（${plan.changeType}）。${impacts}${warnings}更新会先受控停止相关插件；不会热加载或刷新动态路由。重启服务后会恢复此前已启用的状态。`
}

function rollbackConfirmationContent(cascade: boolean) {
  return cascade
    ? '目标插件正在运行，将按依赖顺序级联停机（含硬/软依赖方），回滚后自动恢复原先启用的软依赖方；硬依赖方若与降级版本不兼容会标记异常。降级可能与数据或依赖不兼容。'
    : '将回滚到本地保存的已知良好备份。目标插件已停止；降级可能与数据或依赖不兼容。回滚不会热加载或刷新动态路由；重启服务后会恢复此前已启用的状态。'
}
</script>

<template>
  <div>
    <YdPageHeader title="插件市场" class="mb-0">
      <template #description>
        从已订阅的市场源安装、更新插件。远程源随时可添加；本机源与公开社区需启用「插件市场源」能力。
      </template>
    </YdPageHeader>

    <YdPageMain>
      <div class="marketplace-toolbar">
        <div class="marketplace-filters">
          <YdInput v-model="keyword" clearable placeholder="搜索名称、编码、描述或版本" class="marketplace-search" />
          <YdSelect v-model="status" :options="statusOptions" class="marketplace-status" />
          <YdSelect v-model="categoryFilter" :options="categoryOptions" class="marketplace-category" />
        </div>
        <div class="marketplace-toolbar-actions">
          <span class="result-count">共 {{ pagination.total }} 个结果</span>
          <YdButton v-if="hasActiveFilters" variant="link" @click="resetFilters">
            重置筛选
          </YdButton>
          <YdButton variant="outline" :loading="loading" @click="load">
            <YdIcon name="morph:refresh-cw" />
            刷新
          </YdButton>
          <YdButton v-auth="'platform:plugin-market-source:create'" variant="outline" @click="openAddSource">
            <YdIcon name="morph:plus" />
            添加市场源
          </YdButton>
          <YdButton v-if="publicMarketEnabled" variant="outline" @click="openPublicMarket">
            <YdIcon name="morph:external-link" />
            公开社区
          </YdButton>
        </div>
      </div>

      <YdCollapsible v-model="advancedFiltersOpen" class="advanced-filters">
        <template #trigger="{ open }">
          <span class="advanced-filters__trigger">
            <YdIcon name="morph:sliders-horizontal" />
            更多筛选
            <span v-if="sourceFilter !== 'all' || tagFilter.length || publishedAfter || publishedBefore" class="advanced-filters__active">已应用</span>
            <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
          </span>
        </template>
        <div class="advanced-filters__content">
          <label v-if="showSourceFilter" class="filter-field">
            <span>市场来源</span>
            <YdSelect v-model="sourceFilter" :options="sourceOptions" class="marketplace-source" />
          </label>
          <label class="filter-field">
            <span>发布时间</span>
            <YdRangePicker v-model="publishedRange" class="marketplace-time" />
          </label>
          <div v-if="tagOptions.length" class="filter-field filter-field--tags">
            <span>标签</span>
            <div class="marketplace-tags">
              <YdButton
                v-for="tag in tagOptions"
                :key="tag.name"
                type="button"
                size="sm"
                :variant="tagFilter.includes(tag.name) ? 'secondary' : 'ghost'"
                class="marketplace-tag"
                :aria-pressed="tagFilter.includes(tag.name)"
                @click="toggleTag(tag.name)"
              >
                {{ tag.name }}<em>{{ tag.count }}</em>
              </YdButton>
            </div>
          </div>
        </div>
      </YdCollapsible>

      <section class="marketplace-browser" aria-label="插件目录">
        <div v-if="pagedRows.length" class="marketplace-list">
          <YdAction
            v-for="item in pagedRows"
            :key="item.code"
            class="plugin-card"
            type="button"
            :aria-label="`查看 ${getDescriptor(item).displayName || getDescriptor(item).code} 的详情`"
            @click="selectPlugin(item.code)"
          >
            <span class="plugin-card-heading">
              <span class="plugin-icon"><YdIcon :name="getDescriptor(item).icon || 'morph:store'" /></span>
              <span class="plugin-card-title">
                <strong>{{ getDescriptor(item).displayName || getDescriptor(item).code }}</strong>
                <span class="plugin-card-version">v{{ getDescriptor(item).version || item.releaseVersion }}</span>
              </span>
            </span>
            <span class="plugin-card-description">{{ getDescriptor(item).description || '暂无插件简介。' }}</span>
            <span class="plugin-card-meta">
              <YdTag variant="secondary">{{ marketplaceStatusLabel(item) }}</YdTag>
              <span v-if="item.sourceName" class="plugin-card-source" :title="`来源：${item.sourceName}`">{{ item.sourceName }}</span>
              <span v-if="getDescriptor(item).category" class="plugin-card-category" :title="`分类：${getDescriptor(item).category}`">{{ getDescriptor(item).category }}</span>
            </span>
          </YdAction>
        </div>
        <div v-else-if="!loading" class="empty-state">
          <template v-if="rows.length">
            暂无符合当前搜索或筛选条件的市场插件。<YdButton variant="link" @click="resetFilters">
              重置筛选
            </YdButton>
          </template>
          <template v-else>
            还没有可用插件。请先
            <YdButton v-auth="'platform:plugin-market-source:create'" variant="link" @click="openAddSource">
              添加远程市场源
            </YdButton>
            ，或启用「插件市场源」能力以使用本机源。
          </template>
        </div>
        <YdPagination
          v-if="pagination.total > pagination.size"
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          class="marketplace-pagination"
        />
      </section>

      <YdDrawer v-model="drawerOpen" :title="selected ? getDescriptor(selected).displayName || getDescriptor(selected).code : '插件详情'" :footer="false" content-class="w-full sm:max-w-[800px]">
        <section v-if="selected && detail" class="plugin-detail">
          <div class="detail-header">
            <div>
              <h2>{{ getDescriptor(selected).displayName || getDescriptor(selected).code }}</h2>
              <p>{{ getDescriptor(selected).description || '暂无插件简介。' }}</p>
              <div class="detail-meta">
                <YdTag v-if="getDescriptor(selected).category" variant="secondary">
                  {{ getDescriptor(selected).category }}
                </YdTag>
                <YdTag v-for="tag in (getDescriptor(selected).tags || []).slice(0, 6)" :key="tag" variant="secondary">
                  {{ tag }}
                </YdTag>
                <YdButton
                  v-if="getDescriptor(selected).gitUrl || getDescriptor(selected).source?.repository"
                  variant="link"
                  class="detail-git"
                  @click="openGit(getDescriptor(selected))"
                >
                  <YdIcon name="i-ri:github-line" />
                  源码仓库
                </YdButton>
              </div>
            </div>
            <YdButton
              v-if="canRollback"
              v-auth="'platform:plugin:manage'"
              size="sm"
              variant="outline"
              :loading="rollingBackCode === selectedCode"
              :disabled="operationsPending()"
              @click="previewAndConfirmRollback"
            >
              回滚本地备份
            </YdButton>
          </div>

          <template v-if="latestVersion">
            <div class="version-section-title">
              最新版本
            </div>
            <VersionCard
              :item="latestVersion"
              :local-module="localModule"
              :installing-version="installingVersion"
              :updating-version="updatingVersion"
              :pending="operationsPending()"
              @install="install"
              @update="previewAndConfirmUpdate"
            />
          </template>

          <div v-if="historyVersions.length" class="history-versions">
            <YdButton class="history-toggle" variant="ghost" type="button" :aria-expanded="showHistory" @click="showHistory = !showHistory">
              <YdIcon name="morph:chevron-right" class="history-caret" :class="{ expanded: showHistory }" />
              历史版本（{{ historyVersions.length }}）
            </YdButton>
            <div v-show="showHistory" class="history-list">
              <VersionCard
                v-for="item in historyVersions"
                :key="item.releaseVersion"
                collapsible
                :item="item"
                :local-module="localModule"
                :installing-version="installingVersion"
                :updating-version="updatingVersion"
                :pending="operationsPending()"
                @install="install"
                @update="previewAndConfirmUpdate"
              />
            </div>
          </div>
        </section>
        <div v-else-if="detailLoading" class="detail-loading">
          正在加载插件详情…
        </div>
        <div v-else class="detail-loading">
          详情暂不可用，请关闭后重试。
        </div>
      </YdDrawer>

      <InstallDependencyDialog
        v-model="installPlanOpen"
        :plan="installPlan"
        :loading="installPlanLoading"
        @confirm="confirmInstallWithDependencies"
      />
    </YdPageMain>
  </div>
</template>

<style scoped>
.marketplace-toolbar,
.marketplace-filters,
.marketplace-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.marketplace-toolbar {
  justify-content: space-between;
  margin-bottom: 8px;
}

.marketplace-filters {
  flex: 1 1 420px;
  min-width: 0;
}

.marketplace-search { flex: 1 1 260px; max-width: 420px; }
.marketplace-status { width: 140px; }
.marketplace-category { width: 170px; }
.marketplace-toolbar-actions { justify-content: flex-end; }
.result-count { color: var(--color-text-3); font-size: 13px; white-space: nowrap; }

.advanced-filters { margin-bottom: 18px; }
.advanced-filters__trigger {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  color: var(--color-text-2);
  font-size: 13px;
}
.advanced-filters__active { color: var(--color-text-1); font-weight: 600; }
.advanced-filters__content {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
  align-items: end;
  padding: 14px 0 4px;
  border-top: 1px solid var(--color-border-2);
}
.filter-field { display: grid; gap: 8px; color: var(--color-text-2); font-size: 12px; }
.filter-field--tags { flex: 1 1 100%; }
.marketplace-source { width: 190px; }
.marketplace-time { width: min(100%, 300px); }
.marketplace-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.marketplace-tag { gap: 6px; }
.marketplace-tag em { color: var(--color-text-3); font-size: 11px; font-style: normal; }

.marketplace-browser { min-width: 0; }
.marketplace-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: 14px;
}
.plugin-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  min-height: 176px;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  text-align: left;
  transition: border-color 0.15s, background-color 0.15s, transform 0.15s;
}
.plugin-card:hover {
  border-color: var(--color-text-2);
  background: var(--color-fill-1);
  transform: translateY(-2px);
}
.plugin-card-heading { display: flex; min-width: 0; gap: 12px; align-items: center; }
.plugin-icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: none;
  place-items: center;
  border-radius: 9px;
  background: var(--color-fill-2);
  font-size: 23px;
}
.plugin-card-title { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.plugin-card-title strong { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.plugin-card-version { color: var(--color-text-3); font-size: 12px; }
.plugin-card-description {
  display: -webkit-box;
  overflow: hidden;
  min-height: 38px;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.plugin-card-meta { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: auto; }
.plugin-card-source,
.plugin-card-category {
  overflow: hidden;
  max-width: 130px;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plugin-card-source::before,
.plugin-card-category::before { content: '·'; margin-right: 8px; }
.marketplace-pagination { margin-top: 20px; }
.empty-state { padding: 48px 12px; color: var(--color-text-3); text-align: center; }

.plugin-detail { min-width: 0; padding: 4px 2px 24px; }
.detail-loading { padding: 32px 0; color: var(--color-text-3); text-align: center; }
.detail-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.detail-header h2 { margin: 0; color: var(--color-text-1); font-size: 19px; }
.detail-header p { margin: 8px 0; color: var(--color-text-2); font-size: 14px; line-height: 1.6; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-top: 10px; }
.detail-git { padding: 0; font-size: 12px; }
.version-section-title { margin: 24px 0 10px; color: var(--color-text-2); font-size: 13px; font-weight: 700; }
.history-versions { margin-top: 20px; }
.history-toggle { display: inline-flex; gap: 6px; align-items: center; padding: 0; font-size: 13px; font-weight: 700; }
.history-caret { color: var(--color-text-3); transition: transform 0.15s; }
.history-caret.expanded { transform: rotate(90deg); }
.history-list { display: grid; gap: 8px; margin-top: 10px; }

@media (max-width: 640px) {
  .marketplace-filters,
  .marketplace-toolbar-actions { width: 100%; }
  .marketplace-search { max-width: none; }
  .marketplace-status,
  .marketplace-category { flex: 1 1 135px; }
  .marketplace-toolbar-actions { justify-content: flex-start; }
  .result-count { flex: 1 1 100%; }
  .marketplace-list { grid-template-columns: 1fr; }
  .detail-header { flex-direction: column; }
  .marketplace-pagination { overflow-x: auto; }
}
</style>
