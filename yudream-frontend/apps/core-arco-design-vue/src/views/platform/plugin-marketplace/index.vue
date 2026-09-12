<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { PluginMarketplaceUpdatePlan, PluginStorePlugin, PluginStorePluginDescriptor, PluginStorePluginDetail, PluginStorePluginVersion } from '@/api/modules/platform-plugin-marketplace'
import type { PluginMarketSource } from '@/api/modules/platform-plugin-market-source'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'
import apiPluginMarketSource from '@/api/modules/platform-plugin-market-source'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { compareSemVer } from './semver'
import VersionCard from './version-card.vue'

type MarketplaceVersion = PluginStorePluginVersion
type MarketplaceStatus = 'all' | 'uninstalled' | 'update' | 'installed' | 'local-newer'

const loading = ref(false)
const keyword = ref('')
const status = ref<MarketplaceStatus>('all')
const sourceFilter = ref('all')
const sources = ref<PluginMarketSource[]>([])
const pagination = reactive({ page: 1, size: 12, total: 0 })
const rows = ref<PluginStorePlugin[]>([])
const modules = ref<PluginModule[]>([])
const selectedCode = ref('')
const detail = ref<PluginStorePluginDetail>()
const installingVersion = ref('')
const updatingVersion = ref('')
const rollingBackCode = ref('')
const toast = useFaToast()
const modal = useFaModal()
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

const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    const descriptor = getDescriptor(item)
    const matchesKeyword = !value || [item.code, descriptor.code, descriptor.displayName, descriptor.description, descriptor.version]
      .some(field => field?.toLowerCase().includes(value))
    const matchesSource = sourceFilter.value === 'all' || item.sourceCode === sourceFilter.value
    return matchesKeyword && matchesSource && (status.value === 'all' || marketplaceStatus(item) === status.value)
  })
})
const pagedRows = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRows.value.slice(start, start + pagination.size)
})
const hasActiveFilters = computed(() => Boolean(keyword.value.trim()) || status.value !== 'all' || sourceFilter.value !== 'all')

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value))
const localModule = computed(() => modules.value.find(item => item.code === selectedCode.value))
const canRollback = computed(() => Boolean(localModule.value?.rollbackAvailable && !localModule.value.loaded && !localModule.value.enabled))

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

