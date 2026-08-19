<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'

const props = defineProps<{
  card: DashboardCard
  onOpen: (card: DashboardCard) => void
}>()
</script>

<template>
  <div class="dashboard-card__content dashboard-guide">
    <p>{{ card.description || '完成必要配置后，即可开始使用此功能。' }}</p>
    <FaButton v-if="card.actionPath" size="sm" @click="onOpen(card)">
      <FaIcon name="i-ri:arrow-right-line" />
      开始配置
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
  color: var(--color-text-2);
  line-height: 1.6;
}

.dashboard-guide :deep(.fa-button) {
  align-self: flex-start;
}
</style>
