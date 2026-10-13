<script setup lang="ts">
import type { YdLoaderStep } from './loader-runner'
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import FaModal from '../basic/modal/index.vue'
import { runYdLoaderSteps, YdLoaderStepError } from './loader-runner'
import YdSteps from './YdSteps.vue'

export type { YdLoaderContext, YdLoaderStep } from './loader-runner'
const props = defineProps<{
  loading?: boolean
  steps: YdLoaderStep[]
  preventClose?: boolean
  title?: string
}>()
const emit = defineEmits<{
  'update:loading': [
        value: boolean,
  ]
  'stateChange': [
        index: number,
  ]
  'complete': [
        results: Record<string, unknown>,
  ]
  'error': [
        error: unknown,
        index: number,
  ]
  'close': [
  ]
}>()
const index = ref(0)
const status = ref<'process' | 'error' | 'finish'>('process')
const completedCount = computed(() => status.value === 'finish' ? props.steps.length : Math.min(index.value, props.steps.length))
const progress = computed(() => props.steps.length ? Math.round(completedCount.value / props.steps.length * 100) : 100)
const stateText = computed(() => status.value === 'finish' ? '任务已完成' : status.value === 'error' ? '执行失败，可以重试' : '正在执行任务')
const stepItems = computed(() => props.steps.map((step, i) => ({
  id: String(i),
  title: i < index.value || status.value === 'finish' ? step.afterText || step.text : step.text,
  description: i < index.value || status.value === 'finish' ? '已完成' : i === index.value ? status.value === 'error' ? '执行失败' : '正在执行' : '待执行',
})))
let controller: AbortController | undefined
let generation = 0
let mounted = false
async function run() {
  if (!mounted) {
    return
  }
  controller?.abort()
  controller = new AbortController()
  const signal = controller.signal
  const token = ++generation
  index.value = 0
  status.value = 'process'
  try {
    const { cancelled, results } = await runYdLoaderSteps(props.steps, signal, (i) => {
      index.value = i
      emit('stateChange', i)
    })
    if (!cancelled && token === generation) {
      status.value = 'finish'
      emit('complete', results)
    }
  }
  catch (error) {
    if (!signal.aborted && token === generation) {
      status.value = 'error'
      emit('error', error instanceof YdLoaderStepError ? error.reason : error, error instanceof YdLoaderStepError ? error.index : index.value)
    }
  }
}
function cancel() {
  generation++
  controller?.abort()
}
function close() {
  if (props.preventClose && status.value === 'process') {
    return
  }
  cancel()
  emit('update:loading', false)
  emit('close')
}
watch(() => props.loading, (next) => {
  if (next && mounted) {
    void run()
  }
  else {
    cancel()
  }
})
onMounted(() => {
  mounted = true
  if (props.loading) {
    void run()
  }
})
onScopeDispose(() => {
  mounted = false
  cancel()
})
defineExpose({ retry: run, cancel })
</script>

<template>
  <FaModal :model-value="!!loading" :title="title || '任务进度'" :closable="!preventClose || status !== 'process'" :close-on-click-overlay="false" :close-on-press-escape="!preventClose || status !== 'process'" :footer="false" @update:model-value="!$event && close()">
    <div class="yd-loader-task" :data-status="status">
      <div class="yd-loader-summary" role="status" aria-live="polite">
        <FaIcon :name="status === 'finish' ? 'morph:circle-check' : status === 'error' ? 'morph:circle-alert' : 'morph:loader-circle'" class="yd-loader-state-icon" :class="{ 'is-processing': status === 'process' }" aria-hidden="true" />
        <strong>{{ stateText }}</strong>
        <span class="yd-loader-count">{{ completedCount }} / {{ steps.length }}</span>
      </div>
      <div class="yd-loader-progress" role="progressbar" aria-label="任务完成进度" :aria-valuenow="progress" :aria-valuemin="0" :aria-valuemax="100" :aria-valuetext="`${completedCount} 个步骤已完成，共 ${steps.length} 个步骤`">
        <div class="yd-loader-progress-fill" :style="{ width: `${progress}%` }" />
      </div>
      <YdSteps class="yd-loader-steps" :model-value="index" :status="status" :steps="stepItems" vertical />
      <div v-if="status !== 'process'" class="yd-loader-actions">
        <FaButton v-if="status === 'error'" variant="outline" class="yd-loader-action" @click="run">
          <FaIcon name="morph:rotate-cw" aria-hidden="true" />重新执行
        </FaButton>
        <FaButton v-else variant="outline" class="yd-loader-action" @click="close">
          <FaIcon name="morph:check" aria-hidden="true" />完成
        </FaButton>
      </div>
    </div>
  </FaModal>
</template>

<style scoped>
.yd-loader-task {
  display: grid;
  gap: var(--yd-content-gap);
  color: oklch(var(--foreground));
}

.yd-loader-summary {
  display: flex;
  gap: 0.6rem;
  align-items: center;
  font-size: var(--yd-control-font-size);
}

.yd-loader-state-icon {
  flex: none;
  width: 1.2rem;
  height: 1.2rem;
  color: var(--yd-emphasis, oklch(var(--foreground)));
}

.yd-loader-state-icon.is-processing {
  animation: yd-loader-state-spin calc(1.2s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.yd-loader-count {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: oklch(var(--muted-foreground));
}

.yd-loader-progress {
  height: 6px;
  overflow: hidden;
  background: oklch(var(--muted));
  border: 1px solid oklch(var(--border));
  border-radius: 999px;
}

.yd-loader-progress-fill {
  height: 100%;
  background: var(--yd-emphasis, oklch(var(--foreground)));
  border-radius: inherit;
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.yd-loader-steps {
  margin: 0;
}

.yd-loader-steps :deep(.yd-step-heading strong) {
  color: oklch(var(--foreground));
}

.yd-loader-steps :deep(.yd-step-number) {
  color: oklch(var(--foreground));
  background: oklch(var(--muted));
  border: 1px solid oklch(var(--border));
}

.yd-loader-steps :deep([data-active="true"] .yd-step-number) {
  color: oklch(var(--foreground));
  background: color-mix(in oklab, var(--yd-emphasis, oklch(var(--foreground))) 16%, oklch(var(--muted)));
  border-color: color-mix(in oklab, var(--yd-emphasis, oklch(var(--foreground))) 55%, oklch(var(--border)));
}

.yd-loader-actions {
  display: flex;
  justify-content: flex-end;
}

.yd-loader-action {
  color: oklch(var(--foreground));
  background: oklch(var(--muted));
  border-color: color-mix(in oklab, oklch(var(--foreground)) 35%, oklch(var(--border)));
}

@keyframes yd-loader-state-spin {
  to {
    rotate: 360deg;
  }
}
</style>
