<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import { toneIconClass } from './tone'

export interface DashboardAction {
  code: string
  title: string
  description?: string
  icon?: string
  category?: string
  actionPath?: string
  tone?: string
}

interface Props {
  card: DashboardCard
  actions: DashboardAction[]
  onOpen?: (action?: DashboardAction) => void
}

defineProps<Props>()
</script>

<template>
  <div class="dashboard-card__content dashboard-quick">
    <div v-if="actions.length" class="dashboard-quick__grid">
      <button
        v-for="action in actions"
        :key="action.code"
        type="button"
        class="dashboard-quick__item"
        @click="onOpen?.(action)"
      >
        <span class="dashboard-quick__icon" :class="toneIconClass(action.tone || card.tone)">
          <FaIcon :name="action.icon || 'i-ri:arrow-right-line'" />
        </span>
        <span class="dashboard-quick__main">
          <strong>{{ action.title }}</strong>
          <span>{{ action.description || action.category }}</span>
        </span>
        <FaIcon name="i-ri:arrow-right-s-line" class="dashboard-quick__arrow" />
      </button>
    </div>
    <div v-else class="dashboard-quick__empty">
      当前角色暂无可展示入口
    </div>
  </div>
</template>

<style scoped>
.dashboard-quick {
  display: grid;
  min-height: 0;
}

.dashboard-quick__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.dashboard-quick__item {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 10px;
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.dashboard-quick__item:hover {
  background: var(--color-fill-2);
  border-color: var(--color-border-3);
}

.dashboard-quick__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 16px;
}

.dashboard-quick__main {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 0;
}

.dashboard-quick__main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.dashboard-quick__main span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.dashboard-quick__arrow {
  flex: 0 0 auto;
  font-size: 14px;
  color: var(--color-text-3);
}

.dashboard-quick__empty {
  display: grid;
  place-items: center;
  min-height: 96px;
  font-size: 13px;
  color: var(--color-text-3);
  border: 1px dashed var(--color-border-2);
  border-radius: 8px;
}

@container (max-width: 360px) {
  .dashboard-quick__grid {
    grid-template-columns: 1fr;
  }
}
</style>
