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
    axisLabel: { color: themeConfig.textSecondary },
    splitLine: { lineStyle: { color: gridColor } },
  }
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
  const gridColor = typeof themeConfig.grid === 'string'
    ? themeConfig.grid
    : (themeConfig.grid?.borderColor as string) || '#e5e7eb'

  const baseOption: EChartsOption = {
    backgroundColor: themeConfig.backgroundColor,
    textStyle: themeConfig.textStyle,
    title: dataset.label
      ? {
          text: dataset.label,
          textStyle: { color: themeConfig.text },
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
      textStyle: { color: themeConfig.text },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: hasDataZoom ? 36 : '3%',
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
        series: [{
          ...firstSeriesOption,
          type: 'line',
          smooth: true,
          encode: { x: nameDimension, y: valueDimension },
        }],
      }
    case 'bar':
      return {
        ...baseOption,
        xAxis: { type: 'category', ...buildAxis(themeConfig, gridColor) },
        yAxis: { type: 'value', ...buildAxis(themeConfig, gridColor) },
        series: [{ ...firstSeriesOption, type: 'bar', encode: { x: nameDimension, y: valueDimension } }],
      }
    case 'pie':
      return {
        ...baseOption,
        series: [
          {
            ...firstSeriesOption,
            type: 'pie',
            radius: ['40%', '70%'],
            encode: { itemName: nameDimension, value: valueDimension },
            itemStyle: { borderRadius: 8 },
            label: { color: themeConfig.text },
          },
        ],
      }
    default:
      return baseOption
  }
}
