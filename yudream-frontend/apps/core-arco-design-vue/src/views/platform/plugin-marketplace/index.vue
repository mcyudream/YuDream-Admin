<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { PluginMarketplaceUpdatePlan, PluginStorePlugin, PluginStorePluginDescriptor, PluginStorePluginDetail, PluginStorePluginVersion } from '@/api/modules/platform-plugin-marketplace'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'

type MarketplaceVersion = PluginStorePluginVersion
type VersionOperation = 'install' | 'update' | 'installed' | 'local-newer' | 'unavailable'
type MarketplaceStatus = 'all' | 'uninstalled' | 'update' | 'installed' | 'local-newer'

interface ParsedSemVer {
  major: number
  minor: number
  patch: number
  prerelease: string[]
}

const SEMVER_PATTERN = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|[0-9A-Za-z-]+)(?:\.(?:0|[1-9]\d*|[0-9A-Za-z-]+))*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/

const loading = ref(false)
const keyword = ref('')
const status = ref<MarketplaceStatus>('all')
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

const statusOptions: { label: string, value: MarketplaceStatus }[] = [
  { label: '全部状态', value: 'all' },
  { label: '未安装', value: 'uninstalled' },
  { label: '可更新', value: 'update' },
  { label: '已安装', value: 'installed' },
  { label: '本地版本较新', value: 'local-newer' },
]

const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    const descriptor = getDescriptor(item)
    const matchesKeyword = !value || [item.code, descriptor.code, descriptor.displayName, descriptor.description, descriptor.version]
      .some(field => field?.toLowerCase().includes(value))
    return matchesKeyword && (status.value === 'all' || marketplaceStatus(item) === status.value)
  })
})
const pagedRows = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRows.value.slice(start, start + pagination.size)
})
const hasActiveFilters = computed(() => Boolean(keyword.value.trim()) || status.value !== 'all')

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value))
const localModule = computed(() => modules.value.find(item => item.code === selectedCode.value))
const canRollback = computed(() => Boolean(localModule.value?.rollbackAvailable && !localModule.value.loaded && !localModule.value.enabled))

watch([keyword, status], () => {
  pagination.page = 1
})
watch(filteredRows, clampPage, { immediate: true })
watch(() => pagination.size, clampPage)

onMounted(load)

async function load() {
  loading.value = true
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

function isInstallable(item: MarketplaceVersion) {
  const descriptor = getDescriptor(item)
  return item.installable !== false && descriptor.installable !== false
}

function installDisabledReason(item: MarketplaceVersion) {
  const descriptor = getDescriptor(item)
  return item.installDisabledReason || descriptor.installDisabledReason
}

function parseSemVer(version?: string): ParsedSemVer | undefined {
  if (!version) {
    return undefined
  }
  const match = SEMVER_PATTERN.exec(version)
  if (!match) {
    return undefined
  }
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    prerelease: match[4]?.split('.') || [],
  }
}

function compareSemVer(left: string, right: string): number | undefined {
  const leftVersion = parseSemVer(left)
  const rightVersion = parseSemVer(right)
  if (!leftVersion || !rightVersion) {
    return undefined
  }
  for (const key of ['major', 'minor', 'patch'] as const) {
    if (leftVersion[key] !== rightVersion[key]) {
      return leftVersion[key] > rightVersion[key] ? 1 : -1
    }
  }
  if (!leftVersion.prerelease.length || !rightVersion.prerelease.length) {
    if (leftVersion.prerelease.length === rightVersion.prerelease.length) {
      return 0
    }
    return leftVersion.prerelease.length ? -1 : 1
  }
  const length = Math.max(leftVersion.prerelease.length, rightVersion.prerelease.length)
  for (let index = 0; index < length; index += 1) {
    const leftPart = leftVersion.prerelease[index]
    const rightPart = rightVersion.prerelease[index]
    if (leftPart === undefined || rightPart === undefined) {
      return leftPart === undefined ? -1 : 1
    }
    if (leftPart === rightPart) {
      continue
    }
    const leftNumeric = /^\d+$/.test(leftPart)
    const rightNumeric = /^\d+$/.test(rightPart)
    if (leftNumeric && rightNumeric) {
      return Number(leftPart) > Number(rightPart) ? 1 : -1
    }
    if (leftNumeric !== rightNumeric) {
      return leftNumeric ? -1 : 1
    }
    return leftPart > rightPart ? 1 : -1
  }
  return 0
}

