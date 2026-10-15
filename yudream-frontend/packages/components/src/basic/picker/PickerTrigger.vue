<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed, nextTick, ref, watch } from 'vue'
import { cn } from '#utils'
import Icon from '../icon/index.vue'
import { Input } from '../input/input'
import { InputGroup, InputGroupAddon } from '../input/input-group'

defineOptions({
  name: 'YdPickerTrigger',
})

const props = withDefaults(defineProps<{
  text?: string | string[]
  placeholder?: string | string[]
  range?: boolean
  disabled?: boolean
  readonly?: boolean
  clearable?: boolean
  error?: boolean
  class?: HTMLAttributes['class']
}>(), {
  text: '',
  placeholder: '',
  range: false,
  clearable: true,
})

const emit = defineEmits<{
  clear: []
  commit: [value: string, index: number]
}>()

const inputTextClass = 'w-full min-w-0 flex-1 border-0 rounded-none bg-transparent ring-offset-transparent dark:bg-transparent focus-visible:ring-0 focus-visible:ring-transparent'

const texts = computed<string[]>(() => {
  const list = Array.isArray(props.text) ? props.text : [props.text]
  return props.range ? [list[0] ?? '', list[1] ?? ''] : [list[0] ?? '']
})

const placeholders = computed<string[]>(() => {
  const list = Array.isArray(props.placeholder) ? props.placeholder : [props.placeholder]
  return props.range ? [list[0] ?? '', list[1] ?? ''] : [list[0] ?? '']
})

const draft = ref<string[]>([...texts.value])
watch(texts, (next) => {
  draft.value = [...next]
})

const focused = ref(false)
const hovered = ref(false)
const hasValue = computed(() => texts.value.some(item => !!item))
const showClear = computed(() => props.clearable && !props.disabled && !props.readonly && hasValue.value && (focused.value || hovered.value))

async function commit(index: number) {
  const value = draft.value[index] ?? ''
  emit('commit', value, index)
  // 未通过校验或不完整的输入回退到受控展示值
  await nextTick()
  draft.value = [...texts.value]
}
</script>

<template>
  <InputGroup
    :class="cn('yd-picker', props.class)"
    :data-disabled="disabled ? 'true' : undefined"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    @focusin="focused = true"
    @focusout="focused = false"
  >
    <template v-for="(_, index) in draft" :key="index">
      <span v-if="index > 0" class="yd-picker-separator" aria-hidden="true">至</span>
      <Input
        v-model="draft[index]"
        :class="inputTextClass"
        :placeholder="placeholders[index]"
        :disabled="disabled"
        :readonly="readonly"
        :aria-invalid="error || undefined"
        autocomplete="off"
        @keydown.enter.prevent="commit(index)"
        @blur="commit(index)"
      />
    </template>
    <InputGroupAddon align="inline-end" class="yd-picker-addon">
      <button v-if="showClear" type="button" class="yd-picker-clear" aria-label="清除" @mousedown.prevent @click.stop="emit('clear')">
        <Icon name="i-lucide:x" />
      </button>
      <Icon name="morph:calendar-days" class="yd-picker-icon" />
    </InputGroupAddon>
  </InputGroup>
</template>
