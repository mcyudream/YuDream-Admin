<script setup lang="ts">
import type { YdColorFormat, YdRgbaColor } from './color-model'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { computed, ref, watch } from 'vue'
import { formatYdColor, parseYdColor, ydColorToHexInput } from './color-model'

const props = withDefaults(defineProps<{
  format?: YdColorFormat
  showAlpha?: boolean
  disabled?: boolean
  presets?: string[]
  ariaLabel?: string
}>(), { format: 'hex', showAlpha: false, presets: () => [], ariaLabel: '选择颜色' })
const emit = defineEmits<{ change: [value: string, color: YdRgbaColor] }>()
const model = defineModel<string>({ default: '#1677ff' })
const open = ref(false)
const editing = ref(model.value)
const color = computed(() => parseYdColor(model.value) ?? { r: 22, g: 119, b: 255, a: 1 })
const formatted = computed(() => formatYdColor(color.value, props.format, props.showAlpha))
watch(model, value => editing.value = value)
function update(next: YdRgbaColor) {
  const value = formatYdColor(next, props.format, props.showAlpha)
  model.value = value
  editing.value = value
  emit('change', value, next)
}
function updateText() {
  const parsed = parseYdColor(editing.value)
  if (parsed) {
    update(parsed)
  }
}
function pickPreset(value: string) {
  const parsed = parseYdColor(value)
  if (parsed) {
    update(parsed)
  }
}
</script>

<template>
  <div class="yd-color-picker" :class="{ 'is-disabled': disabled }">
    <PopoverRoot v-model:open="open">
      <PopoverTrigger as-child>
        <button type="button" class="yd-color-picker__trigger" :aria-label="ariaLabel" :disabled="disabled">
          <span class="yd-color-picker__swatch" :style="{ backgroundColor: formatted }" />
          <span>{{ formatted }}</span>
        </button>
      </PopoverTrigger>
      <PopoverPortal>
        <PopoverContent class="yd-collection-popup yd-color-picker__panel" align="start" :side-offset="6" @open-auto-focus="$event.preventDefault()">
          <label class="yd-color-picker__native-label">
            <span>颜色</span>
            <input type="color" :value="ydColorToHexInput(color)" :disabled="disabled" @input="update({ ...color, ...(parseYdColor(($event.target as HTMLInputElement).value) || color) })">
          </label>
          <label class="yd-color-picker__format-label">
            <span>{{ format.toUpperCase() }}</span>
            <input v-model="editing" type="text" spellcheck="false" :disabled="disabled" @change="updateText" @keydown.enter="updateText">
          </label>
          <label v-if="showAlpha" class="yd-color-picker__alpha-label">
            <span>透明度</span>
            <input type="range" min="0" max="1" step="0.01" :value="color.a" :disabled="disabled" @input="update({ ...color, a: Number(($event.target as HTMLInputElement).value) })">
          </label>
          <div v-if="presets.length" class="yd-color-picker__presets" aria-label="预设颜色">
            <button v-for="preset in presets" :key="preset" type="button" :aria-label="preset" :title="preset" :style="{ backgroundColor: preset }" @click="pickPreset(preset)" />
          </div>
        </PopoverContent>
      </PopoverPortal>
    </PopoverRoot>
  </div>
</template>

<style scoped>
.yd-color-picker {
  display: inline-flex;
}

.yd-color-picker__trigger {
  display: inline-flex;
  gap: 0.5rem;
  align-items: center;
  min-height: var(--yd-control-height);
  padding: 0.4rem 0.65rem;
  color: oklch(var(--foreground));
  cursor: pointer;
  background: oklch(var(--background));
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius);
}

.yd-color-picker__swatch {
  width: 1.25rem;
  height: 1.25rem;
  background-image: conic-gradient(#ccc 25%, white 0 50%, #ccc 0 75%, white 0);
  background-size: 8px 8px;
  border: 1px solid oklch(var(--border));
  border-radius: 0.25rem;
}

.is-disabled {
  opacity: 0.55;
}
</style>

<style>
/* 面板传送到 body，必须用全局类命中；与 .yd-collection-popup 组成复合选择器以稳定覆盖共享弹层默认值。 */
.yd-collection-popup.yd-color-picker__panel {
  display: grid;
  gap: 0.75rem;
  width: 17rem;
  padding: 1rem;
}

.yd-color-picker__native-label,
.yd-color-picker__format-label,
.yd-color-picker__alpha-label {
  display: grid;
  gap: 0.35rem;
  font-size: 0.75rem;
}

.yd-color-picker__native-label input {
  width: 100%;
  height: 2.25rem;
  padding: 3px;
  cursor: pointer;
  background: transparent;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius);
}

.yd-color-picker__native-label input::-webkit-color-swatch-wrapper {
  padding: 0;
}

.yd-color-picker__native-label input::-webkit-color-swatch {
  border: 0;
  border-radius: calc(var(--yd-control-radius) - 4px);
}

.yd-color-picker__native-label input::-moz-color-swatch {
  border: 0;
  border-radius: calc(var(--yd-control-radius) - 4px);
}

.yd-color-picker__format-label input {
  min-width: 0;
  padding: 0.45rem;
  color: inherit;
  background: transparent;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius);
}

.yd-color-picker__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.yd-color-picker__presets button {
  width: 1.5rem;
  height: 1.5rem;
  cursor: pointer;
  border: 1px solid oklch(var(--border));
  border-radius: 50%;
}
</style>
