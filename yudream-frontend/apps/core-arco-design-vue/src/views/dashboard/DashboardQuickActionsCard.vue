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
      <YdButton
        v-for="action in actions"
        :key="action.code"
        type="button"
        variant="ghost"
        class="dashboard-quick__item"
        :title="action.description || action.category"
        @click="onOpen?.(action)"
      >
        <span class="dashboard-quick__icon" :class="toneIconClass(action.tone || card.tone)">
          <YdIcon :name="action.icon || 'morph:arrow-right'" />
        </span>
        <span class="dashboard-quick__label">{{ action.title }}</span>
      </YdButton>
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
  grid-template-rows: repeat(2, minmax(0, 1fr));
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 4px;
  align-content: stretch;
}

.dashboard-quick__item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: auto;
  min-height: 0;
  padding: 8px 4px;
  color: var(--color-text-1);
  text-align: center;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: calc(var(--yd-surface-radius, 18px) * 0.66);
  box-shadow: none;
  transition: background var(--yd-motion-duration-fast, 140ms) ease;
}

.dashboard-quick__item:hover,
.dashboard-quick__item:focus-visible {
  background: var(--color-fill-1);
}

@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .dashboard-quick__item:hover .dashboard-quick__icon {
    transform: translateY(-3px);
  }
}

.dashboard-quick__item:active {
  transform: scale(var(--yd-motion-press, 0.975));
}

.dashboard-quick__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 44px;
  height: 44px;
  font-size: 22px;
  background: var(--color-fill-2);
  border: 0;
  border-radius: 14px;
  transition: transform var(--yd-motion-duration-normal, 240ms) var(--yd-motion-ease, ease);
}

.dashboard-quick__label {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
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

@container (max-width: 400px) {
  .dashboard-quick__icon {
    width: 40px;
    height: 40px;
    font-size: 21px;
    border-radius: 12px;
  }

  .dashboard-quick__label {
    font-size: 12px;
  }
}

@container (max-width: 260px) {
  .dashboard-quick__icon {
    width: 30px;
    height: 30px;
    font-size: 18px;
  }

  .dashboard-quick__item {
    gap: 7px;
    padding-inline: 0;
  }

  .dashboard-quick__label {
    font-size: 11px;
    white-space: normal;
  }
}

@container (max-width: 220px) {
  .dashboard-quick__grid {
    grid-template-rows: repeat(4, minmax(0, 1fr));
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-quick__item {
    flex-direction: row;
    gap: 5px;
  }

  .dashboard-quick__icon {
    width: 22px;
    height: 22px;
    font-size: 15px;
  }
}

@container dashboard-card (max-height: 250px) {
  .dashboard-quick__grid {
    gap: 4px;
  }

  .dashboard-quick__item {
    gap: 4px;
    padding: 2px;
  }

  .dashboard-quick__icon {
    width: 24px;
    height: 24px;
    font-size: 16px;
    border-radius: 7px;
  }

  .dashboard-quick__label {
    font-size: 11px;
  }
}

:root[data-admin-motion="reduced"] .dashboard-quick__item,
:root[data-admin-motion="reduced"] .dashboard-quick__item:hover .dashboard-quick__icon {
  transform: none;
  transition: none;
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-quick__item,
  .dashboard-quick__icon {
    transform: none;
    transition: none;
  }
}
</style>
