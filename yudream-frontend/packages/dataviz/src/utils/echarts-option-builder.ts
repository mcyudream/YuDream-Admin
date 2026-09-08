import type { EChartsOption } from 'echarts'
import type { ChartDataset, ChartTheme, ChartThemeConfig } from '../types'
import { adaptThemeForECharts } from './theme-adapters'

/**
 * 推断数据集对应的图表类型
 */
function inferChartType(dataset: ChartDataset): 'line' | 'bar' | 'pie' {
  if (dataset.chartType) {
    return dataset.chartType === 'bar' ? 'bar' : dataset.chartType === 'pie' ? 'pie' : 'line'
  }
  if (dataset.nodes || dataset.links) {
    return 'pie'
  }
  const dims = dataset.dimensions || []
  if (dims.length <= 2) {
    return 'pie'
  }
  return 'line'
}

/**
 * 构建坐标轴配置
 */
function buildAxis(themeConfig: ChartThemeConfig, gridColor: string) {
  return {
    axisLine: { lineStyle: { color: gridColor } },
    axisLabel: {
      color: themeConfig.textSecondary,
      hideOverlap: true,
      fontSize: 11,
    },
    splitLine: { lineStyle: { color: gridColor } },
  }
}

function valueDimensions(dataset: ChartDataset): string[] {
  return (dataset.dimensions ?? []).slice(1).filter(Boolean)
}

function cartesianSeries(dataset: ChartDataset, type: 'line' | 'bar'): EChartsOption['series'] {
  const nameDimension = dataset.dimensions?.[0]
  const seriesOptions = dataset.series ?? []
  const dimensions = valueDimensions(dataset)
  const targets = dimensions.length > 0 ? dimensions : [dataset.dimensions?.[1]]

  return targets.map((dimension, index) => {
    const extra = seriesOptions[index] ?? {}
    return {
      ...extra,
      type,
      name: extra.name || dimension,
      smooth: type === 'line' ? extra.smooth ?? true : extra.smooth,
      encode: extra.encode ?? { x: nameDimension, y: dimension },
    }
  }) as EChartsOption['series']
}

/**
 * 根据数据集与主题构建 ECharts 配置项
 * @param dataset - 图表数据集
 * @param theme - 图表主题
 * @returns ECharts 配置项
 */
export function buildEChartsOption(dataset: ChartDataset, theme: ChartTheme): EChartsOption {
  const themeConfig = adaptThemeForECharts(theme)
  const type = inferChartType(dataset)
  const nameDimension = dataset.dimensions?.[0]
  const valueDimension = dataset.dimensions?.[1]
  const firstSeriesOption = dataset.series?.[0] ?? {}
  const hasDataZoom = Array.isArray(dataset.dataZoom) && dataset.dataZoom.length > 0
  const hasTitle = Boolean(dataset.label)
  const multiSeries = valueDimensions(dataset).length > 1
  const gridColor = typeof themeConfig.grid === 'string'
    ? themeConfig.grid
    : (themeConfig.grid?.borderColor as string) || '#e5e7eb'

  const baseOption: EChartsOption = {
    backgroundColor: themeConfig.backgroundColor,
    textStyle: themeConfig.textStyle,
    title: hasTitle
      ? {
          text: dataset.label,
          left: 0,
          top: 0,
          textStyle: { color: themeConfig.text, fontSize: 13, fontWeight: 600 },
          subtextStyle: { color: themeConfig.textSecondary },
        }
      : undefined,
    tooltip: {
      trigger: type === 'pie' ? 'item' : 'axis',
      backgroundColor: themeConfig.backgroundColor,
      borderColor: gridColor,
      textStyle: { color: themeConfig.text },
    },
    legend: {
      show: multiSeries,
      type: 'scroll',
      top: hasTitle ? 24 : 0,
      textStyle: { color: themeConfig.text, fontSize: 11 },
    },
    grid: {
      left: 8,
      right: 12,
      top: hasTitle ? (multiSeries ? 68 : 48) : (multiSeries ? 36 : 8),
      bottom: hasDataZoom ? 40 : 8,
      containLabel: true,
      borderColor: gridColor,
    },
    color: themeConfig.color,
    dataZoom: dataset.dataZoom as EChartsOption['dataZoom'],
    dataset: {
      dimensions: dataset.dimensions,
      source: (dataset.source ?? []) as unknown as any[],
    },
  }

  switch (type) {
    case 'line':
      return {
        ...baseOption,
        xAxis: { type: 'category', ...buildAxis(themeConfig, gridColor) },
        yAxis: { type: 'value', ...buildAxis(themeConfig, gridColor) },
        series: cartesianSeries(dataset, 'line'),
      }
    case 'bar':
      return {
        ...baseOption,
        xAxis: { type: 'category', ...buildAxis(themeConfig, gridColor) },
        yAxis: { type: 'value', ...buildAxis(themeConfig, gridColor) },
        series: cartesianSeries(dataset, 'bar'),
      }
    case 'pie':
      return {
        ...baseOption,
        legend: {
          ...baseOption.legend,
          show: true,
          type: 'scroll',
          orient: 'horizontal',
          left: 'center',
          bottom: 0,
          top: undefined,
        },
        series: [
          {
            ...firstSeriesOption,
            type: 'pie',
            radius: ['38%', '58%'],
            center: ['50%', '44%'],
            encode: { itemName: nameDimension, value: valueDimension },
            itemStyle: { borderRadius: 8 },
            label: { show: false },
          },
        ],
      }
    default:
      return baseOption
  }
}
