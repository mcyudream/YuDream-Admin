<script setup lang="ts">
import type { PluginFrontendModule, PluginFrontendSortPayload, PluginModule, PluginStatus } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import router from '@/router'
import { refreshDynamicRoutes } from '@/router/dynamic'

const toast = useFaToast()
const modal = useFaModal()
const loading = ref(false)
const manifestLoading = ref(false)
const actionLoading = ref('')
const rows = ref<PluginModule[]>([])
const frontendModules = ref<PluginFrontendModule[]>([])
const sortDrafts = ref<Record<string, PluginFrontendSortPayload>>({})
const selectedCode = ref('')

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value) || rows.value[0])
const selectedFrontendModules = computed(() => {
  if (!selected.value) {
    return []
  }
  return frontendModules.value.filter(module => module.pluginCode === selected.value?.code)
})
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

async function load() {
  loading.value = true
  try {
    const res = await apiPlugin.list()
    rows.value = res.data
    syncSelectedCode()
    await loadFrontendManifest()
  }
  finally {
    loading.value = false
  }
}

async function refresh() {
  loading.value = true
  try {
    const res = await apiPlugin.refresh()
    rows.value = res.data
    syncSelectedCode()
    await loadFrontendManifest()
    await refreshDynamicRoutes(router)
    toast.success('插件目录已扫描')
  }
  finally {
    loading.value = false
  }
}

