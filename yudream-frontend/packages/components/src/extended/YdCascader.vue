<script setup lang="ts">
import { Cascader as ACascader } from '@arco-design/web-vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { computed, nextTick, ref, watch } from 'vue'
import { YdButton, YdIcon } from '../adapters/index'
import './extended.css'

export interface YdCascaderOption {
  value: string | number
  label: string
  disabled?: boolean
  children?: YdCascaderOption[]
}
const props = withDefaults(defineProps<{
  options: YdCascaderOption[]
  disabled?: boolean
  clearable?: boolean
  leafOnly?: boolean
  placeholder?: string
  separator?: string
  panelMode?: 'multi' | 'single'
  triggerMode?: 'click' | 'hover'
}>(), { leafOnly: true, placeholder: '请选择', separator: ' / ', panelMode: 'multi', triggerMode: 'click' })
const emit = defineEmits<{
  change: [value: string | number | undefined]
  clear: []
}>()
const value = defineModel<string | number>()
const open = ref(false)
const trail = ref<YdCascaderOption[]>([])
const menuRef = ref<HTMLElement>()
const currentOptions = computed(() => trail.value.at(-1)?.children || props.options)
function findPath(options: YdCascaderOption[], key: string | number | undefined): YdCascaderOption[] {
  for (const option of options) {
    if (option.value === key) {
      return [option]
    }
    const nested = findPath(option.children || [], key)
    if (nested.length) {
      return [option, ...nested]
    }
  }
  return []
}
const label = computed(() => findPath(props.options, value.value).map(node => node.label).join(props.separator))
const cascaderOptions = computed(() => {
  function convert(options: YdCascaderOption[], parentDisabled = false): YdCascaderOption[] {
    return options.map(option => ({ ...option, disabled: parentDisabled || option.disabled, children: option.children ? convert(option.children, parentDisabled || !!option.disabled) : undefined }))
  }
  return convert(props.options)
})
watch(open, (next) => {
  if (next) {
    trail.value = findPath(props.options, value.value).slice(0, -1)
  }
})
watch(() => props.disabled, (disabled) => {
  if (disabled) {
    open.value = false
  }
})
async function focusChoice() {
  await nextTick()
  menuRef.value?.querySelector<HTMLButtonElement>('.yd-cascader-choice:not(:disabled)')?.focus()
}
function update(next: unknown) {
  if (props.disabled) {
    return
  }
  if (next === undefined || next === null) {
    clear()
    return
  }
  if (typeof next !== 'string' && typeof next !== 'number') {
    return
  }
  const path = findPath(props.options, next)
  if (!path.length || path.some(option => option.disabled) || (props.leafOnly && path.at(-1)?.children?.length)) {
    return
  }
  value.value = next
  emit('change', next)
}
function select(option: YdCascaderOption) {
  if (option.disabled || props.disabled) {
    return
  }
  if (!option.children?.length || !props.leafOnly) {
    update(option.value)
  }
  if (option.children?.length) {
    trail.value = [...trail.value, option]
    void focusChoice()
  }
  else {
    open.value = false
  }
}
function back() {
  trail.value = trail.value.slice(0, -1)
  void focusChoice()
}
function clear() {
  if (props.disabled) {
    return
  }
  value.value = undefined
  emit('change', undefined)
  emit('clear')
}
function onKeydown(event: KeyboardEvent) {
  const buttons = [...menuRef.value?.querySelectorAll<HTMLButtonElement>('.yd-cascader-choice:not(:disabled)') || []]
  const index = buttons.indexOf(event.target as HTMLButtonElement)
  if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    event.preventDefault()
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
    buttons[next]?.focus()
  }
  else if (event.key === 'ArrowLeft' && trail.value.length) {
    event.preventDefault()
    back()
  }
  else if (event.key === 'ArrowRight' && index >= 0) {
    event.preventDefault()
    buttons[index]?.click()
  }
}
</script>

<template>
  <ACascader v-if="panelMode === 'multi'" :model-value="value" :options="cascaderOptions" :disabled="disabled" :allow-clear="clearable" :check-strictly="!leafOnly" :placeholder="placeholder" :expand-trigger="triggerMode" :format-label="path => path.map(node => node.label).join(separator)" :trigger-props="{ contentClass: 'yd-cascader-popup', popupOffset: 6 }" class="yd-cascader-control" @update:model-value="update">
    <template #empty>
      <slot name="empty">
        没有可选项
      </slot>
    </template>
    <template #arrow-icon>
      <YdIcon name="morph:chevron-down" />
    </template>
    <template #option="{ data }">
      <span class="yd-cascader-label" :data-selected="data.value === value || undefined"><slot name="option" :option="findPath(options, data.value).at(-1)">{{ data.label }}</slot></span>
    </template>
  </ACascader>
  <div v-else class="yd-cascader-single">
    <PopoverRoot :open="open && !disabled" @update:open="open = !disabled && $event">
      <PopoverTrigger as-child>
        <YdButton variant="outline" :disabled="disabled" class="yd-cascader-trigger">
          <span :class="{ 'yd-muted': !label }">{{ label || placeholder }}</span><YdIcon name="morph:chevron-down" />
        </YdButton>
      </PopoverTrigger>
      <PopoverPortal>
        <PopoverContent align="start" :side-offset="6" class="yd-collection-popup yd-cascader-single-popup">
          <div v-if="trail.length" class="yd-cascader-breadcrumb">
            <YdButton variant="ghost" size="sm" @click="back">
              <YdIcon name="morph:arrow-left" />{{ trail.at(-1)?.label }}
            </YdButton>
          </div>
          <div ref="menuRef" role="group" aria-label="级联选择选项" class="yd-collection-viewport" @keydown="onKeydown">
            <button v-for="option in currentOptions" :key="option.value" type="button" class="yd-collection-option yd-cascader-choice" :disabled="option.disabled" :data-selected="option.value === value || undefined" @click="select(option)">
              <slot name="option" :option="option">
                {{ option.label }}
              </slot><YdIcon v-if="option.children?.length" name="morph:chevron-right" />
            </button>
            <div v-if="!currentOptions.length" class="yd-collection-empty">
              <slot name="empty">
                没有可选项
              </slot>
            </div>
          </div>
        </PopoverContent>
      </PopoverPortal>
    </PopoverRoot>
    <button v-if="clearable && value !== undefined" type="button" :disabled="disabled" class="yd-collection-icon" aria-label="清除选择" @click="clear">
      <YdIcon name="morph:x" />
    </button>
  </div>
</template>
