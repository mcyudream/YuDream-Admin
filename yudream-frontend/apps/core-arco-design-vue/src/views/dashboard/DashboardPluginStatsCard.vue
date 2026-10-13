<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiPlugin from '@/api/modules/platform-plugin'
import DashboardMetrics from './DashboardMetrics.vue'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

defineProps<Props>()

const loading = ref(false)
const ready = ref(false)
const rows = ref<PluginModule[]>([])
const message = ref('')

const enabledCount = computed(() => rows.value.filter(item => item.enabled).length)
const loadedCount = computed(() => rows.value.filter(item => item.loaded).length)
const errorCount = computed(() => rows.value.filter(item => item.status === 'ERROR').length)
const disabledCount = computed(() => rows.value.filter(item => !item.enabled).length)
const metrics = computed(() => [
  { label: '插件总数', value: ready.value ? rows.value.length : null },
  { label: '已启用', value: ready.value ? enabledCount.value : null },
  { label: '已加载', value: ready.value ? loadedCount.value : null },
  { label: '异常 / 停用', value: ready.value ? `${errorCount.value} / ${disabledCount.value}` : null },
])

onMounted(loadStats)

async function loadStats() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiPlugin.list()
    rows.value = res.data
    ready.value = true
  }
  catch (error) {
    message.value = error instanceof Error ? error.message : '插件数据暂不可用'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <DashboardMetrics
    :metrics="metrics"
    :loading="loading"
    :message="loading ? '正在刷新插件状态' : (message || '统计来自插件运行时列表')"
    :show-action="!!card.actionPath"
    action-label="管理"
    @open="onOpen?.(card)"
  />
</template>
