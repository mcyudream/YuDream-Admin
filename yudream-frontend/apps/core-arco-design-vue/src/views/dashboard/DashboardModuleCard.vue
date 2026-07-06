<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'

export interface DashboardInsight {
  label: string
  value: string
}

interface Props {
  card: DashboardCard
  insights: DashboardInsight[]
  onOpen?: (card?: DashboardCard) => void
}

defineProps<Props>()
</script>

<template>
  <div class="dashboard-card__content dashboard-module">
    <p v-if="card.description" class="dashboard-module__desc">
      {{ card.description }}
    </p>

    <div class="dashboard-module__insights">
      <div v-for="insight in insights" :key="insight.label" class="dashboard-module__insight">
        <span class="dashboard-module__insight-label">{{ insight.label }}</span>
        <span class="dashboard-module__insight-value">{{ insight.value }}</span>
      </div>
    </div>

    <div class="dashboard-module__actions">
      <FaButton v-if="card.actionPath" size="sm" @click="onOpen?.(card)">
        <FaIcon name="i-ri:arrow-right-line" />
        打开
      </FaButton>
    </div>
  </div>
</template>

<style scoped>
.dashboard-module {
  display: grid;
  gap: 12px;
}

.dashboard-module__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.dashboard-module__insights {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
}

.dashboard-module__insight {
  display: grid;
  gap: 4px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border-1);
}

.dashboard-module__insight-label {
  font-size: 11px;
  color: var(--color-text-3);
}

.dashboard-module__insight-value {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--color-text-1);
}

.dashboard-module__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}

@container (max-width: 300px) {
  .dashboard-module__insights {
    grid-template-columns: 1fr;
  }
}
</style>
