import type { MetricChartTheme, MetricPoint } from './metric-chart-options'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { buildMetricChartOption, metricPoints } from './metric-chart-options'

const theme: MetricChartTheme = { accent: 'rgb(12, 100, 85)', text: 'rgb(20, 24, 28)', muted: 'rgb(100, 104, 108)', border: 'rgb(220, 224, 228)', surface: 'rgb(255, 255, 255)', font: 'sans-serif', reducedMotion: false }
const points: MetricPoint[] = [216, 660, 135, 0, 0, 0, 73, 4536].map((value, i) => ({ label: `2026/10/${i + 7}`, value }))
const build = (overrides: Partial<Parameters<typeof buildMetricChartOption>[0]> = {}) => buildMetricChartOption({ points, kind: 'line', unit: '次接口调用', width: 340, theme, ...overrides })

describe('system metric chart', () => {
  it('preserves zeros and gaps in both dataset shapes', () => {
    assert.deepEqual(metricPoints(['day', 'count'], [{ day: 'a', count: 0 }, { day: 'b', count: null }, ['c', '12'], ['d', ''], ['e', 'invalid']]), [
      { label: 'a', value: 0 },
      { label: 'b', value: null },
      { label: 'c', value: 12 },
      { label: 'd', value: null },
      { label: 'e', value: null },
    ])
  })

  it('keeps the actual spike, baseline and gaps instead of smoothing / inventing data', () => {
    const option = build({ points: [...points, { label: '2026/10/15', value: null }] })
    const series = (option.series as any[])[0]
    assert.deepEqual(series.data, [...points.map(point => point.value), null])
    assert.equal(series.connectNulls, false)
    assert.equal(series.smooth, false)
    assert.equal((option.yAxis as any).min, 0)
    assert.equal((option.yAxis as any).minInterval, 1)
  })

  it('renders zero observations on a baseline without a fake positive bar', () => {
    const option = build({ kind: 'bar', points: points.map(point => ({ ...point, value: 0 })) })
    const series = (option.series as any[])[0]
    assert.equal(series.type, 'line')
    assert.equal(series.showSymbol, true)
    assert.ok(series.data.every((value: number) => value === 0))
    assert.equal((option.yAxis as any).axisLabel.formatter(1), '')
  })

  it('uses the current theme in marks, axes and tooltip, and respects reduced motion', () => {
    const option = build({ theme: { ...theme, reducedMotion: true } })
    assert.equal(option.animation, true)
    assert.ok(Number(option.animationDuration) < Number(build().animationDuration))
    assert.equal((option.series as any[])[0].itemStyle.color, theme.accent)
    assert.equal((option.tooltip as any).backgroundColor, theme.surface)
    assert.equal((option.xAxis as any).axisLabel.color, theme.muted)
  })

  it('reduces date density in narrow cards and confines a single readable tooltip', () => {
    assert.ok((build({ width: 220 }).xAxis as any).axisLabel.interval > (build({ width: 800 }).xAxis as any).axisLabel.interval)
    const tooltip = build().tooltip as any
    assert.equal(tooltip.confine, true)
    assert.equal(tooltip.renderMode, 'richText')
    assert.equal(tooltip.formatter([{ dataIndex: 7 }]), '2026/10/14\n4,536 次接口调用')
  })

  it('supports horizontal classifications and single / empty datasets', () => {
    const ranking = build({ kind: 'ranking', points: [{ label: '分发能力', value: 3 }] })
    assert.equal((ranking.yAxis as any).inverse, true)
    assert.equal((ranking.series as any[])[0].type, 'bar')
    assert.equal((build({ points: [points[0]] }).series as any[])[0].showSymbol, true)
    assert.deepEqual((build({ points: [] }).series as any[])[0].data, [])
  })

  it('keeps all classifications in short cards and switches to horizontal bars when tall', () => {
    const points = Array.from({ length: 9 }, (_, i) => ({ label: `分类${i}`, value: i }))
    const short = build({ kind: 'ranking', points, height: 120, width: 240 })
    const tall = build({ kind: 'ranking', points, height: 240 })
    assert.equal((short.xAxis as any).type, 'category')
    assert.equal((short.xAxis as any).axisLabel.interval, 0)
    assert.equal((short.xAxis as any).axisLabel.hideOverlap, false)
    assert.equal((short.xAxis as any).data.length, 9)
    assert.equal((tall.yAxis as any).type, 'category')
  })
})
