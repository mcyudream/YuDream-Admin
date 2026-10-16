<script setup lang="ts">
/**
 * YdNumberRange：最小-最大数值范围输入。
 *
 * 与 YdRangePicker 保持一致的数组绑定语义：`v-model` 为 `[最小值, 最大值]`，
 * 两侧均未填写时为 `undefined`。适用于筛选栏的数值区间条件。
 */
import type { HTMLAttributes } from 'vue'
import { cn } from '#utils'
import {
  NumberField,
  NumberFieldContent,
  NumberFieldDecrement,
  NumberFieldIncrement,
  NumberFieldInput,
} from '../number-field/number-field'

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
function updateAt(index: 0 | 1, next: number | null | undefined) {
  const current = value.value ?? []
  const merged: (number | undefined)[] = [current[0], current[1]]
  merged[index] = next ?? undefined
  value.value = merged[0] === undefined && merged[1] === undefined ? undefined : merged
}
</script>

<template>
  <div class="flex gap-2 w-full items-center" :class="cn(props.class)">
    <NumberField
      class="flex-1 min-w-0 w-full"
      :model-value="value?.[0] ?? null"
      :min="min"
      :max="max"
      :step="step"
      :disabled="disabled"
      @update:model-value="(v: number | null) => updateAt(0, v)"
    >
      <NumberFieldContent>
        <NumberFieldDecrement />
        <NumberFieldInput :placeholder="placeholder[0]" />
        <NumberFieldIncrement />
      </NumberFieldContent>
    </NumberField>
    <span class="text-sm text-muted-foreground shrink-0">{{ separator }}</span>
    <NumberField
      class="flex-1 min-w-0 w-full"
      :model-value="value?.[1] ?? null"
      :min="min"
      :max="max"
      :step="step"
      :disabled="disabled"
      @update:model-value="(v: number | null) => updateAt(1, v)"
    >
      <NumberFieldContent>
        <NumberFieldDecrement />
        <NumberFieldInput :placeholder="placeholder[1]" />
        <NumberFieldIncrement />
      </NumberFieldContent>
    </NumberField>
  </div>
</template>
