<script setup lang="ts">
import { ComboboxAnchor, ComboboxContent, ComboboxGroup, ComboboxInput, ComboboxItem, ComboboxItemIndicator, ComboboxLabel, ComboboxPortal, ComboboxRoot, ComboboxTrigger, ComboboxViewport, ComboboxVirtualizer } from 'reka-ui'
import { computed, ref } from 'vue'
import FaIcon from '../basic/icon/index.vue'
import { getYdComboboxCreateValue } from './combobox-create-model'
import './extended.css'

export interface YdComboboxOption {
  label: string
  value: string | number
  disabled?: boolean
}
export interface YdComboboxGroup {
  label: string
  options: YdComboboxOption[]
}
const props = withDefaults(defineProps<{
  options: (YdComboboxOption | YdComboboxGroup)[]
  disabled?: boolean
  clearable?: boolean
  placeholder?: string
  emptyText?: string
  virtualList?: boolean
  loading?: boolean
  allowCreate?: boolean
  filter?: (search: string, option: YdComboboxOption) => boolean
}>(), { placeholder: '搜索并选择', emptyText: '没有匹配项' })
const emit = defineEmits<{
  change: [value: string | number | undefined]
  clear: []
  search: [value: string]
  create: [value: string]
}>()
const value = defineModel<string | number>()
const open = ref(false)
const query = ref('')
const flatOptions = computed(() => props.options.flatMap(item => 'options' in item ? item.options : [item]))
const optionMap = computed(() => new Map(flatOptions.value.map(option => [option.value, option])))
const groupMap = computed(() => new Map(props.options.flatMap(item => 'options' in item ? item.options.map(option => [option.value, item.label] as const) : [])))
const groups = computed(() => props.options.map((item) => {
  const grouped = 'options' in item
  const options = grouped ? item.options : [item]
  return {
    label: grouped ? item.label : '',
    options: options.filter(option => !query.value || (props.filter?.(query.value, option) ?? (option.label.toLocaleLowerCase().includes(query.value.toLocaleLowerCase()) || (grouped && item.label.toLocaleLowerCase().includes(query.value.toLocaleLowerCase()))))),
  }
}).filter(group => group.options.length))
const visibleOptions = computed(() => groups.value.flatMap(group => group.options))
const createValue = computed(() => props.allowCreate && !props.loading ? getYdComboboxCreateValue(query.value, flatOptions.value) : undefined)
const createOption = Object.freeze({ __ydComboboxCreate: true as const })
const virtualValues = computed(() => visibleOptions.value.map(option => option.value))
function optionFor(key: string | number) {
  return optionMap.value.get(key)
}
function displayValue(key: unknown) {
  return typeof key === 'string' || typeof key === 'number' ? optionMap.value.get(key)?.label || '' : ''
}
function search(next: string) {
  query.value = next
  emit('search', next)
}
function select(next: unknown) {
  if (props.disabled || props.loading || (typeof next !== 'string' && typeof next !== 'number')) {
    return
  }
  if (!optionMap.value.has(next) || optionFor(next)?.disabled) {
    return
  }
  value.value = next
  query.value = ''
  emit('change', next)
}
function create() {
  const candidate = createValue.value
  if (props.disabled || props.loading || !candidate) {
    return
  }
  // Handle selection before Reka closes the popup and clears the search query.
  emit('create', candidate)
  query.value = ''
  open.value = false
}
function clear() {
  if (props.disabled) {
    return
  }
  value.value = undefined
  query.value = ''
  emit('change', undefined)
  emit('clear')
}
function changeOpen(next: boolean) {
  open.value = !props.disabled && next
  query.value = ''
}
</script>

<template>
  <ComboboxRoot :model-value="value" :open="open" :disabled="disabled" ignore-filter open-on-focus open-on-click class="yd-combobox" @update:model-value="select" @update:open="changeOpen">
    <ComboboxAnchor class="yd-collection-control" :data-disabled="disabled || undefined" :aria-busy="loading">
      <FaIcon name="morph:search" class="yd-collection-leading" />
      <ComboboxInput :display-value="displayValue" :placeholder="placeholder" class="yd-collection-input" @update:model-value="search" />
      <button v-if="clearable && value !== undefined && !disabled" type="button" class="yd-collection-icon" aria-label="清除选择" @click="clear">
        <FaIcon name="morph:x" />
      </button>
      <ComboboxTrigger class="yd-collection-icon" aria-label="展开选项">
        <FaIcon :name="loading ? 'morph:loader-circle+animate-spin' : 'morph:chevron-down'" />
      </ComboboxTrigger>
    </ComboboxAnchor>
    <ComboboxPortal>
      <ComboboxContent position="popper" align="start" :side-offset="6" class="yd-collection-popup yd-combobox-popup">
        <ComboboxViewport class="yd-collection-viewport">
          <div v-if="loading" class="yd-collection-empty" role="status">
            <slot name="loading">
              正在加载…
            </slot>
          </div>
          <div v-else-if="!visibleOptions.length && !createValue" class="yd-collection-empty">
            <slot name="empty">
              {{ emptyText }}
            </slot>
          </div>
          <ComboboxVirtualizer v-else-if="virtualList" v-slot="{ option }" :options="virtualValues" :estimate-size="44" :text-content="displayValue">
            <ComboboxItem :value="option" :disabled="optionFor(option)?.disabled" class="yd-collection-option">
              <span><slot name="option" :option="optionFor(option)">{{ displayValue(option) }}<small v-if="groupMap.get(option)" class="yd-collection-group-hint">{{ groupMap.get(option) }}</small></slot></span>
              <ComboboxItemIndicator><FaIcon name="morph:check" /></ComboboxItemIndicator>
            </ComboboxItem>
          </ComboboxVirtualizer>
          <template v-else>
            <ComboboxGroup v-for="(group, index) in groups" :key="index">
              <ComboboxLabel v-if="group.label" class="yd-collection-label">
                {{ group.label }}
              </ComboboxLabel>
              <ComboboxItem v-for="option in group.options" :key="option.value" :value="option.value" :disabled="option.disabled" class="yd-collection-option">
                <span><slot name="option" :option="option">{{ option.label }}</slot></span>
                <ComboboxItemIndicator><FaIcon name="morph:check" /></ComboboxItemIndicator>
              </ComboboxItem>
            </ComboboxGroup>
          </template>
          <ComboboxItem v-if="createValue" :value="createOption" class="yd-collection-option yd-combobox-create-option" @select.prevent="create">
            使用这个编码：<strong>{{ createValue }}</strong>
          </ComboboxItem>
        </ComboboxViewport>
      </ComboboxContent>
    </ComboboxPortal>
  </ComboboxRoot>
</template>
