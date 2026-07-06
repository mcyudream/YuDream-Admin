import type { ChartTheme, ChartThemeConfig } from '../types'

/**
 * 默认亮色主题
 */
const defaultLight: ChartThemeConfig = {
  name: 'yudream-light',
  background: '#ffffff',
  backgroundColor: '#ffffff',
  text: '#1f2937',
  textSecondary: '#6b7280',
  grid: '#e5e7eb',
  color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'],
  colors: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'],
}

/**
 * 默认暗色主题
 */
const defaultDark: ChartThemeConfig = {
  name: 'yudream-dark',
  background: '#111827',
  backgroundColor: '#111827',
  text: '#f9fafb',
  textSecondary: '#9ca3af',
  grid: '#374151',
  color: ['#60a5fa', '#34d399', '#fbbf24', '#f87171', '#a78bfa', '#f472b6', '#22d3ee', '#a3e635'],
  colors: ['#60a5fa', '#34d399', '#fbbf24', '#f87171', '#a78bfa', '#f472b6', '#22d3ee', '#a3e635'],
}

/**
 * 解析主题为完整配置对象
 * @param theme - 主题名称或配置
 * @returns 完整主题配置
 */
export function resolveTheme(theme: ChartTheme): ChartThemeConfig {
  const base = theme === 'dark' ? defaultDark : defaultLight

  if (typeof theme === 'string') {
    return base
  }

  return {
    ...base,
    ...theme,
    color: theme.color ?? base.color,
    colors: theme.colors ?? theme.color ?? base.colors,
    backgroundColor: theme.backgroundColor ?? theme.background ?? base.backgroundColor,
    text: theme.text ?? base.text,
    textSecondary: theme.textSecondary ?? base.textSecondary,
    grid: theme.grid ?? base.grid,
  }
}

/**
 * 解析网格线颜色
 */
function resolveGridColor(themeConfig: ChartThemeConfig): string {
  if (typeof themeConfig.grid === 'string') {
    return themeConfig.grid
  }
  if (themeConfig.grid && typeof themeConfig.grid.borderColor === 'string') {
    return themeConfig.grid.borderColor
  }
  return '#e5e7eb'
}

/**
 * 将主题适配为 ECharts 可用的配置
 * @param theme - 原始主题
 * @returns ECharts 主题配置
 */
export function adaptThemeForECharts(theme: ChartTheme): ChartThemeConfig {
  const resolved = resolveTheme(theme)
  const gridColor = resolveGridColor(resolved)

  return {
    ...resolved,
    backgroundColor: resolved.backgroundColor,
    textStyle: {
      color: resolved.text,
    },
    title: {
      textStyle: { color: resolved.text },
      subtextStyle: { color: resolved.textSecondary },
    },
    legend: {
      textStyle: { color: resolved.text },
    },
    axis: {
      axisLine: { lineStyle: { color: gridColor } },
      axisLabel: { color: resolved.textSecondary },
      splitLine: { lineStyle: { color: gridColor } },
    },
    grid: { borderColor: gridColor },
  }
}

/**
 * D3 渲染所需的主题颜色
 */
export interface D3ThemeColors {
  background: string
  text: string
  textSecondary: string
  grid: string
  colors: string[]
}

/**
 * 将主题适配为 D3 可用的颜色配置
 * @param theme - 原始主题
 * @returns D3 颜色配置
 */
export function adaptThemeForD3(theme: ChartTheme): D3ThemeColors {
  const resolved = resolveTheme(theme)
  return {
    background: resolved.backgroundColor ?? resolved.background ?? '#ffffff',
    text: resolved.text ?? '#1f2937',
    textSecondary: resolved.textSecondary ?? '#6b7280',
    grid: resolveGridColor(resolved),
    colors: resolved.colors ?? resolved.color ?? defaultLight.colors ?? [],
  }
}
