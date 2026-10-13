import assert from 'node:assert/strict'
// 项目定向测试通过 tsx + Node 内置测试运行器执行。
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { normalizeAdminAppearance } from './admin-appearance'

describe('admin appearance settings compatibility', () => {
  it('gives existing deployments the default appearance when no configuration exists', () => {
    for (const value of [undefined, null, false, 42, 'compact', [], {}]) {
      assert.deepEqual(normalizeAdminAppearance(value), { preset: 'vibrant', motion: 'system' })
    }
  })

  it('preserves each supported preset and the explicit reduced-motion preference after a JSON round trip', () => {
    for (const preset of ['vibrant', 'calm', 'compact']) {
      const saved = JSON.parse(JSON.stringify({ preset, motion: 'reduced' }))
      assert.deepEqual(normalizeAdminAppearance(saved), { preset, motion: 'reduced' })
    }
  })

  it('recovers unknown fields independently without discarding a supported preference', () => {
    assert.deepEqual(normalizeAdminAppearance({ preset: 'future-preset', motion: 'reduced' }), {
      preset: 'vibrant',
      motion: 'reduced',
    })
    assert.deepEqual(normalizeAdminAppearance({ preset: 'compact', motion: 'always' }), {
      preset: 'compact',
      motion: 'system',
    })
    assert.deepEqual(normalizeAdminAppearance({ preset: 'calm' }), { preset: 'calm', motion: 'system' })
  })

  it('does not mutate the received settings or share mutable default objects', () => {
    const saved = Object.freeze({ preset: 'calm', motion: 'reduced', futureSetting: true })
    assert.deepEqual(normalizeAdminAppearance(saved), { preset: 'calm', motion: 'reduced' })
    const first = normalizeAdminAppearance(undefined)
    first.preset = 'compact'
    assert.equal(normalizeAdminAppearance(undefined).preset, 'vibrant')
    assert.equal(saved.futureSetting, true)
  })
})
