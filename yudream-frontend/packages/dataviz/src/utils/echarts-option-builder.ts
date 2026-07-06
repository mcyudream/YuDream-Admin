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
      bottom: '3%',
      containLabel: true,
      borderColor: gridColor,
    },
    color: themeConfig.color,
    dataset: {
      dimensions: dataset.dimensions,
      source: dataset.source as unknown as any[],
    },
  }

  switch (type) {
    case 'line':
      return {
        ...baseOption,
        xAxis: buildAxis(themeConfig, gridColor),
        yAxis: buildAxis(themeConfig, gridColor),
        series: [{ type: 'line', smooth: true }],
      }
    case 'bar':
      return {
        ...baseOption,
        xAxis: buildAxis(themeConfig, gridColor),
        yAxis: buildAxis(themeConfig, gridColor),
        series: [{ type: 'bar' }],
      }
    case 'pie':
      return {
        ...baseOption,
        series: [
          {
            type: 'pie',
            radius: ['40%', '70%'],
            itemStyle: { borderRadius: 8 },
            label: { color: themeConfig.text },
          },
        ],
      }
    default:
      return baseOption
  }
}
