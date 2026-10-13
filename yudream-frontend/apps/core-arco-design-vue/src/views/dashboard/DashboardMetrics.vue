<script setup lang="ts">
import { YdCountTo } from '@yudream/components'

interface Metric {
  label: string
  value: string | number | null | undefined
  compact?: boolean
}

defineProps<{
  metrics: Metric[]
  loading: boolean
  message: string
  actionLabel?: string
  showAction?: boolean
}>()

defineEmits<{
  open: []
}>()
</script>

<template>
  <div class="dashboard-card__content dashboard-metrics" :aria-busy="loading">
    <dl class="dashboard-metrics__grid">
      <div v-for="(metric, index) in metrics" :key="metric.label" class="dashboard-metrics__item" :class="{ 'dashboard-metrics__item--compact': metric.compact }">
        <dt>{{ metric.label }}</dt>
        <dd :class="{ 'dashboard-metrics__value--compact': metric.compact }">
          <span v-if="loading" class="dashboard-metrics__placeholder" aria-label="加载中" />
          <span v-else class="dashboard-metrics__value" :title="String(metric.value ?? '—')" :aria-label="String(metric.value ?? '—')">
            <YdCountTo v-if="index === 0 && typeof metric.value === 'number'" :end-val="metric.value" :duration="700" aria-hidden="true" />
            <template v-else>{{ metric.value ?? '—' }}</template>
          </span>
        </dd>
      </div>
    </dl>
    <div class="dashboard-metrics__footer">
      <span :title="message" role="status">{{ message }}</span>
      <FaButton v-if="showAction" size="sm" variant="ghost" @click="$emit('open')">
        {{ actionLabel || '查看' }}
        <FaIcon name="morph:arrow-up-right" />
      </FaButton>
    </div>
  </div>
</template>

<style scoped>
.dashboard-metrics {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dashboard-metrics__grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 0 24px;
  align-content: center;
  margin: 0;
}

.dashboard-metrics__item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-1);
}

.dashboard-metrics__item:first-child {
  flex-direction: column-reverse;
  grid-row: span 3;
  gap: 9px;
  align-items: flex-start;
  justify-content: center;
  padding-right: 16px;
  border-right: 1px solid var(--color-border-1);
  border-bottom: 0;
}

.dashboard-metrics__item:last-child {
  border-bottom: 0;
}

.dashboard-metrics__item:first-child dd {
  font-size: clamp(40px, 14cqi, 64px);
  line-height: 1;
}

.dashboard-metrics__item dt {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1.3;
  color: var(--color-text-3);
}

.dashboard-metrics__item dd {
  min-width: 0;
  max-width: 100%;
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  color: var(--color-text-1);
  letter-spacing: -0.035em;
}

.dashboard-metrics__value {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  animation: dashboard-metric-enter var(--yd-motion-duration-normal, 240ms) var(--yd-motion-ease, ease) both;
}

.dashboard-metrics__item .dashboard-metrics__value--compact {
  display: flex;
  align-items: center;
  min-height: 31px;
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: normal;
}

.dashboard-metrics__item--compact {
  grid-column: 1 / -1;
  align-items: baseline;
  border-top: 1px solid var(--color-border-1);
}

.dashboard-metrics__item--compact .dashboard-metrics__value {
  font-size: 12px;
  font-weight: 500;
  overflow-wrap: anywhere;
  white-space: normal;
}

.dashboard-metrics__grid:has(.dashboard-metrics__item--compact) > :first-child {
  grid-row: span 2;
}

.dashboard-metrics__placeholder {
  display: block;
  width: 58%;
  height: 26px;
  margin: 3px 0 2px;
  background: var(--color-fill-3);
  border-radius: 5px;
}

.dashboard-metrics__footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  margin-top: auto;
}

.dashboard-metrics__footer > span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-3);
  white-space: nowrap;
}

@keyframes dashboard-metric-enter {
  from {
    opacity: 0;
    transform: translateY(calc(var(--yd-motion-enter-y, 14px) * 0.3));
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@container (max-width: 260px) {
  .dashboard-metrics__grid {
    gap: 0 12px;
  }

  .dashboard-metrics__item {
    gap: 5px;
  }

  .dashboard-metrics__item dd {
    font-size: 16px;
  }
}

@container dashboard-card (max-height: 250px) {
  .dashboard-metrics {
    gap: 4px;
  }

  .dashboard-metrics__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0 8px;
  }

  .dashboard-metrics__grid:has(.dashboard-metrics__item--compact) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dashboard-metrics__item,
  .dashboard-metrics__item:first-child {
    flex-direction: column-reverse;
    grid-row: auto;
    gap: 4px;
    align-items: flex-start;
    justify-content: center;
    padding: 0;
    border: 0;
  }

  .dashboard-metrics__grid:has(.dashboard-metrics__item--compact) > :first-child {
    grid-row: auto;
  }

  .dashboard-metrics__item dt {
    font-size: 11px;
  }

  .dashboard-metrics__item dd {
    font-size: 17px;
  }

  .dashboard-metrics__item:first-child dd {
    font-size: 32px;
  }

  .dashboard-metrics__item {
    min-height: 48px;
  }

  .dashboard-metrics__item--compact {
    flex-direction: row;
    gap: 8px;
    align-items: baseline;
    justify-content: space-between;
    min-height: 18px;
  }

  .dashboard-metrics__item--compact .dashboard-metrics__value--compact {
    min-height: 0;
  }

  .dashboard-metrics__footer :deep(button) {
    height: 24px;
    font-size: 11px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-metrics__value {
    animation: none;
  }
}
</style>
