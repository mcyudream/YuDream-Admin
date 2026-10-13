<script setup lang="ts">
import type { CapabilityItem } from '@/api/modules/platform-capability'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiCapability from '@/api/modules/platform-capability'
import DashboardMetrics from './DashboardMetrics.vue'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

const props = defineProps<Props>()

const loading = ref(false)
const ready = ref(false)
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
const metrics = computed(() => [
  { label: '可用能力', value: ready.value ? availableCount.value : null },
  { label: '已启用', value: ready.value ? enabledCount.value : null },
  { label: '异常', value: ready.value ? errorCount.value : null },
  { label: '能力类型', value: ready.value ? mainTypes.value || '暂无' : null, compact: true },
])

onMounted(loadStats)

async function loadStats() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiCapability.list()
    rows.value = res.data
    ready.value = true
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
    DISTRIBUTION: '分发',
  }
  return map[type] || type
}
</script>

<template>
  <DashboardMetrics
    :metrics="metrics"
    :loading="loading"
    :message="loading ? '正在刷新平台能力' : (message || '展示当前项目允许加载的能力')"
    :show-action="!!card.actionPath"
    action-label="管理"
    @open="props.onOpen?.(card)"
  />
</template>