function marketplaceStatus(item: MarketplaceVersion): Exclude<MarketplaceStatus, 'all'> {
  const module = modules.value.find(localItem => localItem.code === item.code)
  if (!module) {
    return 'uninstalled'
  }
  const comparison = compareSemVer(item.releaseVersion, module.version || '')
  if (comparison === undefined || comparison < 0) {
    return 'local-newer'
  }
  return comparison > 0 ? 'update' : 'installed'
}

function marketplaceStatusLabel(item: MarketplaceVersion) {
  switch (marketplaceStatus(item)) {
    case 'uninstalled': return '未安装'
    case 'update': return '可更新'
    case 'installed': return '已安装'
    case 'local-newer': return '本地版本较新'
  }
}

function versionOperation(item: MarketplaceVersion): VersionOperation {
  if (!localModule.value) {
    return 'install'
  }
  const comparison = compareSemVer(item.releaseVersion, localModule.value.version || '')
  if (comparison === undefined) {
    return 'unavailable'
  }
  if (comparison > 0) {
    return 'update'
  }
  return comparison === 0 ? 'installed' : 'local-newer'
}

function clampPage() {
  pagination.total = filteredRows.value.length
  const maxPage = Math.max(1, Math.ceil(pagination.total / pagination.size))
  pagination.page = Math.min(Math.max(1, pagination.page), maxPage)
}

function resetFilters() {
  keyword.value = ''
  status.value = 'all'
}

function operationLabel(item: MarketplaceVersion) {
  switch (versionOperation(item)) {
    case 'installed': return '已安装'
    case 'local-newer': return '本地版本较新'
    case 'unavailable': return '版本不可比较'
    default: return ''
  }
}

function operationTitle(item: MarketplaceVersion) {
  if (versionOperation(item) === 'unavailable') {
    return `本地版本 ${localModule.value?.version || '未知'} 或市场版本 ${item.releaseVersion} 不符合 SemVer，无法判断更新关系`
  }
  return undefined
}

function operationsPending() {
  return Boolean(installingVersion.value || updatingVersion.value || rollingBackCode.value)
}

