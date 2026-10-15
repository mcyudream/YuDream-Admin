<script setup lang="ts">
/**
 * YdFilterSelect：宽度受控的 YD 选择器。
 *
 * FaSelect 用 inheritAttrs:false 把 class 摊到内部 trigger 上，trigger 不带页面作用域属性，
 * 因此页面里 `.my-filter { width: 150px }` 这类 scoped 规则永远不生效（死样式），
 * 选择器只能退回内置 200px。这里把宽度写成内联样式——内联样式同样落在 trigger 上但不依赖作用域，
 * 页面就不必再写 `:deep()` 绕过。其余属性与事件与原选择器一致。
 */
import type { HTMLAttributes } from 'vue'
import type { SelectGroupOption, SelectModelValue, SelectOption } from '../basic/select/select-model'
import Select from '../basic/select/index.vue'

defineOptions({ name: 'YdFilterSelect' })

const props = withDefaults(defineProps<{
  options: (SelectOption | SelectGroupOption)[]
  /** 触发器的目标宽度，默认与列表筛选区一致。 */
  width?: string | number
  /** 允许收缩的最小宽度，窄容器下不会挤出换行。 */
  minWidth?: string | number
  placeholder?: string
  multiple?: boolean
  disabled?: boolean
  allowSearch?: boolean
  class?: HTMLAttributes['class']
}>(), {
  width: 168,
  minWidth: 132,
  disabled: undefined,
})

const emits = defineEmits<{
  change: [value: SelectModelValue]
  search: [value: string]
}>()
const value = defineModel<SelectModelValue>()
function cssSize(size: string | number) {
  return typeof size === 'number' ? `${size}px` : size
}
</script>

<template>
  <Select
    v-model="value"
    :options="props.options"
    :placeholder="props.placeholder"
    :multiple="props.multiple"
    :disabled="props.disabled"
    :allow-search="props.allowSearch"
    :class="props.class"
    :style="{ width: cssSize(props.width), minWidth: cssSize(props.minWidth) }"
    @change="emits('change', $event as SelectModelValue)"
    @search="emits('search', $event)"
  />
</template>
