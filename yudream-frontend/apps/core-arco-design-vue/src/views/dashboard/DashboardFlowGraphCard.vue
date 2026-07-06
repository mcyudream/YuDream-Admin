<script setup lang="ts">
import { onMounted, ref } from 'vue'
import apiDataviz from '@/api/modules/platform-dataviz'
import type { ChartDataset } from '@/api/modules/platform-dataviz'
import ChartWidget from '@/components/chart/ChartWidget.vue'

export interface DashboardCard {
  cardCode: string
  title?: string
  description?: string
}

const props = defineProps<{
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const dataset = ref<ChartDataset | null>(null)

async function loadDataset() {
  loading.value = true
  error.value = null
  try {
    const res = await apiDataviz.queryDataset({ chartType: 'graph', datasetQuery: { source: 'demo', metric: 'graph' } })
    dataset.value = res.data
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}

function handleOpen() {
  props.onOpen?.(props.card)
}

onMounted(() => {
  loadDataset()
})
</script>

<template>
  <div class="dashboard-flow-graph-card" @click="handleOpen">
    <div class="card-header">
      <strong>{{ card.title ?? '关系图谱演示' }}</strong>
      <span v-if="card.description" class="description">{{ card.description }}</span>
    </div>
    <div class="card-body">
      <div v-if="loading" class="state">
        <a-spin />
      </div>
      <div v-else-if="error" class="state error">
        {{ error }}
      </div>
      <ChartWidget v-else-if="dataset" :dataset="dataset" height="320" />
    </div>
  </div>
</template>

<style scoped>
.dashboard-flow-graph-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.dashboard-flow-graph-card:hover {
  border-color: var(--accent);
  transform: translateY(-1px);
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-header strong {
  color: var(--color-text-1);
  font-size: 15px;
}

.description {
  color: var(--color-text-3);
  font-size: 12px;
}

.card-body {
  position: relative;
  min-height: 320px;
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  color: var(--color-text-3);
}

.state.error {
  color: rgb(var(--danger-6));
}
</style>
