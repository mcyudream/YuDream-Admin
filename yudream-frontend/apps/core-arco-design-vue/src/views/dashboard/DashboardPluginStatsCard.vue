<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiPlugin from '@/api/modules/platform-plugin'
import { toneTextClass } from './tone'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

defineProps<Props>()

const loading = ref(false)
const rows = ref<PluginModule[]>([])
const message = ref('')

const enabledCount = computed(() => rows.value.filter(item => item.enabled).length)
const loadedCount = computed(() => rows.value.filter(item => item.loaded).length)
const errorCount = computed(() => rows.value.filter(item => item.status === 'ERROR').length)
const disabledCount = computed(() => rows.value.filter(item => !item.enabled).length)

onMounted(loadStats)

async function loadStats() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiPlugin.list()
    rows.value = res.data
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
  <div class="dashboard-card__content dashboard-stat">
    <div class="dashboard-stat__grid">
      <div class="dashboard-stat__item">
        <span>插件总数</span>
        <strong :class="toneTextClass(card.tone)">{{ rows.length }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>已启用</span>
        <strong :class="toneTextClass(card.tone)">{{ enabledCount }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>已加载</span>
        <strong>{{ loadedCount }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>异常/停用</span>
        <strong>{{ errorCount }} / {{ disabledCount }}</strong>
      </div>
    </div>

    <div class="dashboard-stat__footer">
      <span v-if="loading">正在刷新插件状态</span>
      <span v-else>{{ message || '统计来自插件运行时列表' }}</span>
      <FaButton v-if="card.actionPath" size="sm" variant="outline" @click="onOpen?.(card)">
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
