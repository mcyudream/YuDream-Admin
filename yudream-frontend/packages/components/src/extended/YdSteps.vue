<script setup lang="ts">
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

export interface YdStepItem {
  id: string
  title: string
  description?: string
  disabled?: boolean
}
withDefaults(defineProps<{
  steps: YdStepItem[]
  status?: 'error' | 'process' | 'wait' | 'finish'
  vertical?: boolean
  clickable?: boolean
}>(), { status: 'process' })
const emit = defineEmits<{
  change: [
        index: number,
  ]
}>()
const model = defineModel<number>({ default: 0 })
function select(index: number) {
  model.value = index
  emit('change', index)
}
</script>

<template>
  <ol class="yd-steps" :class="{ vertical }">
    <li v-for="(step, index) in steps" :key="step.id" :data-active="index === model" :aria-current="index === model ? 'step' : undefined">
      <component :is="clickable ? 'button' : 'div'" :type="clickable ? 'button' : undefined" :disabled="step.disabled" class="yd-step-heading yd-focus" @click="clickable && !step.disabled && select(index)">
        <span class="yd-step-number"><FaIcon v-if="index < model || (index === model && status === 'finish')" name="morph:check" /><FaIcon v-else-if="index === model && status === 'error'" name="morph:x" /><span v-else>{{ index + 1 }}</span></span>
        <span><strong>{{ step.title }}</strong><small v-if="step.description" class="yd-muted">{{ step.description }}</small></span>
      </component>
      <slot :name="step.id" :step="step" :index="index" :active="index === model" :finished="index < model || (index === model && status === 'finish')" :status="index < model ? 'finish' : index === model ? status : 'wait'" />
    </li>
  </ol>
</template>

<style scoped>
.yd-steps {
  display: flex;
  gap: var(--yd-content-gap);
  padding: 0;
  list-style: none;
}

.yd-steps.vertical {
  flex-direction: column;
}

li {
  flex: 1;
  min-width: 0;
}

.yd-step-heading {
  display: flex;
  gap: 12px;
  align-items: center;
  text-align: start;
}

.yd-step-number {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: var(--yd-control-height);
  height: var(--yd-control-height);
  background: oklch(var(--muted));
  border-radius: var(--yd-control-radius);
}

[data-active="true"] .yd-step-number {
  color: oklch(var(--primary-foreground));
  background: oklch(var(--primary));
}

small {
  display: block;
  margin-top: 4px;
}

strong {
  font-size: var(--yd-control-font-size);
}

@media (width <= 768px) {
  .yd-steps {
    flex-direction: column;
  }
}
</style>
