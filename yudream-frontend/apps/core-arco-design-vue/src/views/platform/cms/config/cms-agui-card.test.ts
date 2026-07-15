import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { normalizeAguiCard } from './cms-agui-card'

test('normalizes AG-UI card fields and actions into display-safe strings', () => {
  assert.deepEqual(normalizeAguiCard({
    title: '风险摘要',
    summary: '发现两项风险',
    tone: 'warning',
    fields: [{ label: '高风险', value: 2 }, { label: '', value: 'ignored' }],
    actions: [{ label: '查看详情', action: 'open', value: '/risk' }],
  }), {
    title: '风险摘要',
    summary: '发现两项风险',
    tone: 'warning',
    fields: [{ label: '高风险', value: '2' }],
    actions: [{ label: '查看详情', action: 'open', value: '/risk' }],
  })
})

test('falls back to a neutral card for invalid optional values', () => {
  assert.deepEqual(normalizeAguiCard({ title: '完成', tone: 'unknown' }), {
    title: '完成',
    summary: '',
    tone: 'info',
    fields: [],
    actions: [],
  })
})
