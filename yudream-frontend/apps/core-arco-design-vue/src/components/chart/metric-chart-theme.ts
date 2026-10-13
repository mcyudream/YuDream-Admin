import type { MetricChartTheme } from './metric-chart-options'

/** Resolve inherited plugin / host CSS, including color-mix and oklch, to ECharts' sRGB colors. */
export function readMetricChartTheme(element: HTMLElement): MetricChartTheme {
  const probe = document.createElement('span')
  probe.style.cssText = 'position:absolute;visibility:hidden;pointer-events:none;'
  element.appendChild(probe)
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  const context = canvas.getContext('2d', { willReadFrequently: true })
  const color = (variable: string) => {
    probe.style.color = variable
    const resolved = getComputedStyle(probe).color
    if (!context) {
      return resolved
    }
    context.clearRect(0, 0, 1, 1)
    context.fillStyle = resolved
    context.fillRect(0, 0, 1, 1)
    const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data
    return `rgba(${r},${g},${b},${a / 255})`
  }
  try {
    return {
      accent: color('var(--yd-dashboard-info, var(--color-text-1))'),
      text: color('var(--color-text-1)'),
      muted: color('var(--color-text-3)'),
      border: color('var(--color-border-2)'),
      surface: color('var(--color-bg-2)'),
      font: getComputedStyle(element).fontFamily,
      reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches || document.documentElement.dataset.adminMotion === 'reduced',
    }
  }
  finally { probe.remove() }
}
