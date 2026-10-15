<script setup lang="ts">
import type { PluginDevPlugin, PluginLogEntry } from '@/api/modules/platform-devtools'
import { YdFilterSelect } from '@yudream/components'
import apiDevtools from '@/api/modules/platform-devtools'
import { usePluginLogs } from '../usePluginLogs'

const plugins = ref<PluginDevPlugin[]>([])
const pluginsLoading = ref(false)

const {
  pluginCode,
  level,
  keyword,
  entries,
  paused,
  connected,
  loading,
  streamError,
  reload,
  togglePause,
  clear,
} = usePluginLogs()

const levelOptions = [
  { label: '全部级别', value: '' },
  { label: 'DEBUG', value: 'DEBUG' },
  { label: 'INFO', value: 'INFO' },
  { label: 'WARN', value: 'WARN' },
  { label: 'ERROR', value: 'ERROR' },
]

const pluginOptions = computed(() => [...plugins.value]
  .sort((left, right) => Number(right.enabled) - Number(left.enabled) || left.code.localeCompare(right.code))
  .map(item => ({
    label: `${item.name || item.code}${item.enabled ? '' : '（未启用）'}`,
    value: item.code,
  })))

const listRef = ref<HTMLElement | null>(null)
const expandedSequences = ref<Set<number>>(new Set())

onMounted(async () => {
  pluginsLoading.value = true
  try {
    const res = await apiDevtools.plugins()
    plugins.value = res.data || []
    if (!pluginCode.value && plugins.value.length) {
      pluginCode.value = plugins.value[0].code
    }
  }
  finally {
    pluginsLoading.value = false
  }
})

// 新日志到达时只有原本就停在底部才自动滚动，避免打断向上翻阅
watch(() => entries.value.length, async () => {
  const el = listRef.value
  if (!el) {
    return
  }
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 60
  if (nearBottom) {
    await nextTick()
    el.scrollTop = el.scrollHeight
  }
})

function toggleExpand(entry: PluginLogEntry) {
  const next = new Set(expandedSequences.value)
  if (next.has(entry.sequence)) {
    next.delete(entry.sequence)
  }
  else {
    next.add(entry.sequence)
  }
  expandedSequences.value = next
}

function shortLogger(logger: string) {
  const parts = logger.split('.')
  return parts.length > 1 ? parts[parts.length - 1] : logger
}

function levelClass(entryLevel: string) {
  if (entryLevel === 'ERROR') {
    return 'log-level log-level--error'
  }
  if (entryLevel === 'WARN') {
    return 'log-level log-level--warn'
  }
  if (entryLevel === 'DEBUG') {
    return 'log-level log-level--debug'
  }
  return 'log-level'
}
</script>

