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
      <FaButton
        v-for="action in actions"
        :key="action.code"
        type="button"
        variant="ghost"
        class="dashboard-quick__item"
        @click="onOpen?.(action)"
      >
        <span class="dashboard-quick__icon" :class="toneIconClass(action.tone || card.tone)">
          <FaIcon :name="action.icon || 'morph:arrow-right'" />
        </span>
        <span class="dashboard-quick__main">
          <strong>{{ action.title }}</strong>
          <span>{{ action.description || action.category }}</span>
        </span>
        <FaIcon name="morph:chevron-right" class="dashboard-quick__arrow" />
      </FaButton>
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
  grid-template-columns: repeat(auto-fit, minmax(min(160px, 100%), 1fr));
  gap: 10px;
  align-content: start;
}

.dashboard-quick__item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  height: auto;
  min-height: 62px;
  padding: 12px;
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  background: var(--color-fill-1);
  border: 1px solid transparent;
  border-radius: calc(var(--yd-surface-radius, 18px) * 0.66);
  transition: border-color var(--yd-motion-duration-fast, 140ms) ease, background var(--yd-motion-duration-fast, 140ms) ease, transform var(--yd-motion-duration-normal, 240ms) var(--yd-motion-ease, ease);
}

.dashboard-quick__item:hover,
.dashboard-quick__item:focus-visible {
  background: var(--color-fill-2);
  border-color: var(--color-border-2);
}

@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .dashboard-quick__item:hover {
    transform: translateY(var(--yd-motion-lift, -3px));
  }

  .dashboard-quick__item:hover .dashboard-quick__icon {
    transform: rotate(-7deg) scale(1.08);
  }

  .dashboard-quick__item:hover .dashboard-quick__arrow {
    opacity: 1;
    transform: translateX(2px);
  }
}

.dashboard-quick__item:active {
  transform: scale(var(--yd-motion-press, 0.975));
}

.dashboard-quick__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-1);
  border-radius: 10px;
  transition: transform var(--yd-motion-duration-normal, 240ms) var(--yd-motion-ease, ease);
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
  opacity: 0.55;
  transition: opacity var(--yd-motion-duration-fast, 140ms) ease, transform var(--yd-motion-duration-normal, 240ms) var(--yd-motion-ease, ease);
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

:root[data-admin-motion="reduced"] .dashboard-quick__item:hover .dashboard-quick__icon,
:root[data-admin-motion="reduced"] .dashboard-quick__item:hover .dashboard-quick__arrow {
  transform: none;
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-quick__item,
  .dashboard-quick__icon,
  .dashboard-quick__arrow {
    transition: none;
  }
}
</style>
