import type { ChartThemeConfig } from '../types'

/**
 * 默认亮色主题色板
 */
const lightPalette = {
  background: '#ffffff',
  text: '#1f2937',
  textSecondary: '#6b7280',
  grid: '#e5e7eb',
  colors: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'],
}

/**
 * 默认暗色主题色板
 */
const darkPalette = {
  background: '#111827',
  text: '#f9fafb',
  textSecondary: '#9ca3af',
  grid: '#374151',
  colors: ['#60a5fa', '#34d399', '#fbbf24', '#f87171', '#a78bfa', '#f472b6', '#22d3ee', '#a3e635'],
}

/**
 * 获取默认图表主题配置
 * @param mode - 主题模式
 * @returns 主题配置对象
 */
export function useChartTheme(mode: 'light' | 'dark'): ChartThemeConfig {
  const palette = mode === 'dark' ? darkPalette : lightPalette

  return {
    name: `yudream-${mode}`,
    backgroundColor: palette.background,
    background: palette.background,
    text: palette.text,
    textSecondary: palette.textSecondary,
    grid: palette.grid,
    color: palette.colors,
    colors: palette.colors,
    textStyle: {
      color: palette.text,
    },
    title: {
      textStyle: { color: palette.text },
      subtextStyle: { color: palette.textSecondary },
    },
    legend: {
      textStyle: { color: palette.text },
    },
    axis: {
      axisLine: { lineStyle: { color: palette.grid } },
      axisLabel: { color: palette.textSecondary },
      splitLine: { lineStyle: { color: palette.grid } },
    },
  }
}
