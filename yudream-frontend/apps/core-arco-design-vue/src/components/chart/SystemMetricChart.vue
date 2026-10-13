<script setup lang="ts">
import type { EChartsType } from 'echarts/core'
import type { MetricChartKind, MetricPoint } from './metric-chart-options'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, useId, watch } from 'vue'
import { buildMetricChartOption, formatMetric, metricCategoryAxis } from './metric-chart-options'
import { readMetricChartTheme } from './metric-chart-theme'

const props = withDefaults(defineProps<{
  points: MetricPoint[]
  kind?: MetricChartKind
  unit: string
  label: string
  zeroMessage?: string
}>(), { kind: 'line' })

const emit = defineEmits<{ select: [index: number | null] }>()

use([BarChart, LineChart, GridComponent, TooltipComponent, SVGRenderer])

const surface = ref<HTMLElement>()
const canvas = ref<HTMLElement>()
const helpId = useId()
const allZero = computed(() => props.points.length > 0 && props.points.every(point => point.value === 0))
let chart: EChartsType | undefined
let resizeObserver: ResizeObserver | undefined
let themeObserver: MutationObserver | undefined
let motion: MediaQueryList | undefined
let frame = 0
let active = true
let keyboardIndex: number | null = null
let signature = ''

function render() {
  frame = 0
  const element = canvas.value
  if (!active || !element || !surface.value || !element.clientWidth || !element.clientHeight) {
    return
  }
  if (!chart) {
    chart = init(element, undefined, { renderer: 'svg' })
    chart.on('updateAxisPointer', (event: unknown) => {
      const axis = (event as { axesInfo?: { axisDim: string, value: number }[] }).axesInfo?.find(axis => axis.axisDim === metricCategoryAxis(props.kind, canvas.value?.clientHeight))
      const index = axis?.value
      if (index !== undefined && Number.isInteger(index) && index >= 0 && index < props.points.length) {
        keyboardIndex = index
        emit('select', index)
      }
    })
    chart.getZr().on('globalout', clearSelection)
  }
  chart.resize({ animation: { duration: 0 } })
  const theme = readMetricChartTheme(surface.value)
  const nextSignature = JSON.stringify([props.points, props.kind, props.unit, element.clientWidth, element.clientHeight, theme])
  if (signature !== nextSignature) {
    signature = nextSignature
    chart.setOption(buildMetricChartOption({ points: props.points, kind: props.kind, unit: props.unit, theme, width: element.clientWidth, height: element.clientHeight }), { notMerge: true })
  }
}

function schedule() {
  if (!frame && active) {
    frame = requestAnimationFrame(render)
  }
}

function clearSelection() {
  keyboardIndex = null
  emit('select', null)
  chart?.dispatchAction({ type: 'updateAxisPointer', currTrigger: 'leave' })
  chart?.dispatchAction({ type: 'hideTip' })
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    clearSelection()
    return
  }
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || !props.points.length) {
    return
  }
  event.preventDefault()
  const last = props.points.length - 1
  keyboardIndex = event.key === 'Home' ? 0 : event.key === 'End' ? last : Math.max(0, Math.min(last, (keyboardIndex ?? last) + (event.key === 'ArrowLeft' ? -1 : 1)))
  chart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: keyboardIndex })
  emit('select', keyboardIndex)
}

function stylesheetLoaded(event: Event) {
  if (event.target instanceof HTMLLinkElement && event.target.rel === 'stylesheet') {
    schedule()
  }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(schedule)
  resizeObserver.observe(canvas.value!)
  // Plugin styles can load / unload without changing the root theme class.
  themeObserver = new MutationObserver(schedule)
  themeObserver.observe(document.documentElement, { attributes: true })
  themeObserver.observe(document.body, { attributes: true })
  themeObserver.observe(document.head, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['href', 'media', 'disabled'] })
  motion = window.matchMedia('(prefers-reduced-motion: reduce)')
  motion.addEventListener('change', schedule)
  document.addEventListener('load', stylesheetLoaded, true)
  schedule()
})
watch(() => [props.points, props.kind, props.unit], () => {
  clearSelection()
  schedule()
}, { deep: true })
onActivated(() => {
  active = true
  schedule()
})
onDeactivated(() => {
  active = false
  cancelAnimationFrame(frame)
  frame = 0
  clearSelection()
})
onBeforeUnmount(() => {
  active = false
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  motion?.removeEventListener('change', schedule)
  document.removeEventListener('load', stylesheetLoaded, true)
  chart?.dispose()
})
</script>

<template>
  <div ref="surface" class="system-metric-chart" role="group" tabindex="0" :aria-label="label" :aria-describedby="helpId" @keydown="onKeydown" @blur="clearSelection">
    <div ref="canvas" class="system-metric-chart__canvas" aria-hidden="true" />
    <div v-if="allZero && zeroMessage" class="system-metric-chart__zero">
      <FaIcon name="morph:chart-no-axes-combined" />
      <span>{{ zeroMessage }}</span>
    </div>
    <div class="sr-only">
      <p :id="helpId">
        左右方向键查看数据，Home 和 End 跳到首尾，Esc 关闭提示。
      </p>
      <table>
        <caption>{{ label }}</caption>
        <thead>
          <tr>
            <th scope="col">
              日期或分类
            </th><th scope="col">
              {{ unit }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(point, index) in points" :key="index">
            <th scope="row">
              {{ point.label }}
            </th><td>{{ point.value === null ? '未上报' : formatMetric(point.value) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.system-metric-chart {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 80px;
  border-radius: 4px;
}

.system-metric-chart:focus-visible {
  outline: 1px solid var(--color-text-3);
  outline-offset: 3px;
}

.system-metric-chart__canvas {
  position: absolute;
  inset: 0;
}

.system-metric-chart__zero {
  position: absolute;
  inset: 0 0 32px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--color-text-3);
  pointer-events: none;
}

.system-metric-chart__zero > :first-child {
  font-size: 24px;
  color: var(--color-text-4);
}
</style>
