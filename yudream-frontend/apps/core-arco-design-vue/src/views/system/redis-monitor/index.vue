<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { RedisKeySample, RedisMonitor } from '@/api/modules/system-monitor'
import apiMonitor from '@/api/modules/system-monitor'

const toast = useFaToast()

const loading = ref(false)
const pattern = ref('*')
const data = ref<RedisMonitor>({
  connected: false,
  keys: [],
  keyspace: {},
})

const stats = computed(() => [
  { label: '\u8fde\u63a5\u72b6\u6001', value: data.value.connected ? '\u6b63\u5e38' : '\u5f02\u5e38', icon: 'i-ri:pulse-line', tone: data.value.connected ? 'ok' : 'bad' },
  { label: 'Redis \u7248\u672c', value: data.value.version || '-', icon: 'i-ri:git-branch-line' },
  { label: 'Key \u603b\u6570', value: data.value.dbSize ?? 0, icon: 'i-ri:key-2-line' },
  { label: '\u5185\u5b58\u4f7f\u7528', value: data.value.usedMemory || '-', icon: 'i-ri:database-2-line' },
  { label: '\u5ba2\u6237\u7aef', value: data.value.connectedClients ?? 0, icon: 'i-ri:computer-line' },
  { label: '\u6bcf\u79d2\u547d\u4ee4', value: data.value.opsPerSecond ?? 0, icon: 'i-ri:speed-up-line' },
  { label: '\u547d\u4ee4\u603b\u6570', value: data.value.totalCommands ?? 0, icon: 'i-ri:terminal-box-line' },
  { label: '\u547d\u4e2d\u7387', value: `${data.value.hitRate ?? 0}%`, icon: 'i-ri:crosshair-2-line' },
])

const keyspaceRows = computed(() => Object.entries(data.value.keyspace || {}).map(([db, value]) => ({ db, value })))

const keyColumns = computed<TableColumn<RedisKeySample>[]>(() => [
  { accessorKey: 'key', header: 'Key', width: 420 },
  { accessorKey: 'type', header: '\u7c7b\u578b', width: 120 },
  { id: 'ttl', header: '\u8fc7\u671f\u65f6\u95f4', width: 140, align: 'right' },
])

const keyspaceColumns = computed<TableColumn<{ db: string; value: string }>[]>(() => [
  { accessorKey: 'db', header: '\u6570\u636e\u5e93', width: 120 },
  { accessorKey: 'value', header: '\u7edf\u8ba1', width: 320 },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiMonitor.redis({
      pattern: pattern.value || '*',
      limit: 80,
    })
    data.value = res.data
    if (!res.data.connected) {
      toast.warning('Redis', { description: res.data.message || '\u8fde\u63a5\u5931\u8d25' })
    }
  }
  finally {
    loading.value = false
  }
}

function ttlText(ttl?: number) {
  if (ttl == null) {
    return '-'
  }
  if (ttl === -1) {
    return '\u6c38\u4e45'
  }
  if (ttl === -2) {
    return '\u5df2\u8fc7\u671f'
  }
  if (ttl < 60) {
    return `${ttl}s`
  }
  return `${Math.floor(ttl / 60)}m ${ttl % 60}s`
}
</script>

<template>
  <div>
    <FaPageHeader title="Redis &#30417;&#25511;" class="mb-0" />

    <FaPageMain>
      <FaSearchBar>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,420px)_auto] md:items-center">
          <FaInput v-model="pattern" clearable placeholder="Key &#21305;&#37197;&#65292;&#20363;&#22914; yudream:*" @keydown.enter="load" @clear="load" />
          <div class="flex gap-2 md:justify-end">
            <FaButton :loading="loading" @click="load">
              <FaIcon name="i-ri:refresh-line" />
              &#21047;&#26032;
            </FaButton>
          </div>
        </div>
      </FaSearchBar>

      <div class="monitor-summary">
        <div v-for="item in stats" :key="item.label" class="summary-item">
          <div class="summary-icon" :class="{ ok: item.tone === 'ok', bad: item.tone === 'bad' }">
            <FaIcon :name="item.icon" />
          </div>
          <div class="summary-body">
            <span>{{ item.label }}</span>
            <strong :class="{ ok: item.tone === 'ok', bad: item.tone === 'bad' }">{{ item.value }}</strong>
          </div>
        </div>
      </div>

      <div class="monitor-grid">
        <section class="monitor-panel">
          <div class="panel-title">
            <span>Keyspace</span>
            <FaTag variant="secondary">{{ keyspaceRows.length }}</FaTag>
          </div>
          <FaTable
            row-key="db"
            table-root-class="rounded-md overflow-hidden"
            table-class="min-w-[420px]"
            border
            stripe
            :columns="keyspaceColumns"
            :data="keyspaceRows"
          />
        </section>

        <section class="monitor-panel">
          <div class="panel-title">
            <span>&#32531;&#23384; Key &#25277;&#26679;</span>
            <FaTag variant="secondary">{{ data.keys.length }}</FaTag>
          </div>
          <FaTable
            row-key="key"
            table-root-class="rounded-md overflow-hidden"
            table-class="min-w-[720px]"
            border
            stripe
            column-visibility
            :columns="keyColumns"
            :data="data.keys"
          >
            <template #cell-key="{ row }">
              <code>{{ row.original.key }}</code>
            </template>
            <template #cell-ttl="{ row }">
              {{ ttlText(row.original.ttl) }}
            </template>
          </FaTable>
        </section>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.monitor-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
  margin: 14px 0;
}

.summary-item {
  display: flex;
  min-width: 0;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.summary-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: none;
  place-items: center;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 18px;
}

.summary-body {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.summary-body span {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-body strong {
  overflow: hidden;
  font-size: 18px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ok {
  color: rgb(var(--success-6));
}

.bad {
  color: rgb(var(--danger-6));
}

.monitor-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.42fr) minmax(0, 1fr);
  gap: 14px;
}

.monitor-panel {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 600;
}

code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

@media (max-width: 1180px) {
  .monitor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
