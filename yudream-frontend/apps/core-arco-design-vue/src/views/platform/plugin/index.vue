<script setup lang="ts">
import type { PluginModule, PluginStatus } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'

const toast = useFaToast()
const loading = ref(false)
const actionLoading = ref('')
const rows = ref<PluginModule[]>([])
const selectedCode = ref('')

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value) || rows.value[0])
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
    if (!selectedCode.value && rows.value.length) {
      selectedCode.value = rows.value[0].code
    }
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
    toast.success('插件目录已刷新')
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
    toast.success(actionText(action))
  }
  finally {
    actionLoading.value = ''
  }
}

function replaceItem(item: PluginModule) {
  const index = rows.value.findIndex(row => row.code === item.code)
  if (index >= 0) {
    rows.value[index] = item
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
    enable: '插件已启用，刷新页面后可看到动态菜单',
    disable: '插件已禁用',
    unload: '插件已卸载',
  }
  return map[action] || '操作完成'
}
</script>

<template>
  <div>
    <FaPageHeader title="插件管理" class="mb-0">
      <template #description>
        管理插件 JAR 的发现、加载、启用、禁用和卸载，插件启用后可动态注册接口、权限和前端页面。
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
              variant="destructive"
              :disabled="selected.enabled || !selected.loaded"
              :loading="actionLoading === `${selected.code}:unload`"
              @click="runAction(selected.code, 'unload')"
            >
              <FaIcon name="i-ri:eject-line" />
              卸载
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
