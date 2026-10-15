<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { PluginMarketplaceBatchInstallItem, PluginMarketplaceInstallPlan, PluginMarketplaceUpdatePlan, PluginStorePlugin, PluginStorePluginDescriptor, PluginStorePluginDetail, PluginStorePluginVersion } from '@/api/modules/platform-plugin-marketplace'
import type { PluginMarketSource } from '@/api/modules/platform-plugin-market-source'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'
import apiPluginMarketSource from '@/api/modules/platform-plugin-market-source'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { compareSemVer } from './semver'
import InstallDependencyDialog from './install-dependency-dialog.vue'
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
const sources = ref<PluginMarketSource[]>([])
const pagination = reactive({ page: 1, size: 12, total: 0 })
const rows = ref<PluginStorePlugin[]>([])
const modules = ref<PluginModule[]>([])
const selectedCode = ref('')
const detail = ref<PluginStorePluginDetail>()
const installingVersion = ref('')
const updatingVersion = ref('')
const rollingBackCode = ref('')
const installPlan = ref<PluginMarketplaceInstallPlan | null>(null)
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
    if (!rows.value.some(item => item.code === selectedCode.value)) {
      selectedCode.value = rows.value[0]?.code || ''
    }
    if (selectedCode.value) {
      await loadDetail(selectedCode.value, false)
    }
    else {
      detail.value = undefined
    }
  }
  catch {
    toast.error('加载插件市场或本地插件状态失败')
  }
  finally {
    loading.value = false
  }
}

/** 窄屏：选中插件后展示详情窗格，返回按钮回到目录。 */
const mobileShowingDetail = ref(false)

async function selectPlugin(code: string) {
  selectedCode.value = code
  mobileShowingDetail.value = true
  await loadDetail(code)
}

function backToList() {
  mobileShowingDetail.value = false
}

