<script setup lang="ts">
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
      <div v-for="metric in metrics" :key="metric.label" class="dashboard-metrics__item">
        <dt>{{ metric.label }}</dt>
        <dd :class="{ 'dashboard-metrics__value--compact': metric.compact }">
          <span v-if="loading" class="dashboard-metrics__placeholder" aria-label="加载中" />
          <span v-else class="dashboard-metrics__value" :title="String(metric.value ?? '—')">{{ metric.value ?? '—' }}</span>
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
  padding: 12px 0;
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
  font-size: 12px;
  line-height: 1.3;
  color: var(--color-text-3);
}

.dashboard-metrics__item dd {
  min-width: 0;
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

@media (prefers-reduced-motion: reduce) {
  .dashboard-metrics__value {
    animation: none;
  }
}
</style>
