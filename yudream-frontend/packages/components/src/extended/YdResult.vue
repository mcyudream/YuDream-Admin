<script setup lang="ts">
import type { YdStateIllustrationName } from './YdStateIllustration.vue'
import { computed } from 'vue'
import YdIcon from '../basic/icon/index.vue'
import YdEmpty from './YdEmpty.vue'

type YdResultStatus = 'success' | 'info' | 'warning' | 'error' | '403' | '404' | '500'

const props = withDefaults(defineProps<{
  status?: YdResultStatus
  title?: string
  description?: string
  illustration?: YdStateIllustrationName | 'auto' | false
  illustrationSize?: 'sm' | 'md' | 'lg'
}>(), { status: 'info', illustration: 'auto', illustrationSize: 'lg' })

const statusIllustration: Record<YdResultStatus, YdStateIllustrationName | false> = {
  success: 'success',
  info: false,
  warning: 'warning',
  error: 'error',
  403: 'forbidden',
  404: 'no-result',
  500: 'error',
}
const iconName = computed(() => ({ success: 'morph:circle-check', info: 'morph:info', warning: 'morph:triangle-alert', error: 'morph:circle-alert', 403: 'morph:shield-alert', 404: 'morph:file-question', 500: 'morph:server-crash' })[props.status])
const illustrationName = computed(() => props.illustration === 'auto' ? statusIllustration[props.status] : props.illustration)
</script>

<template>
  <div class="yd-result" :data-status="status">
    <YdEmpty :icon="iconName" :title="title || ({ success: '操作成功', info: '处理完成', warning: '需要注意', error: '操作失败', 403: '无权访问', 404: '页面不存在', 500: '服务异常' }[status])" :description="description" :illustration="illustrationName" :illustration-size="illustrationSize">
      <template v-if="$slots.illustration" #illustration>
        <slot name="illustration" />
      </template>
      <template #icon>
        <slot name="icon">
          <YdIcon :name="iconName" />
        </slot>
      </template>
      <template #action>
        <slot name="action">
          <slot />
        </slot>
      </template>
    </YdEmpty>
  </div>
</template>

<style scoped>
.yd-result :deep(.yd-empty-icon) {
  color: var(--yd-status-info);
  background: color-mix(in srgb, var(--yd-status-info) 10%, transparent);
  border-color: color-mix(in srgb, var(--yd-status-info) 35%, transparent);
}

.yd-result[data-status="success"] :deep(.yd-empty-icon) {
  color: var(--yd-status-success);
  background: color-mix(in srgb, var(--yd-status-success) 10%, transparent);
  border-color: color-mix(in srgb, var(--yd-status-success) 35%, transparent);
}

.yd-result[data-status="warning"] :deep(.yd-empty-icon) {
  color: var(--yd-status-warning);
  background: color-mix(in srgb, var(--yd-status-warning) 10%, transparent);
  border-color: color-mix(in srgb, var(--yd-status-warning) 35%, transparent);
}

.yd-result[data-status="error"] :deep(.yd-empty-icon),
.yd-result[data-status="403"] :deep(.yd-empty-icon),
.yd-result[data-status="404"] :deep(.yd-empty-icon),
.yd-result[data-status="500"] :deep(.yd-empty-icon) {
  color: var(--yd-status-danger);
  background: color-mix(in srgb, var(--yd-status-danger) 10%, transparent);
  border-color: color-mix(in srgb, var(--yd-status-danger) 35%, transparent);
}

.yd-result__icon {
  display: block;
  width: 1.5rem;
  height: 1.5rem;
  background: currentcolor;
  mask: var(--yd-result-mask, none);
}
</style>
