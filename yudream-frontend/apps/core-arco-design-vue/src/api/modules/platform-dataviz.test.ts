import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeDataset } from './platform-dataviz-normalizer'

test('keeps cartesian series data when backend includes empty graph arrays', () => {
  const dataset = normalizeDataset({
    chartType: 'line',
    series: [{
      name: '接口调用',
      categories: ['2026/7/7', '2026/7/8'],
      values: ['960', '2460'],
      nodes: [],
      links: [],
    }],
  }, {
    chartType: 'line',
    datasetQuery: { metric: 'log-activity' },
  })

  assert.deepEqual(dataset.dimensions, ['name', '接口调用'])
  assert.deepEqual(dataset.source, [
    { name: '2026/7/7', 接口调用: 960 },
    { name: '2026/7/8', 接口调用: 2460 },
  ])
})
