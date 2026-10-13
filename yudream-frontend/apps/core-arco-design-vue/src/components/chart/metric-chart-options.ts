import type { BarSeriesOption, LineSeriesOption } from 'echarts/charts'
import type { GridComponentOption, TooltipComponentOption } from 'echarts/components'
import type { ComposeOption } from 'echarts/core'

export interface MetricPoint { label: string, value: number | null }
export interface MetricChartTheme {
  accent: string
  text: string
  muted: string
  border: string
  surface: string
  font: string
  reducedMotion: boolean
}
export type MetricChartKind = 'line' | 'bar' | 'ranking'
export type MetricChartOption = ComposeOption<LineSeriesOption | BarSeriesOption | GridComponentOption | TooltipComponentOption>

export const formatMetric = (value: number) => new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)
export const shortMetricDate = (label: string) => label.replace(/^\d{4}[-/](\d{1,2})[-/](\d{1,2})$/, '$1/$2')

export const metricCategoryAxis = (kind: MetricChartKind, height = 220) => kind === 'ranking' && height >= 190 ? 'y' : 'x'

export function metricPoints(dimensions: string[], source: (unknown[] | Record<string, unknown>)[]): MetricPoint[] {
  return source.map((row) => {
    const cells = Array.isArray(row) ? row : dimensions.map(key => row[key])
    const raw = cells[1]
    const value = raw === null || raw === undefined || raw === '' ? Number.NaN : Number(raw)
    return { label: String(cells[0] ?? ''), value: Number.isFinite(value) ? value : null }
  })
}

export function buildMetricChartOption(input: {
  points: MetricPoint[]
  kind: MetricChartKind
  unit: string
  theme: MetricChartTheme
  width: number
  height?: number
}): MetricChartOption {
  const { points, kind, unit, theme, width, height = 220 } = input
  const category = kind === 'ranking'
  const ranking = metricCategoryAxis(kind, height) === 'y'
  const allZero = points.length > 0 && points.every(point => point.value === 0)
  const labels = points.map(point => point.label)
  const labelStep = Math.max(1, Math.ceil(points.length / Math.max(2, Math.floor(width / 65))))
  const compactNumber = (value: number) => Math.abs(value) >= 10000 ? `${formatMetric(value / 10000)}万` : formatMetric(value)
  const valueAxis = {
    type: 'value' as const,
    min: 0,
    max: allZero ? 1 : undefined,
    minInterval: 1,
    splitNumber: 2,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: theme.muted, fontSize: 11, margin: 10, formatter: (value: number) => allZero && value !== 0 ? '' : compactNumber(value) },
    splitLine: { show: !allZero, lineStyle: { color: theme.border, type: 'dashed' as const } },
  }
  const categoryAxis = {
    type: 'category' as const,
    data: labels,
    axisLine: { show: true, lineStyle: { color: theme.border } },
    axisTick: { show: false },
    axisLabel: { color: theme.muted, fontSize: 11, margin: 10, hideOverlap: true },
  }
  const baseSeries = {
    name: unit,
    data: points.map(point => point.value),
    itemStyle: { color: theme.accent },
    emphasis: { focus: 'self' as const },
  }
  return {
    backgroundColor: 'transparent',
    color: [theme.accent],
    textStyle: { fontFamily: theme.font, color: theme.text },
    animation: true,
    animationDuration: theme.reducedMotion ? 350 : 600,
    animationDurationUpdate: theme.reducedMotion ? 160 : 240,
    animationEasing: 'cubicOut',
    grid: { top: category && !ranking ? 22 : 12, right: ranking ? 32 : category ? 2 : 22, bottom: 2, left: 2, containLabel: true },
    tooltip: {
      trigger: 'axis',
      triggerOn: 'mousemove|click',
      renderMode: 'richText',
      confine: true,
      backgroundColor: theme.surface,
      borderColor: theme.border,
      borderWidth: 1,
      padding: [10, 12],
      textStyle: { color: theme.text, fontFamily: theme.font, fontSize: 12 },
      shadowBlur: 0,
      formatter: (params) => {
        const datum = Array.isArray(params) ? params[0] : params
        const point = points[datum?.dataIndex]
        return point ? `${point.label}\n${point.value === null ? '未上报' : `${formatMetric(point.value)} ${unit}`}` : ''
      },
      axisPointer: { type: 'line', snap: true, lineStyle: { color: theme.muted, type: 'dashed' }, label: { show: false } },
    },
    xAxis: ranking
      ? valueAxis
      : {
          ...categoryAxis,
          boundaryGap: (kind === 'bar' || category) && !allZero,
          axisLabel: { ...categoryAxis.axisLabel, interval: category ? 0 : labelStep - 1, hideOverlap: !category, rotate: category && width < 300 ? 45 : 0, fontSize: category ? 10 : 11, showMinLabel: true, showMaxLabel: true, formatter: shortMetricDate },
        },
    yAxis: ranking
      ? {
          ...categoryAxis,
          inverse: true,
          axisLine: { show: false },
          axisLabel: { ...categoryAxis.axisLabel, color: theme.text, hideOverlap: false, width: Math.min(92, width * 0.3), overflow: 'truncate', interval: 0 },
        }
      : valueAxis,
    series: kind === 'line' || (allZero && !category)
      ? [{
          ...baseSeries,
          type: 'line',
          smooth: false,
          connectNulls: false,
          showSymbol: points.length === 1 || allZero,
          symbol: 'circle',
          symbolSize: allZero ? 5 : 7,
          lineStyle: { width: 2.5 },
          areaStyle: {
            color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: theme.accent }, { offset: 1, color: theme.surface }] },
            opacity: 0.12,
          },
          emphasis: { scale: 1.4 },
        }]
      : [{
          ...baseSeries,
          type: 'bar',
          barMaxWidth: ranking ? 14 : 22,
          itemStyle: { color: theme.accent, borderRadius: ranking ? [0, 3, 3, 0] : [3, 3, 0, 0] },
          label: { show: category, position: ranking ? 'right' : 'top', color: theme.text, fontSize: 11, formatter: params => params.value === null ? '—' : formatMetric(Number(params.value)) },
        }],
  }
}