<template>
  <div class="logs-panel">
    <div class="logs-toolbar">
      <YdFilterSelect v-model="pluginCode" :options="pluginOptions" placeholder="选择插件" :loading="pluginsLoading" :width="180" />
      <YdFilterSelect v-model="level" :options="levelOptions" :width="110" />
      <YdInput v-model="keyword" placeholder="关键字过滤" class="logs-toolbar__keyword" clearable />
      <YdTooltip :text="paused ? '继续接收' : '暂停追加'" side="bottom">
        <YdButton variant="ghost" size="icon-sm" :aria-label="paused ? '继续' : '暂停'" @click="togglePause">
          <YdIcon :name="paused ? 'morph:play' : 'morph:pause'" class="size-4" />
        </YdButton>
      </YdTooltip>
      <YdTooltip text="清空列表" side="bottom">
        <YdButton variant="ghost" size="icon-sm" aria-label="清空" @click="clear">
          <YdIcon name="morph:trash-2" class="size-4" />
        </YdButton>
      </YdTooltip>
      <YdTooltip text="重新加载" side="bottom">
        <YdButton variant="ghost" size="icon-sm" aria-label="刷新" :loading="loading" @click="reload">
          <YdIcon name="morph:refresh-cw" class="size-4" />
        </YdButton>
      </YdTooltip>
    </div>

    <div class="logs-status">
      <span class="sse-dot" :class="connected ? 'sse-dot--on' : ''" />
      <span>{{ connected ? '日志流已连接' : (streamError || '日志流未连接') }}</span>
      <span v-if="paused" class="logs-status__paused">已暂停，新日志将在继续后追加</span>
      <span class="flex-1" />
      <span>{{ entries.length }} 条</span>
    </div>

    <div v-if="!pluginCode" class="logs-empty">
      <YdIcon name="morph:file-text" class="size-8" />
      <p>选择一个插件查看其运行日志</p>
      <p class="logs-empty__hint">
        日志按插件包名前缀（online.yudream.base.plugin.*）过滤，覆盖宿主环形缓冲中的近期日志
      </p>
    </div>

    <div v-else ref="listRef" class="logs-list">
      <div v-if="!entries.length && !loading" class="logs-empty">
        <p>暂无日志</p>
      </div>
      <YdAction
        v-for="entry in entries"
        :key="entry.sequence"
        type="button" :disabled="!entry.throwable" class="log-entry"
        :class="{ 'log-entry--expandable': !!entry.throwable }"
        @click="entry.throwable && toggleExpand(entry)"
      >
        <div class="log-entry__line">
          <span class="log-entry__time">{{ entry.time.slice(11) }}</span>
          <span :class="levelClass(entry.level)">{{ entry.level }}</span>
          <span class="log-entry__logger" :title="entry.logger">{{ shortLogger(entry.logger) }}</span>
          <span class="log-entry__message">{{ entry.message }}</span>
          <YdIcon
            v-if="entry.throwable"
            :name="expandedSequences.has(entry.sequence) ? 'morph:chevron-down' : 'morph:chevron-right'"
            class="shrink-0 size-3.5"
          />
        </div>
        <pre v-if="entry.throwable && expandedSequences.has(entry.sequence)" class="log-entry__stack">{{ entry.throwable }}</pre>
      </YdAction>
    </div>
  </div>
</template>

<style scoped>
.logs-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
}

.logs-toolbar {
  display: flex;
  gap: 6px;
  align-items: center;
}

.logs-toolbar__keyword {
  flex: 1;
  min-width: 0;
}

.logs-status {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--color-text-3);
  font-size: 11px;
}

.logs-status__paused {
  color: var(--color-warning, #ff7d00);
}

.logs-empty {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 40px 16px;
  color: var(--color-text-3);
  font-size: 13px;
  text-align: center;
}

.logs-empty__hint {
  font-size: 11px;
}

.logs-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.log-entry {
  display: block;
  width: 100%;
  padding: 4px 8px;
  text-align: left;
  border-bottom: 1px solid var(--color-border-1, var(--color-border-2));
}

.log-entry--expandable {
  cursor: pointer;
}

.log-entry__line {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.log-entry__time {
  color: var(--color-text-3);
  flex-shrink: 0;
}

.log-level {
  flex-shrink: 0;
  width: 44px;
  font-weight: 600;
  color: var(--color-text-3);
}

.log-level--error {
  color: var(--color-danger-5, #f53f3f);
}

.log-level--warn {
  color: var(--color-warning, #ff7d00);
}

.log-level--debug {
  color: var(--color-text-3);
  font-weight: 400;
}

.log-entry__logger {
  color: var(--color-text-3);
  flex-shrink: 0;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-entry__message {
  color: var(--color-text-2);
  word-break: break-all;
  white-space: pre-wrap;
  flex: 1;
  min-width: 0;
}

.log-entry__stack {
  margin: 4px 0 2px;
  padding: 6px 8px;
  color: var(--color-text-3);
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--color-fill-1, var(--color-bg-3));
  border-radius: 6px;
}
</style>
