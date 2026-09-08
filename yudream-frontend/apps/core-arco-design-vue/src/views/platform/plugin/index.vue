<script setup lang="ts">
import type { PluginModule, PluginStatus } from '@/api/modules/platform-plugin'
import type { PluginMarketplaceUpdatePlan } from '@/api/modules/platform-plugin-marketplace'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPluginMarketplace from '@/api/modules/platform-plugin-marketplace'
import router from '@/router'
import { refreshDynamicRoutes } from '@/router/dynamic'

type PluginFilterStatus = 'all' | PluginStatus | 'update'

const toast = useFaToast()
const modal = useFaModal()
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
const uploadInput = ref<HTMLInputElement>()

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value) || rows.value[0])
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
      || filterStatus.value === 'update' && Boolean(updatePlanFor(item.code))
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
    { label: '插件总数', value: rows.value.length, icon: 'i-ri:puzzle-2-line' },
    { label: '已加载', value: loaded, icon: 'i-ri:download-cloud-line' },
    { label: '已启用', value: enabled, icon: 'i-ri:checkbox-circle-line', tone: 'ok' },
    { label: '异常', value: error, icon: 'i-ri:error-warning-line', tone: error > 0 ? 'bad' : 'ok' },
  ]
})

onMounted(load)

watch([keyword, filterStatus], () => {
  pagination.page = 1
})
watch(filteredRows, clampPage, { immediate: true })
watch(() => pagination.size, clampPage)

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
  'ai-chatbot': 'i-ri:robot-2-line',
  'yudream-alipay': 'i-ri:alipay-line',
  'authlib-injector': 'i-ri:shield-keyhole-line',
  'mc-wiki': 'i-ri:book-open-line',
  mcguess: 'i-ri:question-answer-line',
  'minecraft-server': 'i-ri:server-line',
  'project-progress': 'i-ri:task-line',
  'qq-binding': 'i-ri:links-line',
  'qqbot-automation': 'i-ri:chat-settings-line',
  'yudream-student-info': 'i-ri:graduation-cap-line',
  'yudream-wallet': 'i-ri:wallet-3-line',
  'web-card': 'i-ri:layout-4-line',
  'world-map': 'i-ri:map-2-line',
  'yudream-skin': 'i-ri:t-shirt-2-line',
}

function pluginIcon(item: PluginModule) {
  const icon = item.icon || pluginIconFallbacks[item.code] || 'i-ri:puzzle-2-line'
  if (/^https?:\/\//.test(icon) || icon.includes(':')) {
    return icon
  }
  return `/api/platform/plugins/${item.code}/assets/${icon.replace(/^\.\//, '')}`
}

function canConfirmUpdate(item: PluginModule, plan?: PluginMarketplaceUpdatePlan) {
  return !!plan && !plan.blockedReason && actionLoading.value !== `${item.code}:update`
}

