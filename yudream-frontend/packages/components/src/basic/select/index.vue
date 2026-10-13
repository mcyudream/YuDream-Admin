<script setup lang="ts" generic="T extends SelectModelValue">
import type { SelectContentProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import type { SelectGroupOption, SelectModelValue, SelectOption } from './select-model'
import { useTextDirection } from '@vueuse/core'
import { computed, onBeforeUnmount, ref, useAttrs, watch } from 'vue'
import { cn } from '#utils'
import Button from '../button/index.vue'
import Icon from '../icon/index.vue'
import Input from '../input/index.vue'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from './select'
import {
  createSelectOptionKeyResolver,
  filterSelectOptions,
  mapSelectValueFromInternal,
  mapSelectValueToInternal,
  resolveSelectSelection,
} from './select-model'

defineOptions({ name: 'BuiltInSelect', inheritAttrs: false })
const props = withDefaults(defineProps<{
  multiple?: boolean
  disabled?: boolean
  position?: SelectContentProps['position']
  options: (SelectOption | SelectGroupOption)[]
  placeholder?: string
  allowSearch?: boolean
  searchPlaceholder?: string
  emptyText?: string
  class?: HTMLAttributes['class']
}>(), { placeholder: '没有选择', allowSearch: false, searchPlaceholder: '搜索选项…', emptyText: '没有匹配的选项' })
const emits = defineEmits<{
  change: [value: T | undefined]
  search: [value: string]
}>()
const value = defineModel<T>()
const attrs = useAttrs()
const open = ref(false)
const query = ref<string | undefined>('')
const searchContainer = ref<HTMLElement | null>(null)
let searchFocusFrame: number | undefined
const dir = useTextDirection({ observe: true })
const formName = computed(() => typeof attrs.name === 'string' ? attrs.name : undefined)
const optionKey = createSelectOptionKeyResolver()
const visibleOptions = computed(() => {
  const keyword = props.allowSearch ? query.value || '' : ''
  // 在筛选前确定候选身份，分组的筛选副本也沿用原始组 key。
  return props.options.flatMap(original => filterSelectOptions([original], keyword)
    .map(option => ({ option, key: optionKey(original) })))
})
const selectedOptions = computed(() => resolveSelectSelection(props.options, value.value, Boolean(props.multiple)))
const selectValue = computed({
  get: () => mapSelectValueToInternal(value.value),
  set: (next: SelectModelValue | undefined) => { value.value = mapSelectValueFromInternal(next) as T | undefined },
})

watch(value, next => emits('change', next))
watch(query, next => emits('search', next || ''))
watch(open, (next) => {
  cancelSearchFocus()
  if (next && props.allowSearch) {
    scheduleSearchFocus()
  }
  if (!next) {
    query.value = ''
  }
})
watch(() => props.allowSearch, () => {
  cancelSearchFocus()
  query.value = ''
  if (open.value && props.allowSearch) {
    scheduleSearchFocus()
  }
})
watch(() => props.disabled, (disabled) => {
  if (disabled) {
    open.value = false
  }
})

function focusSearch() {
  if (open.value && props.allowSearch) {
    searchContainer.value?.querySelector<HTMLInputElement>('input')?.focus()
  }
}

function clearSearch() {
  query.value = ''
  focusSearch()
}

function cancelSearchFocus() {
  if (searchFocusFrame !== undefined) {
    cancelAnimationFrame(searchFocusFrame)
    searchFocusFrame = undefined
  }
}

function scheduleSearchFocus() {
  // 等浮层定位及 Select 默认焦点完成后，聚焦搜索；关闭和卸载会取消。
  searchFocusFrame = requestAnimationFrame(() => {
    searchFocusFrame = requestAnimationFrame(() => {
      searchFocusFrame = undefined
      focusSearch()
    })
  })
}

onBeforeUnmount(cancelSearchFocus)

function searchKeydown(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) {
    event.stopPropagation()
    return
  }
  // 搜索输入不参与 Select 的字符定位；向下键进入筛选结果，Esc 沿用浮层关闭。
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    event.stopPropagation()
    const popup = (event.target as HTMLElement).closest('[data-slot="select-content"]')
    const items = popup?.querySelectorAll<HTMLElement>('[data-slot="select-item"]:not([data-disabled])')
    const target = event.key === 'ArrowDown' ? items?.[0] : items?.[items.length - 1]
    target?.focus()
  }
  else if (event.key !== 'Escape' && event.key !== 'Tab') {
    event.stopPropagation()
  }
}
</script>

<template>
  <Select v-model="selectValue" v-model:open="open" :multiple :disabled :name="formName" :dir="dir === 'ltr' ? 'ltr' : 'rtl'">
    <SelectTrigger
      v-bind="$attrs"
      :class="cn('w-[200px]', multiple && 'yd-select-multiple h-auto min-h-9 whitespace-normal py-2', props.class)"
      :title="selectedOptions.map(option => option.label).join('、') || props.placeholder"
    >
      <SelectValue :placeholder="props.placeholder" :class="multiple ? 'min-w-0 flex-wrap line-clamp-none text-start' : undefined">
        <template v-if="multiple && selectedOptions.length">
          <span v-for="(option, index) in selectedOptions" :key="index" class="text-xs text-secondary-foreground px-2 py-0.5 rounded-md bg-secondary inline-flex max-w-full break-all items-center">
            {{ option.label }}
          </span>
        </template>
        <template v-else>
          {{ selectedOptions[0]?.label || props.placeholder }}
        </template>
      </SelectValue>
    </SelectTrigger>
    <SelectContent :position class="z-2000">
      <div v-if="allowSearch" ref="searchContainer" class="p-1 pb-2 bg-popover top-0 sticky z-1">
        <Input v-model="query" role="searchbox" :placeholder="searchPlaceholder" aria-label="搜索选项" @keydown="searchKeydown">
          <template #start>
            <Icon name="morph:search" />
          </template>
          <template #end>
            <Button v-if="query" type="button" variant="ghost" size="icon" class="size-6" aria-label="清除搜索" @click="clearSearch">
              <Icon name="morph:x" />
            </Button>
          </template>
        </Input>
      </div>
      <template v-for="{ option, key } in visibleOptions" :key="key">
        <SelectGroup v-if="'options' in option">
          <SelectLabel>{{ option.label }}</SelectLabel>
          <SelectItem v-for="item in option.options" :key="optionKey(item)" :value="mapSelectValueToInternal(item.value) as SelectOption['value']" :disabled="item.disabled">
            {{ item.label }}
          </SelectItem>
        </SelectGroup>
        <SelectItem v-else :value="mapSelectValueToInternal(option.value) as SelectOption['value']" :disabled="option.disabled">
          {{ option.label }}
        </SelectItem>
      </template>
      <p v-if="!visibleOptions.length" role="status" class="text-sm text-muted-foreground px-3 py-4 text-center">
        {{ emptyText }}
      </p>
    </SelectContent>
  </Select>
</template>

<style>
.yd-select-multiple[data-slot="select-trigger"][data-size] {
  height: auto;
}
.yd-select-multiple[data-slot="select-trigger"] [data-slot="select-value"] {
  display: flex;
  flex-wrap: wrap;
  overflow: visible;
  -webkit-line-clamp: unset;
  white-space: normal;
}
</style>
