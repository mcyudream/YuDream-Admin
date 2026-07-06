<script setup lang="ts">
import type { CapabilityItem } from '@/api/modules/platform-capability'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiCapability from '@/api/modules/platform-capability'
import { toneTextClass } from './tone'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

const props = defineProps<Props>()

const loading = ref(false)
const rows = ref<CapabilityItem[]>([])
const message = ref('')

const enabledCount = computed(() => rows.value.filter(item => item.enabled).length)
const errorCount = computed(() => rows.value.filter(item => item.status === 'ERROR').length)
const availableCount = computed(() => rows.value.length)
const mainTypes = computed(() => {
  const groups = new Map<string, number>()
  for (const row of rows.value) {
    groups.set(row.type, (groups.get(row.type) || 0) + 1)
  }
  return Array.from(groups.entries())
    .sort((left, right) => right[1] - left[1])
    .slice(0, 3)
    .map(([type, count]) => `${typeText(type as CapabilityItem['type'])} ${count}`)
    .join(' / ')
})

onMounted(loadStats)

async function loadStats() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiCapability.list()
    rows.value = res.data
  }
  catch (error) {
    message.value = error instanceof Error ? error.message : '能力数据暂不可用'
  }
  finally {
    loading.value = false
  }
}

function typeText(type: CapabilityItem['type']) {
  const map: Record<CapabilityItem['type'], string> = {
    REALTIME: '实时',
    MESSAGING: '消息',
    DOCUMENTATION: '文档',
    INTEGRATION: '集成',
    DOCUMENT: '文档',
    CONTENT: '内容',
    GRAPH: '图谱',
    AI: 'AI',
  }
  return map[type] || type
}
</script>

<template>
  <div class="dashboard-card__content dashboard-stat">
    <div class="dashboard-stat__grid">
      <div class="dashboard-stat__item">
        <span>可用能力</span>
        <strong :class="toneTextClass(card.tone)">{{ availableCount }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>已启用</span>
        <strong :class="toneTextClass(card.tone)">{{ enabledCount }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>异常</span>
        <strong>{{ errorCount }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>能力类型</span>
        <strong>{{ mainTypes || '-' }}</strong>
      </div>
    </div>

    <div class="dashboard-stat__footer">
      <span v-if="loading">正在刷新平台能力</span>
      <span v-else>{{ message || '展示当前项目允许加载的能力' }}</span>
      <FaButton v-if="card.actionPath" size="sm" variant="outline" @click="props.onOpen?.(card)">
        <FaIcon name="i-ri:arrow-right-line" />
        管理
      </FaButton>
    </div>
  </div>
</template>

<style scoped>
.dashboard-stat {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-stat__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.dashboard-stat__item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding-top: 8px;
  border-top: 1px solid var(--color-border-1);
}

.dashboard-stat__item span,
.dashboard-stat__footer span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.dashboard-stat__item strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-text-1);
  white-space: nowrap;
}

.dashboard-stat__footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  margin-top: auto;
}

.dashboard-stat__footer span {
  flex: 1;
}

@container (max-width: 320px) {
  .dashboard-stat__grid {
    grid-template-columns: 1fr;
  }
}
</style>