function confirmRollback(item = selected.value) {
  if (!item || !item.rollbackAvailable || item.loaded || item.enabled) {
    return
  }
  modal.confirm({
    title: '确认回滚插件',
    content: `确认将插件「${item.name}」回滚到 ${item.rollbackVersion || '本地备份'} 吗？目标插件及依赖方必须停止；回滚后需重启服务，且不会自动启用。`,
    onConfirm: async () => {
      actionLoading.value = `${item.code}:rollback`
      try {
        const res = await apiPluginMarketplace.rollback(item.code)
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

async function uploadJar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
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
  actionLoading.value = `${code}:${action}`
  try {
    const res = await apiPlugin[action](code)
    replaceItem(res.data)
    if (['enable', 'disable', 'unload'].includes(action)) {
      await refreshDynamicRoutes(router)
    }
    toast.success(actionText(action))
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
      actionLoading.value = `${item.code}:remove`
      try {
        await apiPlugin.remove(item.code)
        rows.value = rows.value.filter(row => row.code !== item.code)
        syncSelectedCode()
        await refreshDynamicRoutes(router)
        toast.success('插件记录已删除')
      }
      finally {
        actionLoading.value = ''
      }
    },
  })
}

function replaceItem(item: PluginModule) {
  const index = rows.value.findIndex(row => row.code === item.code)
  if (index >= 0) {
    rows.value[index] = item
  }
}

function syncSelectedCode() {
  if (!rows.value.length) {
    selectedCode.value = ''
    return
  }
  if (!rows.value.some(item => item.code === selectedCode.value)) {
    selectedCode.value = rows.value[0].code
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
    <FaPageHeader title="插件管理" class="mb-0">
      <template #description>
        管理插件 JAR 的发现、加载、启用、禁用、卸载和记录删除。插件启用后会动态注册接口、权限和前端页面。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <div class="plugin-toolbar">
        <div class="plugin-toolbar-actions">
          <FaButton v-auth="'platform:plugin:manage'" variant="outline" :loading="uploading" @click="uploadInput?.click()">
            <FaIcon name="i-ri:upload-cloud-2-line" />
            上传 JAR
          </FaButton>
          <input ref="uploadInput" type="file" accept=".jar,application/java-archive" hidden @change="uploadJar">
          <FaButton variant="outline" :loading="checkingUpdates" :disabled="loading" @click="checkUpdates">
            <FaIcon name="i-ri:arrow-up-circle-line" />
            检查更新
          </FaButton>
          <FaButton v-auth="'platform:plugin:manage'" variant="outline" :loading="loading" @click="refresh">
            <FaIcon name="i-ri:folder-search-line" />
            扫描目录
          </FaButton>
        </div>
        <div class="plugin-filters">
          <FaInput v-model="keyword" clearable placeholder="搜索名称、编码、版本或描述" class="plugin-search" />
          <FaSelect v-model="filterStatus" :options="filterOptions" class="plugin-status-filter" />
          <FaButton v-if="hasActiveFilters" variant="link" @click="resetFilters">重置筛选</FaButton>
        </div>
      </div>

      <div class="summary-grid">
        <div v-for="item in summary" :key="item.label" class="summary-item">
          <div class="summary-icon" :class="{ ok: item.tone === 'ok', bad: item.tone === 'bad' }">
            <FaIcon :name="item.icon" />
          </div>
          <div class="summary-body">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>

      <div class="plugin-browser">
        <div class="plugin-browser-header">
          <span class="plugin-result-count">共 {{ pagination.total }} 个插件</span>
        </div>
        <div v-if="pagedRows.length" class="plugin-grid">
          <button
            v-for="item in pagedRows"
            :key="item.code"
            class="plugin-item"
            :class="{ active: selected?.code === item.code }"
            type="button"
            @click="selectedCode = item.code"
          >
            <FaIcon :name="pluginIcon(item)" class="plugin-item-icon" />
            <div class="plugin-item-body">
              <div class="plugin-item-title">
                <strong>{{ item.name }}</strong>
                <FaTag :variant="statusVariant(item.status)">{{ statusText(item.status) }}</FaTag>
              </div>
              <span class="plugin-item-code">{{ item.code }} · {{ item.version || '未知版本' }}</span>
              <p>{{ item.description || '暂无插件简介。' }}</p>
            </div>
            <FaTag v-if="updatePlanFor(item.code)" variant="default" class="update-tag">
              可更新至 {{ updatePlanFor(item.code)?.toVersion }}
            </FaTag>
          </button>
        </div>
        <div v-else-if="!loading" class="empty-state">
          <template v-if="rows.length">暂无符合当前筛选条件的插件。<FaButton variant="link" @click="resetFilters">重置筛选</FaButton></template>
          <template v-else>暂无插件。将插件 JAR 放入 plugins 目录后点击扫描目录。</template>
        </div>
        <FaPagination
          v-if="pagination.total > pagination.size"
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          class="plugin-pagination"
        />
      </div>

      <section v-if="selected" class="plugin-detail">
          <div class="detail-header">
            <div class="plugin-detail-title">
              <FaIcon :name="pluginIcon(selected)" class="plugin-detail-icon" />
              <div>
                <h2>{{ selected.name }}</h2>
                <p>{{ selected.description || '-' }}</p>
              </div>
            </div>
            <FaTag :variant="statusVariant(selected.status)">{{ statusText(selected.status) }}</FaTag>
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
            <div>
              <span>主类</span>
              <strong>{{ selected.mainClass || '-' }}</strong>
            </div>
            <div>
              <span>JAR 路径</span>
              <strong>{{ selected.jarPath || '-' }}</strong>
            </div>
          </div>

          <div v-if="updatePlanFor(selected.code)" class="update-panel" :class="{ blocked: updatePlanFor(selected.code)?.blockedReason }">
            <div class="section-title">更新计划（只读）</div>
            <p>版本变化：{{ updatePlanFor(selected.code)?.fromVersion || '-' }} → {{ updatePlanFor(selected.code)?.toVersion || '-' }}</p>
            <p>变更类型：{{ updatePlanFor(selected.code)?.changeType || '-' }}</p>
            <p>需要重启：{{ updatePlanFor(selected.code)?.requiresRestart ? '是' : '否' }}</p>
            <p v-if="updatePlanFor(selected.code)?.blockedReason">阻断原因：{{ updatePlanFor(selected.code)?.blockedReason }}</p>

            <div v-if="updatePlanFor(selected.code)?.requiredDependencies?.length" class="plan-section">
              <strong>必需依赖</strong>
              <div class="dependency-list">
                <FaTag v-for="dependency in updatePlanFor(selected.code)?.requiredDependencies" :key="`required-${dependency.code}`" variant="secondary">
                  {{ dependency.code }}{{ dependency.range ? ` (${dependency.range})` : '' }}{{ dependency.warning ? `：${dependency.warningReason || '存在告警'}` : '' }}
                </FaTag>
              </div>
            </div>

            <div v-if="updatePlanFor(selected.code)?.optionalDependencies?.length" class="plan-section">
              <strong>可选依赖</strong>
              <div class="dependency-list">
                <FaTag v-for="dependency in updatePlanFor(selected.code)?.optionalDependencies" :key="`optional-${dependency.code}`" variant="secondary">
                  {{ dependency.code }}{{ dependency.range ? ` (${dependency.range})` : '' }}{{ dependency.warning ? `：${dependency.warningReason || '存在告警'}` : '' }}
                </FaTag>
              </div>
            </div>

            <div v-if="updatePlanFor(selected.code)?.affectedEnabledPlugins?.length" class="plan-section">
              <strong>受影响的已启用插件</strong>
              <div class="dependency-list">
                <FaTag v-for="code in updatePlanFor(selected.code)?.affectedEnabledPlugins" :key="code" variant="secondary">{{ code }}</FaTag>
              </div>
            </div>

            <div v-if="updatePlanFor(selected.code)?.warnings?.length" class="plan-section">
              <strong>警告</strong>
              <ul class="warning-list">
                <li v-for="warning in updatePlanFor(selected.code)?.warnings" :key="warning">{{ warning }}</li>
              </ul>
            </div>

            <div class="detail-actions">
              <FaButton
                v-auth="'platform:plugin:manage'"
                :disabled="!canConfirmUpdate(selected, updatePlanFor(selected.code))"
                :loading="actionLoading === `${selected.code}:update`"
                @click="confirmUpdate(selected)"
              >
                <FaIcon name="i-ri:arrow-up-circle-line" />
                确认更新
              </FaButton>
              <FaButton
                v-auth="'platform:plugin:manage'"
                variant="outline"
                :disabled="!selected.rollbackAvailable || selected.loaded || selected.enabled || Boolean(actionLoading)"
                :loading="actionLoading === `${selected.code}:rollback`"
                @click="confirmRollback(selected)"
              >
                <FaIcon name="i-ri:history-line" />
                回滚到 {{ selected.rollbackVersion || '备份版本' }}
              </FaButton>
            </div>
            <p v-if="selected.enabled || updatePlanFor(selected.code)?.affectedEnabledPlugins?.length" class="plan-warning">确认更新会受控停用并卸载目标插件及其依赖方；重启后仅恢复更新前已启用的插件。</p>
          </div>

          <div v-if="selected.dependencies?.length" class="dependency-panel">
            <div class="section-title">插件依赖</div>
            <div class="dependency-list">
              <FaTag v-for="dependency in selected.dependencies" :key="dependency" variant="secondary">
                {{ dependency }}
              </FaTag>
            </div>
          </div>

          <div v-if="selected.errorMessage" class="error-panel">
            {{ selected.errorMessage }}
          </div>

          <div class="detail-actions">
            <FaButton
              v-auth="'platform:plugin:manage'"
              variant="outline"
              :disabled="selected.loaded"
              :loading="actionLoading === `${selected.code}:load`"
              @click="runAction(selected.code, 'load')"
            >
              <FaIcon name="i-ri:download-cloud-line" />
              加载
            </FaButton>
            <FaButton
              v-auth="'platform:plugin:manage'"
              :disabled="selected.enabled"
              :loading="actionLoading === `${selected.code}:enable`"
              @click="runAction(selected.code, 'enable')"
            >
              <FaIcon name="i-ri:play-circle-line" />
              启用
            </FaButton>
            <FaButton
              v-auth="'platform:plugin:manage'"
              variant="outline"
              :disabled="!selected.enabled"
              :loading="actionLoading === `${selected.code}:disable`"
              @click="runAction(selected.code, 'disable')"
            >
              <FaIcon name="i-ri:pause-circle-line" />
              禁用
            </FaButton>
            <FaButton
              v-auth="'platform:plugin:manage'"
              variant="outline"
              :disabled="selected.enabled || !selected.loaded"
              :loading="actionLoading === `${selected.code}:unload`"
              @click="runAction(selected.code, 'unload')"
            >
              <FaIcon name="i-ri:eject-line" />
              卸载
            </FaButton>
            <FaButton
              v-auth="'platform:plugin:manage'"
              variant="destructive"
              :loading="actionLoading === `${selected.code}:remove`"
              @click="confirmRemove(selected)"
            >
              <FaIcon name="i-ri:delete-bin-line" />
              删除记录
            </FaButton>
          </div>
      </section>
    </FaPageMain>
  </div>
</template>

<style scoped>
.plugin-toolbar,
.plugin-toolbar-actions,
.plugin-filters,
.plugin-browser-header,
.plugin-item-title,
.plugin-item-tags {
  display: flex;
  gap: 10px;
  align-items: center;
}

.plugin-toolbar {
  flex-wrap: wrap;
  justify-content: space-between;
  margin-bottom: 14px;
}

.plugin-toolbar-actions,
.plugin-filters {
  flex-wrap: wrap;
}

.plugin-search {
  width: min(100%, 340px);
}

.plugin-status-filter {
  width: 140px;
}

.plugin-browser,
.plugin-detail {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.plugin-browser {
  padding: 14px;
}

.plugin-browser-header {
  justify-content: space-between;
  margin-bottom: 12px;
}

.plugin-result-count,
.plugin-item-code {
  color: var(--color-text-3);
  font-size: 12px;
}

.plugin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.plugin-item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  min-height: 116px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
  color: var(--color-text-1);
  text-align: left;
}

.plugin-item.active,
.plugin-item:hover {
  border-color: var(--color-border-3);
  background: var(--color-fill-2);
}

.plugin-item-body {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: 5px;
}

.plugin-item-title {
  min-width: 0;
  justify-content: space-between;
}

.plugin-item-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-item-body p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.update-tag {
  grid-column: 2;
  justify-self: start;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-pagination {
  margin-top: 16px;
}

.plugin-detail {
  min-width: 0;
  margin-top: 14px;
  padding: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
}

.summary-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 19px;
}

.summary-body {
  display: grid;
  gap: 4px;
}

.summary-body span,
.detail-grid span {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-body strong,
.detail-grid strong {
  color: var(--color-text-1);
  font-weight: 700;
}

.plugin-detail-title {
  display: flex;
  gap: 12px;
  min-width: 0;
  align-items: flex-start;
}

.plugin-item-icon,
.plugin-detail-icon {
  width: 22px;
  height: 22px;
  flex: none;
}

.plugin-detail-icon {
  width: 36px;
  height: 36px;
  padding: 6px;
  border-radius: 6px;
  background: var(--color-fill-2);
}



.detail-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.detail-header h2 {
  margin: 0 0 4px;
  color: var(--color-text-1);
  font-size: 18px;
  font-weight: 700;
}

.detail-header p {
  margin: 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.detail-grid div {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.dependency-panel,
.update-panel,
.detail-actions {
  margin-top: 16px;
}

.update-panel {
  padding: 12px;
  border: 1px solid rgb(var(--success-6));
  border-radius: 6px;
  background: rgb(var(--success-1));
  color: rgb(var(--success-6));
}

.update-panel.blocked {
  border-color: rgb(var(--danger-6));
  background: rgb(var(--danger-1));
  color: rgb(var(--danger-6));
}

.update-panel p {
  margin: 4px 0 0;
}

.plan-section {
  margin-top: 12px;
}

.plan-section strong {
  display: block;
  margin-bottom: 6px;
}

.plan-warning {
  color: rgb(var(--warning-6));
  font-weight: 700;
}

.warning-list {
  margin: 0;
  padding-left: 20px;
}

.section-title {
  margin-bottom: 8px;
  color: var(--color-text-1);
  font-weight: 700;
}

.dependency-list,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.error-panel {
  margin-top: 16px;
  padding: 10px 12px;
  border: 1px solid rgb(var(--danger-6));
  border-radius: 6px;
  background: rgb(var(--danger-1));
  color: rgb(var(--danger-6));
}

.empty-state {
  padding: 32px 12px;
  color: var(--color-text-3);
  text-align: center;
}

.ok {
  color: rgb(var(--success-6));
}

.bad {
  color: rgb(var(--danger-6));
}

@media (max-width: 640px) {
  .plugin-toolbar,
  .plugin-toolbar-actions,
  .plugin-filters,
  .detail-header {
    align-items: stretch;
    flex-direction: column;
  }

  .plugin-search,
  .plugin-status-filter {
    width: 100%;
  }

  .plugin-grid {
    grid-template-columns: 1fr;
  }

  .plugin-item {
    min-height: 108px;
  }

  .plugin-pagination {
    overflow-x: auto;
  }
}

</style>