async function loadDetail(code: string, showLoading = true) {
  if (showLoading) {
    loading.value = true
  }
  try {
    const res = await apiPluginMarketplace.detail(code)
    detail.value = res.data
  }
  catch {
    toast.error('加载插件详情失败')
  }
  finally {
    if (showLoading) {
      loading.value = false
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
  if (!selectedCode.value || localModule.value) {
    return
  }
  const sourceCode = versionByRelease(releaseVersion)?.sourceCode
  installingVersion.value = releaseVersion
  installPlanLoading.value = true
  try {
    const res = await apiPluginMarketplace.installPlan(selectedCode.value, releaseVersion, sourceCode)
    const plan = res.data
    const needsAction = plan.entries.some(entry => (!entry.installed || !entry.versionSatisfied))
    if (!needsAction) {
      await installNow(releaseVersion, sourceCode)
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

async function installNow(releaseVersion: string, sourceCode?: string) {
  if (!selectedCode.value) {
    return
  }
  installingVersion.value = releaseVersion
  try {
    await apiPluginMarketplace.install(selectedCode.value, { releaseVersion, sourceCode })
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
  if (!selectedCode.value || !installPlan.value) {
    return
  }
  const target: PluginMarketplaceBatchInstallItem = {
    code: selectedCode.value,
    releaseVersion: installPlan.value.releaseVersion,
    sourceCode: versionByRelease(installPlan.value.releaseVersion)?.sourceCode,
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
    const plan = res.data
    if (plan.blockedReason) {
      toast.error(`无法更新：${plan.blockedReason}`)
      updatingVersion.value = ''
      return
    }
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
          const result = await apiPluginMarketplace.update(code, { releaseVersion, sourceCode: versionByRelease(releaseVersion)?.sourceCode })
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
  const cascade = Boolean(localModule.value?.loaded || localModule.value?.enabled)
  modal.confirm({
    title: '确认回滚本地备份',
    content: rollbackConfirmationContent(cascade),
    onConfirm: async () => {
      rollingBackCode.value = selectedCode.value
      try {
        const result = await apiPluginMarketplace.rollback(selectedCode.value, cascade)
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
          <YdSelect v-if="showSourceFilter" v-model="sourceFilter" :options="sourceOptions" class="marketplace-source" />
          <div class="marketplace-time">
            <span class="marketplace-time__label">发布时间</span>
            <YdInput v-model="publishedAfter" type="date" class="marketplace-time__input" />
            <span class="marketplace-time__dash">–</span>
            <YdInput v-model="publishedBefore" type="date" class="marketplace-time__input" />
          </div>
        </div>
        <div class="marketplace-toolbar-actions">
          <span class="result-count">共 {{ pagination.total }} 个结果</span>
          <YdButton v-if="hasActiveFilters" variant="link" @click="resetFilters">重置筛选</YdButton>
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

      <div v-if="tagOptions.length" class="marketplace-tags">
        <YdButton
          v-for="tag in tagOptions"
          :key="tag.name"
          type="button"
          :variant="tagFilter.includes(tag.name) ? 'secondary' : 'ghost'"
          class="marketplace-tag"
          :class="{ active: tagFilter.includes(tag.name) }"
          :aria-pressed="tagFilter.includes(tag.name)"
          @click="toggleTag(tag.name)"
        >
          {{ tag.name }}<em>{{ tag.count }}</em>
        </YdButton>
      </div>

      <div class="marketplace-layout" :class="{ 'is-detail-open': mobileShowingDetail }">
        <div class="marketplace-browser">
          <div v-if="pagedRows.length" class="marketplace-list">
            <YdButton
              v-for="item in pagedRows"
              :key="item.code"
              variant="ghost"
              class="plugin-card"
              :class="{ active: selectedCode === item.code }"
              type="button"
              :aria-pressed="selectedCode === item.code"
              @click="selectPlugin(item.code)"
            >
              <YdIcon :name="getDescriptor(item).icon || 'morph:store'" class="plugin-icon" />
              <div class="plugin-card-body">
                <div class="plugin-card-title">
                  <strong>{{ getDescriptor(item).displayName || getDescriptor(item).code }}</strong>
                  <YdTag variant="secondary">{{ getDescriptor(item).version }}</YdTag>
                </div>
                <div class="plugin-card-meta">
                  <YdTag variant="secondary">{{ marketplaceStatusLabel(item) }}</YdTag>
                  <YdTag v-if="item.sourceName" variant="secondary" :title="`来源：${item.sourceName}`">{{ item.sourceName }}</YdTag>
                  <YdTag v-if="getDescriptor(item).category" variant="secondary" :title="`分类：${getDescriptor(item).category}`">{{ getDescriptor(item).category }}</YdTag>
                </div>
              </div>
            </YdButton>
          </div>
          <div v-else-if="!loading" class="empty-state">
            <template v-if="rows.length">暂无符合当前搜索或筛选条件的市场插件。<YdButton variant="link" @click="resetFilters">重置筛选</YdButton></template>
            <template v-else>
              还没有可用插件。请先
              <YdButton v-auth="'platform:plugin-market-source:create'" variant="link" @click="openAddSource">添加远程市场源</YdButton>
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
        </div>

      <section v-if="selected && detail" class="plugin-detail" :class="{ 'is-mobile-open': mobileShowingDetail }">
        <div class="detail-header">
          <div>
            <YdButton variant="ghost" size="sm" class="detail-back" @click="backToList">
              <YdIcon name="morph:arrow-left" />
              返回目录
            </YdButton>
            <h2>{{ getDescriptor(selected).displayName || getDescriptor(selected).code }}</h2>
            <p>{{ getDescriptor(selected).description || '暂无插件简介。' }}</p>
            <div class="detail-meta">
              <YdTag v-if="getDescriptor(selected).category" variant="secondary">{{ getDescriptor(selected).category }}</YdTag>
              <YdTag v-for="tag in (getDescriptor(selected).tags || []).slice(0, 6)" :key="tag" variant="secondary">{{ tag }}</YdTag>
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
      </div>

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
  gap: 10px;
  align-items: center;
}

.marketplace-toolbar {
  justify-content: space-between;
  margin-bottom: 14px;
}

.marketplace-filters {
  flex: 1;
  min-width: 0;
}

.marketplace-search {
  width: min(100%, 360px);
}

.marketplace-status {
  width: 140px;
}

.marketplace-source {
  width: 150px;
}

.marketplace-category {
  width: 170px;
}

.marketplace-time {
  display: flex;
  gap: 6px;
  align-items: center;
}

.marketplace-time__label {
  color: var(--color-text-3);
  font-size: 12px;
  white-space: nowrap;
}

.marketplace-time__input {
  width: 132px;
}

.marketplace-time__dash {
  color: var(--color-text-3);
}

.marketplace-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.marketplace-tag {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 3px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
  font-size: 12px;
  cursor: pointer;
}

.marketplace-tag em {
  color: var(--color-text-3);
  font-style: normal;
  font-size: 11px;
}

.marketplace-tag.active {
  border-color: var(--color-text-1);
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
}

.detail-meta :deep(.fa-tag) {
  max-width: 100%;
  overflow: hidden;
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail-git {
  padding: 0;
  font-size: 12px;
}

.marketplace-toolbar-actions {
  flex: none;
}

.result-count {
  color: var(--color-text-3);
  font-size: 13px;
  white-space: nowrap;
}

.marketplace-pagination {
  margin-top: 16px;
}

.marketplace-layout {
  display: grid;
  grid-template-columns: minmax(300px, 420px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.marketplace-browser,
.plugin-detail {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.marketplace-browser {
  min-width: 0;
  padding: 12px;
}

.marketplace-list {
  display: grid;
  gap: 6px;
}

.plugin-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 7px;
  color: var(--color-text-1);
  text-align: left;
}

.plugin-card.active,
.plugin-card:hover {
  border-color: var(--color-text-1);
  background: var(--color-fill-2);
}

.plugin-icon,
.plugin-icon-fallback {
  display: grid;
  width: 40px;
  height: 40px;
  flex: none;
  place-items: center;
  border-radius: 6px;
}

.plugin-icon {
  object-fit: cover;
  overflow: hidden;
  background: var(--color-fill-2);
}

.plugin-icon-fallback {
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-size: 20px;
}

.plugin-card-body {
  min-width: 0;
}

.plugin-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.plugin-card-meta :deep(.fa-tag) {
  max-width: 100%;
  overflow: hidden;
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.plugin-card-title,
.detail-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.plugin-card-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-detail {
  min-width: 0;
  padding: 16px;
}

.detail-back {
  display: none;
}

.detail-header h2 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 18px;
}

.detail-header p {
  margin: 6px 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.version-section-title {
  margin-top: 16px;
  margin-bottom: 8px;
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 700;
}

.history-versions {
  margin-top: 16px;
}

.history-toggle {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.history-toggle:hover {
  color: var(--color-text-1);
}

.history-caret {
  color: var(--color-text-3);
  transition: transform 0.15s;
}

.history-caret.expanded {
  transform: rotate(90deg);
}

.history-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.empty-state {
  padding: 32px 12px;
  color: var(--color-text-3);
  text-align: center;
}

@media (max-width: 900px) {
  .marketplace-layout {
    grid-template-columns: 1fr;
  }

  .plugin-detail {
    display: none;
  }

  .plugin-detail.is-mobile-open {
    display: block;
  }

  .marketplace-layout.is-detail-open .marketplace-browser {
    display: none;
  }

  .detail-back {
    display: inline-flex;
    margin-bottom: 4px;
  }
}

@media (max-width: 640px) {
  .marketplace-toolbar,
  .marketplace-filters,
  .marketplace-toolbar-actions,
  .detail-header {
    align-items: stretch;
    flex-direction: column;
  }

  .marketplace-toolbar-actions {
    gap: 8px;
  }

  .marketplace-search,
  .marketplace-status {
    width: 100%;
  }

  .marketplace-time {
    flex-wrap: wrap;
  }

  .result-count {
    white-space: normal;
  }

  .marketplace-pagination {
    overflow-x: auto;
  }
}
</style>