watch([keyword, status, sourceFilter], () => {
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

async function selectPlugin(code: string) {
  selectedCode.value = code
  await loadDetail(code)
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
}

function openPublicMarket() {
  window.open('/market', '_blank')
}

function openAddSource() {
  router.push('/platform/plugin-marketplace/add-source')
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
  installingVersion.value = releaseVersion
  try {
    await apiPluginMarketplace.install(selectedCode.value, { releaseVersion, sourceCode: versionByRelease(releaseVersion)?.sourceCode })
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
  modal.confirm({
    title: '确认回滚本地备份',
    content: rollbackConfirmationContent(),
    onConfirm: async () => {
      rollingBackCode.value = selectedCode.value
      try {
        const result = await apiPluginMarketplace.rollback(selectedCode.value)
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

function rollbackConfirmationContent() {
  return '将回滚到本地保存的已知良好备份。目标插件已停止；降级可能与数据或依赖不兼容。回滚不会热加载或刷新动态路由；重启服务后会恢复此前已启用的状态。'
}
</script>

<template>
  <div>
    <FaPageHeader title="插件市场" class="mb-0">
      <template #description>
        从已订阅的市场源安装、更新插件。远程源随时可添加；本机源与公开社区需启用「插件市场源」能力。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <div class="marketplace-toolbar">
        <div class="marketplace-filters">
          <FaInput v-model="keyword" clearable placeholder="搜索名称、编码、描述或版本" class="marketplace-search" />
          <FaSelect v-model="status" :options="statusOptions" class="marketplace-status" />
          <FaSelect v-if="showSourceFilter" v-model="sourceFilter" :options="sourceOptions" class="marketplace-source" />
        </div>
        <div class="marketplace-toolbar-actions">
          <span class="result-count">共 {{ pagination.total }} 个结果</span>
          <FaButton v-if="hasActiveFilters" variant="link" @click="resetFilters">重置筛选</FaButton>
          <FaButton variant="outline" :loading="loading" @click="load">
            <FaIcon name="i-ri:refresh-line" />
            刷新
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:create'" variant="outline" @click="openAddSource">
            <FaIcon name="i-ri:add-line" />
            添加市场源
          </FaButton>
          <FaButton v-if="publicMarketEnabled" variant="outline" @click="openPublicMarket">
            <FaIcon name="i-ri:external-link-line" />
            公开社区
          </FaButton>
        </div>
      </div>

      <div v-if="pagedRows.length" class="marketplace-grid">
        <button
          v-for="item in pagedRows"
          :key="item.code"
          class="plugin-card"
          :class="{ active: selectedCode === item.code }"
          type="button"
          @click="selectPlugin(item.code)"
        >
          <FaIcon :name="getDescriptor(item).icon || 'i-ri:store-2-line'" class="plugin-icon" />
          <div class="plugin-card-body">
            <div class="plugin-card-title">
              <strong>{{ getDescriptor(item).displayName || getDescriptor(item).code }}</strong>
              <FaTag variant="secondary">{{ getDescriptor(item).version }}</FaTag>
            </div>
            <div class="plugin-card-meta">
              <FaTag variant="secondary">{{ marketplaceStatusLabel(item) }}</FaTag>
              <FaTag v-if="item.sourceName" variant="secondary" :title="`来源：${item.sourceName}`">{{ item.sourceName }}</FaTag>
            </div>
            <p class="plugin-card-description" :title="getDescriptor(item).description || '暂无插件简介。'">
              {{ getDescriptor(item).description || '暂无插件简介。' }}
            </p>
          </div>
        </button>
      </div>
      <div v-else-if="!loading" class="empty-state">
        <template v-if="rows.length">暂无符合当前搜索或筛选条件的市场插件。<FaButton variant="link" @click="resetFilters">重置筛选</FaButton></template>
        <template v-else>
          还没有可用插件。请先
          <FaButton v-auth="'platform:plugin-market-source:create'" variant="link" @click="openAddSource">添加远程市场源</FaButton>
          ，或启用「插件市场源」能力以使用本机源。
        </template>
      </div>
      <FaPagination
        v-if="pagination.total > pagination.size"
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="marketplace-pagination"
      />

      <section v-if="selected && detail" class="plugin-detail">
        <div class="detail-header">
          <div>
            <h2>{{ getDescriptor(selected).displayName || getDescriptor(selected).code }}</h2>
            <p>{{ getDescriptor(selected).description || '暂无插件简介。' }}</p>
          </div>
          <FaButton
            v-if="canRollback"
            v-auth="'platform:plugin:manage'"
            size="sm"
            variant="outline"
            :loading="rollingBackCode === selectedCode"
            :disabled="operationsPending()"
            @click="previewAndConfirmRollback"
          >
            回滚本地备份
          </FaButton>
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
          <button class="history-toggle" type="button" @click="showHistory = !showHistory">
            <FaIcon name="i-ri:arrow-right-s-line" class="history-caret" :class="{ expanded: showHistory }" />
            历史版本（{{ historyVersions.length }}）
          </button>
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
    </FaPageMain>
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

.marketplace-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 14px;
}

.plugin-card,
.plugin-detail {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.plugin-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
  height: 100%;
  padding: 14px;
  color: var(--color-text-1);
  text-align: left;
}

.plugin-card.active,
.plugin-card:hover {
  border-color: rgb(var(--primary-6));
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
  color: rgb(var(--primary-6));
  font-size: 20px;
}

.plugin-card-body {
  min-width: 0;
}

.plugin-card-meta {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.plugin-card-meta :deep(.fa-tag) {
  font-size: 11px;
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

.plugin-card-description {
  display: -webkit-box;
  min-height: 2.6em;
  margin: 6px 0;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.3;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.plugin-detail {
  margin-top: 14px;
  padding: 16px;
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
  color: rgb(var(--primary-6));
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

  .result-count {
    white-space: normal;
  }

  .marketplace-pagination {
    overflow-x: auto;
  }
}
</style>