async function runAction(code: string, action: 'load' | 'enable' | 'disable' | 'unload') {
  actionLoading.value = `${code}:${action}`
  try {
    const res = await apiPlugin[action](code)
    replaceItem(res.data)
    if (['enable', 'disable', 'unload'].includes(action)) {
      await loadFrontendManifest()
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
        await loadFrontendManifest()
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

async function loadFrontendManifest() {
  manifestLoading.value = true
  try {
    const res = await apiPlugin.frontendManifest()
    frontendModules.value = res.data.modules || []
    syncSortDrafts()
  }
  finally {
    manifestLoading.value = false
  }
}

function moduleKey(module: PluginFrontendModule) {
  return `${module.pluginCode}:${module.moduleName || ''}`
}

function createSortPayload(module: PluginFrontendModule): PluginFrontendSortPayload {
  return {
    moduleName: module.moduleName,
    menuSort: module.menuSort ?? 0,
    routes: (module.routes || []).map(route => ({
      path: route.path,
      name: route.name,
      sort: route.sort ?? 0,
      parentSort: route.parentSort ?? 0,
    })),
  }
}

function syncSortDrafts() {
  const next: Record<string, PluginFrontendSortPayload> = {}
  for (const module of frontendModules.value) {
    const key = moduleKey(module)
    next[key] = sortDrafts.value[key] || createSortPayload(module)
  }
  sortDrafts.value = next
}

function sortDraft(module: PluginFrontendModule) {
  const key = moduleKey(module)
  if (!sortDrafts.value[key]) {
    sortDrafts.value = {
      ...sortDrafts.value,
      [key]: createSortPayload(module),
    }
  }
  return sortDrafts.value[key]
}

function resetSortDraft(module: PluginFrontendModule) {
  sortDrafts.value = {
    ...sortDrafts.value,
    [moduleKey(module)]: createSortPayload(module),
  }
}

async function saveFrontendSort(module: PluginFrontendModule) {
  actionLoading.value = `${module.pluginCode}:frontend-sort:${module.moduleName || ''}`
  try {
    const res = await apiPlugin.saveFrontendSort(module.pluginCode, normalizeSortPayload(sortDraft(module)))
    replaceFrontendModule(res.data)
    syncSortDrafts()
    await refreshDynamicRoutes(router)
    toast.success('菜单排序已保存')
  }
  finally {
    actionLoading.value = ''
  }
}

function replaceFrontendModule(module: PluginFrontendModule) {
  const index = frontendModules.value.findIndex(item => item.pluginCode === module.pluginCode && item.moduleName === module.moduleName)
  if (index >= 0) {
    frontendModules.value[index] = module
  }
  else {
    frontendModules.value.push(module)
  }
}

function normalizeSortPayload(payload: PluginFrontendSortPayload): PluginFrontendSortPayload {
  return {
    moduleName: payload.moduleName,
    menuSort: normalizeSort(payload.menuSort),
    routes: payload.routes.map(route => ({
      path: route.path,
      name: route.name,
      sort: normalizeSort(route.sort),
      parentSort: normalizeSort(route.parentSort),
    })),
  }
}

function normalizeSort(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
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
        <FaButton :loading="loading" @click="load">
          <FaIcon name="i-ri:refresh-line" />
          刷新列表
        </FaButton>
        <FaButton v-auth="'platform:plugin:manage'" variant="outline" :loading="loading" @click="refresh">
          <FaIcon name="i-ri:folder-search-line" />
          扫描目录
        </FaButton>
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

      <div class="plugin-layout">
        <section class="plugin-list">
          <button
            v-for="item in rows"
            :key="item.code"
            class="plugin-item"
            :class="{ active: selected?.code === item.code }"
            type="button"
            @click="selectedCode = item.code"
          >
            <FaIcon name="i-ri:puzzle-2-line" />
            <span>{{ item.name }}</span>
            <FaTag :variant="statusVariant(item.status)">{{ statusText(item.status) }}</FaTag>
          </button>
          <div v-if="!rows.length && !loading" class="empty-state">
            暂无插件。将插件 JAR 放入 plugins 目录后点击扫描目录。
          </div>
        </section>

        <section v-if="selected" class="plugin-detail">
          <div class="detail-header">
            <div>
              <h2>{{ selected.name }}</h2>
              <p>{{ selected.description || '-' }}</p>
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

          <div class="frontend-menu-panel">
            <div class="section-title">注册菜单路由</div>
            <div v-if="manifestLoading" class="menu-empty">
              正在读取插件菜单
            </div>
            <template v-else-if="selectedFrontendModules.length">
              <div v-for="module in selectedFrontendModules" :key="moduleKey(module)" class="frontend-module">
                <div class="frontend-module__header">
                  <div>
                    <strong>{{ module.menuTitle || module.moduleName || module.pluginCode }}</strong>
                    <span>{{ module.moduleName || '-' }}</span>
                  </div>
                  <label class="sort-field">
                    <span>顶层排序</span>
                    <input v-model.number="sortDraft(module).menuSort" type="number">
                  </label>
                </div>

                <div class="route-table">
                  <div class="route-row route-head">
                    <span>路由</span>
                    <span>组件</span>
                    <span>目录排序</span>
                    <span>页面排序</span>
                  </div>
                  <div v-for="(route, routeIndex) in module.routes" :key="route.name || route.path" class="route-row">
                    <div class="route-title">
                      <strong>{{ route.title }}</strong>
                      <small>{{ route.path }}</small>
                      <em v-if="route.parentTitle">{{ route.parentTitle }}</em>
                    </div>
                    <span class="route-component">{{ route.component || '-' }}</span>
                    <input
                      v-if="route.parentTitle"
                      v-model.number="sortDraft(module).routes[routeIndex].parentSort"
                      class="route-sort-input"
                      type="number"
                    >
                    <span v-else class="route-empty">-</span>
                    <input
                      v-model.number="sortDraft(module).routes[routeIndex].sort"
                      class="route-sort-input"
                      type="number"
                    >
                  </div>
                </div>

                <div class="frontend-menu-actions">
                  <FaButton variant="outline" size="sm" @click="resetSortDraft(module)">
                    恢复注册值
                  </FaButton>
                  <FaButton
                    v-auth="'platform:plugin:manage'"
                    size="sm"
                    :loading="actionLoading === `${module.pluginCode}:frontend-sort:${module.moduleName || ''}`"
                    @click="saveFrontendSort(module)"
                  >
                    <FaIcon name="i-ri:save-3-line" />
                    保存排序
                  </FaButton>
                </div>
              </div>
            </template>
            <div v-else class="menu-empty">
              当前插件暂无已注册的前端菜单。启用插件后可在这里查看并调整排序。
            </div>
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
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.plugin-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-item,
.plugin-list,
.plugin-detail {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
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

.plugin-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.plugin-list {
  display: grid;
  gap: 8px;
  padding: 12px;
}

.plugin-item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-1);
  text-align: left;
}

.plugin-item.active,
.plugin-item:hover {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}

.plugin-item span,
.detail-grid strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-detail {
  min-width: 0;
  padding: 16px;
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
.frontend-menu-panel,
.detail-actions {
  margin-top: 16px;
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

.frontend-menu-panel {
  min-width: 0;
}

.frontend-module {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.frontend-module + .frontend-module {
  margin-top: 12px;
}

.frontend-module__header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.frontend-module__header div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.frontend-module__header strong {
  overflow: hidden;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.frontend-module__header span,
.route-title small,
.route-title em,
.route-component,
.route-empty {
  color: var(--color-text-3);
  font-size: 12px;
}

.sort-field {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
  color: var(--color-text-3);
  font-size: 12px;
}

.sort-field input,
.route-sort-input {
  width: 82px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
}

.route-table {
  display: grid;
  min-width: 0;
  overflow-x: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.route-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.4fr) minmax(150px, 1fr) 92px 92px;
  gap: 10px;
  align-items: center;
  min-width: 620px;
  padding: 9px 10px;
  border-top: 1px solid var(--color-border-2);
}

.route-row:first-child {
  border-top: 0;
}

.route-head {
  background: var(--color-fill-2);
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
}

.route-title {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  min-width: 0;
  align-items: center;
}

.route-title strong {
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-title small,
.route-title em,
.route-component {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-title em {
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--color-fill-2);
  font-style: normal;
}

.frontend-menu-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.menu-empty {
  padding: 24px 12px;
  border: 1px dashed var(--color-border-2);
  border-radius: 6px;
  color: var(--color-text-3);
  text-align: center;
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

@media (max-width: 900px) {
  .plugin-layout {
    grid-template-columns: 1fr;
  }
}
</style>
