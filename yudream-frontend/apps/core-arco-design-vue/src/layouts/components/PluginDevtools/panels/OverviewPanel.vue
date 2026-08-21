<script setup lang="ts">
import type { PluginLifecycleAction, PluginLifecycleEventPayload, PluginRuntimeAssetsDiffEntry } from '@/api/modules/platform-devtools'

const store = usePluginDevtoolsStore()

const recentEvents = computed(() => store.lifecycleEvents.slice(0, 20))
// 动态行展开状态：key 为事件在列表中的索引，值变化时整体重置即可
const expandedDiffs = ref<Set<number>>(new Set())

const DIFF_CATEGORY_TEXT: Record<string, string> = {
  menus: '菜单',
  permissions: '权限',
  capabilities: '能力',
  dashboardCards: '仪表盘卡片',
  frontendModules: '前端模块',
  httpEndpoints: '端点',
  commands: '指令',
  messageInteractions: '消息交互',
  aiTools: 'AI 工具',
  agents: 'Agent',
  exposedServices: '暴露服务',
}

function diffEntries(event: PluginLifecycleEventPayload): PluginRuntimeAssetsDiffEntry[] {
  return event.assetsDiff?.entries || []
}

function diffSummary(event: PluginLifecycleEventPayload) {
  const parts: string[] = []
  for (const entry of diffEntries(event)) {
    const label = DIFF_CATEGORY_TEXT[entry.category] || entry.category
    if (entry.added.length) {
      parts.push(`+${entry.added.length} ${label}`)
    }
    if (entry.removed.length) {
      parts.push(`-${entry.removed.length} ${label}`)
    }
  }
  return parts.join('、')
}

function diffEntryText(entry: PluginRuntimeAssetsDiffEntry) {
  const label = DIFF_CATEGORY_TEXT[entry.category] || entry.category
  const parts: string[] = []
  if (entry.added.length) {
    parts.push(`新增 ${entry.added.join('、')}`)
  }
  if (entry.removed.length) {
    parts.push(`移除 ${entry.removed.join('、')}`)
  }
  return `${label}：${parts.join('；')}`
}

function toggleDiff(index: number) {
  const next = new Set(expandedDiffs.value)
  if (next.has(index)) {
    next.delete(index)
  }
  else {
    next.add(index)
  }
  expandedDiffs.value = next
}

const devModeText = computed(() => {
  if (!store.status) {
    return '-'
  }
  if (!store.status.devModeEnabled) {
    return '未启用'
  }
  return store.status.devModeAuto ? '已启用（自动检测）' : '已启用（配置开启）'
})

const hostRunModeText = computed(() => {
  const mode = store.status?.hostRunMode
  return mode === 'SOURCE' ? '源码运行' : mode === 'JAR' ? 'JAR 运行' : '-'
})

function actionText(action?: PluginLifecycleAction | string) {
  const map: Record<string, string> = {
    LOAD: '加载',
    ENABLE: '启用',
    DISABLE: '禁用',
    UNLOAD: '卸载',
    RELOAD: '重载',
    FRONTEND_RELOAD: '前端重载',
    COMPILE: '编译',
  }
  return map[action || ''] || action || '-'
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString()
}

