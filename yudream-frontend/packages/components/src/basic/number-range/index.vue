<script setup lang="ts">
/**
 * YdNumberRange：最小-最大数值范围输入。
 *
 * 与 YdRangePicker 保持一致的数组绑定语义：`v-model` 为 `[最小值, 最大值]`，
 * 两侧均未填写时为 `undefined`。适用于筛选栏的数值区间条件。
 */
import type { HTMLAttributes } from 'vue'
import FaNumberField from '../number-field/index.vue'

defineOptions({ name: 'YdNumberRange' })

const props = withDefaults(defineProps<{
  /** 两侧占位符：[最小值占位, 最大值占位] */
  placeholder?: string[]
  min?: number
  max?: number
  step?: number
  disabled?: boolean
  /** 中间分隔符 */
  separator?: string
  class?: HTMLAttributes['class']
}>(), {
  placeholder: () => ['最小值', '最大值'],
  separator: '至',
})

const value = defineModel<(number | undefined)[] | undefined>()

/** 单侧更新：合并另一侧的现值，两侧都空时回退为 undefined */
function updateAt(index: 0 | 1, next: number | undefined) {
  const current = value.value ?? []
  const merged: (number | undefined)[] = [current[0], current[1]]
  merged[index] = next
  value.value = merged[0] === undefined && merged[1] === undefined ? undefined : merged
}
</script>

<template>
  <div class="flex gap-2 w-full items-center" :class="props.class">
    <FaNumberField
      class="flex-1 min-w-0 w-full"
      :model-value="value?.[0]"
      :placeholder="placeholder[0]"
      :min="min"
      :max="max"
      :step="step"
      :disabled="disabled"
      @update:model-value="(v: number | undefined) => updateAt(0, v)"
    />
    <span class="text-sm text-muted-foreground shrink-0">{{ separator }}</span>
    <FaNumberField
      class="flex-1 min-w-0 w-full"
      :model-value="value?.[1]"
      :placeholder="placeholder[1]"
      :min="min"
      :max="max"
      :step="step"
      :disabled="disabled"
      @update:model-value="(v: number | undefined) => updateAt(1, v)"
    />
  </div>
</template>
