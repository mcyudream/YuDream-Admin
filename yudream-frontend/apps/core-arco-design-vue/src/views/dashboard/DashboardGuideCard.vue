<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'

defineProps<{
  card: DashboardCard
  onOpen: (card: DashboardCard) => void
}>()
</script>

<template>
  <div class="dashboard-card__content dashboard-guide">
    <p>{{ card.description || '完成必要配置后，即可开始使用此功能。' }}</p>
    <FaButton v-if="card.actionPath" variant="ghost" size="sm" @click="onOpen(card)">
      开始配置
      <FaIcon name="morph:arrow-up-right" />
    </FaButton>
  </div>
</template>

<style scoped>
.dashboard-guide {
  display: flex;
  flex-direction: column;
  gap: 16px;
  justify-content: space-between;
}

.dashboard-guide p {
  margin: 0;
  line-height: 1.6;
  color: var(--color-text-2);
}

.dashboard-guide :deep(.fa-button) {
  align-self: flex-start;
}
</style>
