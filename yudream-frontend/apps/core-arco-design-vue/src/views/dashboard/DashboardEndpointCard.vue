<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import { toneTextClass } from './tone'

interface Props {
  card: DashboardCard
  endpointUrl: string
  dragPayload: string
  onCopy: () => void
}

const props = defineProps<Props>()

function handleDragStart(event: DragEvent) {
  if (!event.dataTransfer) {
    return
  }
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.dropEffect = 'copy'
  event.dataTransfer.setData('text/plain', props.dragPayload)
}
</script>

<template>
  <div class="dashboard-card__content dashboard-endpoint">
    <p v-if="card.description" class="dashboard-endpoint__desc">
      {{ card.description }}
    </p>

    <button
      type="button"
      class="dashboard-endpoint__address"
      title="复制 API 地址，也可拖拽到启动器"
      draggable="true"
      @click="onCopy"
      @dragstart="handleDragStart"
    >
      <code>{{ endpointUrl }}</code>
      <span class="dashboard-endpoint__copy" :class="toneTextClass(card.tone)">
        <FaIcon name="i-ri:file-copy-line" />
      </span>
    </button>

    <div class="dashboard-endpoint__hint">
      <FaIcon name="i-ri:information-line" />
      <span>点击可复制地址，也可以把地址卡片拖拽到支持拖入配置的客户端。</span>
    </div>
  </div>
</template>

<style scoped>
.dashboard-endpoint {
  display: grid;
  gap: 12px;
}

.dashboard-endpoint__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.dashboard-endpoint__address {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 10px 12px;
  color: var(--color-text-1);
  text-align: left;
  cursor: copy;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.dashboard-endpoint__address:hover {
  background: var(--color-fill-2);
  border-color: var(--color-border-3);
}

.dashboard-endpoint__address code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace);
  font-size: 12px;
  white-space: nowrap;
}

.dashboard-endpoint__copy {
  flex: 0 0 auto;
  font-size: 16px;
}

.dashboard-endpoint__hint {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.dashboard-endpoint__hint :deep(.fa-icon) {
  flex: 0 0 auto;
  margin-top: 1px;
  font-size: 13px;
}
</style>
