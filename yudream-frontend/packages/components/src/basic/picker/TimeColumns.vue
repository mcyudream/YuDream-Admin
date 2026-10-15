<script setup lang="ts">
import type { YdPickerTimePart } from './picker-model'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { pickerClockTokens, pickerParseTime } from './picker-model'

defineOptions({
  name: 'YdTimeColumns',
})

const props = withDefaults(defineProps<{
  modelValue?: string
  format?: string
  use12Hours?: boolean
  step?: {
    hour?: number
    minute?: number
    second?: number
  }
  disabledHours?: () => number[]
  disabledMinutes?: (hour?: number) => number[]
  disabledSeconds?: (hour?: number, minute?: number) => number[]
  hideDisabledOptions?: boolean
  disabled?: boolean
}>(), {
  modelValue: '',
  format: 'HH:mm:ss',
  use12Hours: false,
  hideDisabledOptions: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

interface YdTimeOption {
  value: string
  label: string
  selected: boolean
  disabled: boolean
}

interface YdTimeColumn {
  key: 'period' | 'hour' | 'minute' | 'second'
  label: string
  select: (value: string) => void
  options: YdTimeOption[]
}

const current = ref<YdPickerTimePart>(pickerParseTime(props.modelValue))

watch(() => props.modelValue, (value) => {
  current.value = pickerParseTime(value, current.value)
})

const tokens = computed(() => pickerClockTokens(props.format))
const period = computed<'am' | 'pm'>(() => current.value.hour < 12 ? 'am' : 'pm')

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function stepValues(max: number, step: number): number[] {
  const size = Number.isFinite(step) && step > 0 ? Math.floor(step) : 1
  const values: number[] = []
  for (let value = 0; value <= max; value += size) {
    values.push(value)
  }
  return values
}

function toHour12(hour: number): number {
  return hour % 12 === 0 ? 12 : hour % 12
}

function toHour24(hour12: number, dayPeriod: 'am' | 'pm'): number {
  const base = hour12 % 12
  return dayPeriod === 'pm' ? base + 12 : base
}

function nearestAllowed(target: number, values: number[], disabled: number[]): number {
  const allowed = values.filter(value => !disabled.includes(value))
  if (!allowed.length) {
    return target
  }
  const next = allowed.find(value => value >= target)
  return next ?? allowed[allowed.length - 1]
}

function normalizeTime(part: YdPickerTimePart): YdPickerTimePart {
  const hour = Math.min(23, Math.max(0, part.hour || 0))
  const minuteStep = props.step?.minute ?? 1
  const secondStep = props.step?.second ?? 1
  let minute = Math.min(59, Math.max(0, part.minute || 0))
  const minutes = props.disabledMinutes?.(hour) ?? []
  if (minutes.includes(minute)) {
    minute = nearestAllowed(minute, stepValues(59, minuteStep), minutes)
  }
  let second = Math.min(59, Math.max(0, part.second || 0))
  const seconds = props.disabledSeconds?.(hour, minute) ?? []
  if (seconds.includes(second)) {
    second = nearestAllowed(second, stepValues(59, secondStep), seconds)
  }
  return { hour, minute, second }
}

function commit(part: Partial<YdPickerTimePart>) {
  const next = normalizeTime({ ...current.value, ...part })
  current.value = next
  emit('update:modelValue', `${pad(next.hour)}:${pad(next.minute)}:${pad(next.second)}`)
}

const columns = computed<YdTimeColumn[]>(() => {
  const result: YdTimeColumn[] = []
  const hourStep = props.step?.hour ?? 1
  const disabledHours = new Set(props.disabledHours?.() ?? [])
  const disabledMinutes = new Set(props.disabledMinutes?.(current.value.hour) ?? [])
  const disabledSeconds = new Set(props.disabledSeconds?.(current.value.hour, current.value.minute) ?? [])
  if (props.use12Hours) {
    result.push({
      key: 'period',
      label: '时段',
      select: (value) => {
        const base = current.value.hour % 12
        commit({ hour: value === 'pm' ? base + 12 : base })
      },
      options: [
        { value: 'am', label: '上午', selected: period.value === 'am', disabled: false },
        { value: 'pm', label: '下午', selected: period.value === 'pm', disabled: false },
      ],
    })
  }
  const hours = stepValues(23, hourStep).filter(hour => !props.use12Hours || (period.value === 'pm' ? hour >= 12 : hour < 12))
  result.push({
    key: 'hour',
    label: '时',
    select: value => commit({ hour: props.use12Hours ? toHour24(Number(value), period.value) : Number(value) }),
    options: hours.map(hour => ({
      value: String(hour),
      label: pad(props.use12Hours ? toHour12(hour) : hour),
      selected: current.value.hour === hour,
      disabled: disabledHours.has(hour),
    })),
  })
  if (tokens.value.minute) {
    result.push({
      key: 'minute',
      label: '分',
      select: value => commit({ minute: Number(value) }),
      options: stepValues(59, props.step?.minute ?? 1).map(minute => ({
        value: String(minute),
        label: pad(minute),
        selected: current.value.minute === minute,
        disabled: disabledMinutes.has(minute),
      })),
    })
  }
  if (tokens.value.second) {
    result.push({
      key: 'second',
      label: '秒',
      select: value => commit({ second: Number(value) }),
      options: stepValues(59, props.step?.second ?? 1).map(second => ({
        value: String(second),
        label: pad(second),
        selected: current.value.second === second,
        disabled: disabledSeconds.has(second),
      })),
    })
  }
  return result
})

const columnElements = ref<Record<string, HTMLElement>>({})

function registerColumn(key: string, element: unknown) {
  if (element instanceof HTMLElement) {
    columnElements.value[key] = element
  }
}

function scrollSelectionIntoView() {
  for (const element of Object.values(columnElements.value)) {
    element.querySelector<HTMLElement>('[data-selected="true"]')?.scrollIntoView({ block: 'nearest' })
  }
}

onMounted(() => {
  nextTick(scrollSelectionIntoView)
})

watch(() => props.modelValue, () => {
  nextTick(scrollSelectionIntoView)
})
</script>

<template>
  <div class="yd-time-columns" role="group">
    <div v-for="column in columns" :key="column.key" :ref="element => registerColumn(column.key, element)" class="yd-time-column" role="listbox" :aria-label="column.label">
      <button
        v-for="option in column.options"
        v-show="!(hideDisabledOptions && option.disabled)"
        :key="`${column.key}-${option.value}`"
        type="button"
        role="option"
        class="yd-time-option"
        :class="{ 'yd-time-option--selected': option.selected }"
        :data-selected="option.selected ? 'true' : undefined"
        :aria-selected="option.selected"
        :disabled="disabled || option.disabled"
        @click="column.select(option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>
