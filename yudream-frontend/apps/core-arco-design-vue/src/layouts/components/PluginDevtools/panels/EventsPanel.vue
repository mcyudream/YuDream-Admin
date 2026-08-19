<script setup lang="ts">
import type { PluginLifecycleAction } from '@/api/modules/platform-devtools'
import AssetSection from './AssetSection.vue'

const store = usePluginDevtoolsStore()

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
  <div class="events-panel">
    <div class="events-toolbar">
      <FaTag :variant="store.lifecycleConnected ? 'default' : 'secondary'" class="text-xs">
        生命周期流{{ store.lifecycleConnected ? '已连接' : '未连接' }}
      </FaTag>
      <FaTag :variant="store.traceConnected ? 'default' : 'secondary'" class="text-xs">
        追踪流{{ store.traceConnected ? '已连接' : '未连接' }}
      </FaTag>
      <div class="flex-1" />
      <FaButton variant="outline" size="sm" @click="store.clearEvents()">
        <FaIcon name="i-ri:delete-bin-line" />
        清空
      </FaButton>
    </div>

    <AssetSection title="插件生命周期" icon="i-ri:radar-line" :count="store.lifecycleEvents.length" default-open>
      <div v-for="(event, index) in store.lifecycleEvents" :key="index" class="event-row">
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
        </div>
        <span class="text-xs text-secondary-foreground/60 shrink-0">
          {{ formatDuration(event.durationMs) }} {{ formatTime(event.occurredAt) }}
        </span>
      </div>
      <div v-if="!store.lifecycleEvents.length" class="asset-empty">
        暂无生命周期事件
      </div>
    </AssetSection>

    <AssetSection title="Agent 追踪事件" icon="i-ri:node-tree" :count="store.traceEvents.length" default-open>
      <div v-for="(event, index) in store.traceEvents" :key="index" class="event-row">
        <FaTag :variant="event.action === 'FAILED' ? 'destructive' : 'secondary'" class="text-xs shrink-0">
          {{ event.action }}
        </FaTag>
        <div class="event-row__main">
          <div class="event-row__title">
            {{ event.agentName || event.agentCode || event.traceId }}
          </div>
          <div class="event-row__sub">
            {{ event.source }}<template v-if="event.ownerPluginCode">
              · {{ event.ownerPluginCode }}
            </template>
            <template v-if="event.step">
              · #{{ event.step.seq }} {{ event.step.nodeTitle || event.step.nodeId || '' }}
            </template>
          </div>
          <div v-if="event.error" class="event-row__error">
            {{ event.error }}
          </div>
        </div>
        <span class="text-xs text-secondary-foreground/60 shrink-0">{{ formatTime(event.occurredAt) }}</span>
      </div>
      <div v-if="!store.traceEvents.length" class="asset-empty">
        暂无追踪事件
      </div>
    </AssetSection>
  </div>
</template>

<style scoped>
.events-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 0;
}

.events-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
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

.event-row__sub {
  color: var(--color-text-3);
  font-size: 12px;
}

.event-row__error {
  color: var(--color-danger-6, #f53f3f);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.asset-empty {
  padding: 12px 0;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}
</style>