async function install(releaseVersion: string) {
  if (!selectedCode.value || localModule.value) {
    return
  }
  installingVersion.value = releaseVersion
  try {
    await apiPluginMarketplace.install(selectedCode.value, { releaseVersion })
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
  if (!selectedCode.value) {
    return
  }
  updatingVersion.value = releaseVersion
  try {
    const res = await apiPluginMarketplace.updatePlan(selectedCode.value, releaseVersion)
    const plan = res.data
    if (plan.blockedReason) {
      toast.error(`无法更新：${plan.blockedReason}`)
      return
    }
    modal.confirm({
      title: `确认更新到 ${plan.toVersion}`,
      content: updateConfirmationContent(plan),
      onConfirm: async () => {
        updatingVersion.value = releaseVersion
        try {
          const result = await apiPluginMarketplace.update(selectedCode.value, { releaseVersion })
          await load()
          toast.success(result.data.requiresRestart ? '插件已更新并受控停止。重启服务后将恢复此前已启用的状态。' : '插件已更新')
        }
        catch {
          toast.error('插件更新失败')
        }
        finally {
          updatingVersion.value = ''
        }
      },
    })
  }
  finally {
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
        浏览可用插件、发布说明及兼容性信息。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <div class="marketplace-toolbar">
        <div class="marketplace-filters">
          <FaInput v-model="keyword" clearable placeholder="搜索名称、编码、描述或版本" class="marketplace-search" />
          <FaSelect v-model="status" :options="statusOptions" class="marketplace-status" />
        </div>
        <div class="marketplace-toolbar-actions">
          <span class="result-count">共 {{ pagination.total }} 个结果</span>
          <FaButton v-if="hasActiveFilters" variant="text" @click="resetFilters">重置筛选</FaButton>
          <FaButton variant="outline" :loading="loading" @click="load">
            <FaIcon name="i-ri:refresh-line" />
            刷新
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
          <img v-if="getDescriptor(item).icon" class="plugin-icon" :src="getDescriptor(item).icon" alt="">
          <FaIcon v-else class="plugin-icon-fallback" name="i-ri:store-2-line" />
          <div class="plugin-card-body">
            <div class="plugin-card-title">
              <strong>{{ getDescriptor(item).displayName || getDescriptor(item).code }}</strong>
              <FaTag variant="secondary">{{ getDescriptor(item).version }}</FaTag>
            </div>
            <div class="plugin-card-meta">
              <FaTag variant="secondary">{{ marketplaceStatusLabel(item) }}</FaTag>
            </div>
            <p>{{ getDescriptor(item).description || '暂无插件简介。' }}</p>
          </div>
        </button>
      </div>
      <div v-else-if="!loading" class="empty-state">
        <template v-if="rows.length">暂无符合当前搜索或筛选条件的市场插件。<FaButton variant="text" @click="resetFilters">重置筛选</FaButton></template>
        <template v-else>插件市场暂时没有可用插件。</template>
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

        <div class="version-list">
          <article v-for="item in detail.versions" :key="item.releaseVersion" class="version-card">
            <div class="detail-header">
              <div>
                <div class="version-title">
                  <h3>{{ item.releaseVersion }}</h3>
                  <FaTag v-if="operationLabel(item)" variant="secondary" :title="operationTitle(item)">{{ operationLabel(item) }}</FaTag>
                </div>
                <p>{{ getDescriptor(item).description || '暂无插件简介。' }}</p>
              </div>
              <div class="version-actions">
                <FaButton
                  v-if="versionOperation(item) === 'install'"
                  v-auth="'platform:plugin:manage'"
                  size="sm"
                  :title="installDisabledReason(item)"
                  :loading="installingVersion === item.releaseVersion"
                  :disabled="!isInstallable(item) || operationsPending()"
                  @click="install(item.releaseVersion)"
                >
                  安装
                </FaButton>
                <FaButton
                  v-else-if="versionOperation(item) === 'update'"
                  v-auth="'platform:plugin:manage'"
                  size="sm"
                  variant="outline"
                  :loading="updatingVersion === item.releaseVersion"
                  :disabled="operationsPending()"
                  @click="previewAndConfirmUpdate(item.releaseVersion)"
                >
                  更新
                </FaButton>
              </div>
            </div>

            <div class="release-notes-panel">
              <div class="section-title">发布说明</div>
              <p class="release-notes">{{ getDescriptor(item).releaseNotes || '该版本暂无发布说明。' }}</p>
            </div>

            <div v-if="getDescriptor(item).compatibility && Object.values(getDescriptor(item).compatibility || {}).some(Boolean)" class="metadata-panel">
              <div class="section-title">兼容性要求</div>
              <div class="compatibility-list">
                <FaTag v-if="getDescriptor(item).compatibility?.host" variant="secondary">宿主：{{ getDescriptor(item).compatibility?.host }}</FaTag>
                <FaTag v-if="getDescriptor(item).compatibility?.spi" variant="secondary">SPI：{{ getDescriptor(item).compatibility?.spi }}</FaTag>
                <FaTag v-if="getDescriptor(item).compatibility?.frontendSdk" variant="secondary">前端 SDK：{{ getDescriptor(item).compatibility?.frontendSdk }}</FaTag>
              </div>
            </div>
            <div v-if="getDescriptor(item).dependencies?.length" class="metadata-panel">
              <div class="section-title">插件依赖</div>
              <div class="dependency-list">
                <FaTag v-for="dependency in getDescriptor(item).dependencies" :key="dependency.code" variant="secondary">
                  {{ dependency.code }}{{ dependency.range ? ` (${dependency.range})` : '' }} · {{ dependency.required === false ? '可选' : '必需' }}
                </FaTag>
              </div>
            </div>
            <div v-if="getDescriptor(item).publisher || getDescriptor(item).license" class="metadata-panel">
              <div class="section-title">发布者与许可证</div>
              <div class="metadata-list">
                <template v-if="getDescriptor(item).publisher">
                  <strong>{{ getDescriptor(item).publisher?.name }}</strong>
                  <span>{{ getDescriptor(item).publisher?.id }}</span>
                  <FaTag v-if="getDescriptor(item).publisher?.verified" variant="secondary">已验证</FaTag>
                  <a v-if="getDescriptor(item).publisher?.url" :href="getDescriptor(item).publisher?.url" target="_blank" rel="noopener noreferrer" class="metadata-link">
                    发布者主页
                  </a>
                </template>
                <span v-if="getDescriptor(item).license">许可证：{{ getDescriptor(item).license }}</span>
              </div>
            </div>
            <div v-if="!isInstallable(item) && installDisabledReason(item)" class="install-blocked-reason">
              {{ installDisabledReason(item) }}
            </div>
            <div v-if="getDescriptor(item).screenshots?.length" class="screenshot-list">
              <img v-for="screenshot in getDescriptor(item).screenshots" :key="screenshot" :src="screenshot" :alt="`${getDescriptor(item).displayName || getDescriptor(item).code} 截图`">
            </div>

            <details class="technical-details">
              <summary>技术详情</summary>
              <div class="detail-grid">
                <div><span>插件编码</span><strong>{{ getDescriptor(item).code }}</strong></div>
                <div><span>描述符版本</span><strong>{{ getDescriptor(item).version }}</strong></div>
                <div><span>入口</span><strong class="break-all">{{ getDescriptor(item).main }}</strong></div>
                <div v-if="getDescriptor(item).jar"><span>Maven 坐标</span><strong class="break-all">{{ getDescriptor(item).jar?.mavenCoordinates }}</strong></div>
                <div v-if="getDescriptor(item).jar"><span>JAR 地址</span><strong class="break-all">{{ getDescriptor(item).jar?.url }}</strong></div>
                <div v-if="getDescriptor(item).jar"><span>SHA-256</span><strong class="break-all">{{ getDescriptor(item).jar?.sha256 }}</strong></div>
                <div v-if="getDescriptor(item).source?.repository"><span>源码仓库</span><strong class="break-all">{{ getDescriptor(item).source?.repository }}</strong></div>
                <div v-if="getDescriptor(item).source?.commit"><span>源码提交</span><strong class="break-all">{{ getDescriptor(item).source?.commit }}</strong></div>
              </div>
            </details>
          </article>
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
  min-width: 0;
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
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: 6px;
}

.plugin-icon {
  object-fit: cover;
}

.plugin-icon-fallback {
  display: grid;
  place-items: center;
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
.detail-header,
.version-title {
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

.version-title {
  justify-content: flex-start;
}

.version-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.plugin-card p,
.detail-header p {
  margin: 6px 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.plugin-detail {
  margin-top: 14px;
  padding: 16px;
}

.detail-header h2,
.detail-header h3 {
  margin: 0;
  color: var(--color-text-1);
}

.detail-header h2 {
  font-size: 18px;
}

.detail-header h3 {
  font-size: 15px;
}

.version-list {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.version-card {
  padding-top: 16px;
  border-top: 1px solid var(--color-border-2);
}

.release-notes-panel,
.metadata-panel,
.install-blocked-reason,
.technical-details {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.section-title {
  margin-bottom: 8px;
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 700;
}

.compatibility-list,
.dependency-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.compatibility-list :deep(.fa-tag),
.dependency-list :deep(.fa-tag) {
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}

.metadata-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: var(--color-text-2);
  font-size: 13px;
}

.metadata-link {
  max-width: 100%;
  color: rgb(var(--primary-6));
  overflow-wrap: anywhere;
}

.release-notes {
  margin: 0;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.install-blocked-reason {
  color: rgb(var(--danger-6));
  font-size: 13px;
}

.screenshot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.screenshot-list img {
  width: min(100%, 260px);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  object-fit: cover;
}

.technical-details {
  color: var(--color-text-2);
}

.technical-details summary {
  cursor: pointer;
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 700;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.detail-grid div {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.detail-grid span {
  color: var(--color-text-3);
  font-size: 12px;
}

.detail-grid strong {
  overflow: hidden;
  color: var(--color-text-1);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.break-all {
  white-space: normal !important;
  overflow-wrap: anywhere;
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