function formatDuration(ms?: number) {
  if (ms === undefined || ms === null) {
    return ''
  }
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`
}
</script>

<template>
  <div class="overview-panel">
    <div class="status-grid">
      <div class="status-card">
        <div class="status-card__label">
          开发模式
        </div>
        <div class="status-card__value">
          {{ devModeText }}
        </div>
        <div class="status-card__sub">
          宿主{{ hostRunModeText }}
        </div>
      </div>
      <div class="status-card">
        <div class="status-card__label">
          Agent 追踪
        </div>
        <div class="status-card__value">
          {{ store.status?.traceEnabled ? '已启用' : '未启用' }}
        </div>
        <div class="status-card__sub">
          实时事件流{{ store.traceConnected ? '已连接' : '未连接' }}
        </div>
      </div>
      <div class="status-card">
        <div class="status-card__label">
          插件
        </div>
        <div class="status-card__value">
          {{ store.status ? `${store.status.enabledCount}/${store.status.installedCount} 启用` : '-' }}
        </div>
        <div class="status-card__sub">
          {{ store.status?.loadedCount ?? 0 }} 个已加载
        </div>
      </div>
      <div class="status-card">
        <div class="status-card__label">
          开发项目
        </div>
        <div class="status-card__value">
          {{ store.status?.devProjects.length ?? 0 }} 个
        </div>
        <FaTooltip v-if="store.status?.devProjectStoreFile" :text="store.status.devProjectStoreFile" side="top">
          <div class="status-card__sub status-card__sub--mono">
            {{ store.status.devProjectStoreFile }}
          </div>
        </FaTooltip>
        <div v-else class="status-card__sub">
          未登记
        </div>
      </div>
    </div>

    <div class="feed-header">
      <span class="feed-header__title">
        <span class="conn-dot" :class="store.lifecycleConnected ? 'conn-dot--on' : ''" />
        最近动态
      </span>
      <div class="flex-1" />
      <FaButton variant="outline" size="sm" :disabled="!store.lifecycleEvents.length" @click="store.clearEvents()">
        <FaIcon name="i-ri:delete-bin-line" />
        清空
      </FaButton>
    </div>

    <div v-for="(event, index) in recentEvents" :key="index" class="event-row">
      <FaTag :variant="event.success ? 'default' : 'destructive'" class="text-xs shrink-0">
        {{ actionText(event.action) }}
      </FaTag>
      <div class="event-row__main">
        <div class="event-row__title">
          {{ event.pluginCode }}<span v-if="event.version" class="text-secondary-foreground/60"> v{{ event.version }}</span>
        </div>
        <div v-if="event.errorMessage" class="event-row__error">
          {{ event.errorMessage }}
        </div>
        <button
          v-if="diffEntries(event).length" type="button" class="event-row__diff"
          :aria-expanded="expandedDiffs.has(index)" @click="toggleDiff(index)"
        >
          <FaIcon :name="expandedDiffs.has(index) ? 'i-ri:arrow-down-s-line' : 'i-ri:arrow-right-s-line'" class="size-3" />
          {{ diffSummary(event) }}
        </button>
        <ul v-if="expandedDiffs.has(index)" class="event-row__diff-detail">
          <li v-for="entry in diffEntries(event)" :key="entry.category">
            {{ diffEntryText(entry) }}
          </li>
        </ul>
      </div>
      <span class="text-xs text-secondary-foreground/60 shrink-0">
        {{ formatDuration(event.durationMs) }} {{ formatTime(event.occurredAt) }}
      </span>
    </div>
    <div v-if="!recentEvents.length" class="feed-empty">
      暂无生命周期事件；加载、启用、重载、编译插件后会出现在这里
    </div>
  </div>
</template>

<style scoped>
.overview-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
}

.status-card {
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  min-width: 0;
}

.status-card__label {
  color: var(--color-text-3);
  font-size: 12px;
}

.status-card__value {
  margin-top: 2px;
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 600;
}

.status-card__sub {
  margin-top: 2px;
  color: var(--color-text-3);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-card__sub--mono {
  font-family: monospace;
  direction: rtl;
  text-align: left;
}

.feed-header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
}

.feed-header__title {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 600;
}

.conn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-fill-4, var(--color-fill-3));
}

.conn-dot--on {
  background: var(--color-success, #00b42a);
}

.event-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  min-width: 0;
  padding: 6px 0;
}

.event-row__main {
  flex: 1;
  min-width: 0;
}

.event-row__title {
  color: var(--color-text-2);
  font-size: 13px;
}

.event-row__error {
  color: var(--color-danger-6, var(--color-danger, #f53f3f));
  font-size: 12px;
  overflow-wrap: anywhere;
}

.event-row__diff {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  padding: 1px 0;
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-3);
  cursor: pointer;
  background: none;
  border: none;
}

.event-row__diff:hover {
  color: var(--color-text-1);
}

.event-row__diff-detail {
  padding: 4px 8px;
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
  list-style: none;
  background: var(--color-fill-1);
  border-radius: 4px;
}

.event-row__diff-detail li {
  overflow-wrap: anywhere;
}

.feed-empty {
  padding: 24px 0;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}
</style>
