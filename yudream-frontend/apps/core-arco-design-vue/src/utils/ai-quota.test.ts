import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeAiQuota, normalizeAiQuotaConfig, normalizeAiQuotaReservations, parseAiTokenCount } from './ai-quota.ts'

test('quota counts accept actual numbers and decimal strings while preserving string user IDs', () => {
  const result = normalizeAiQuota({ userId: '9007199254740993123', usageDate: '2026-10-14', usedTokens: '12', reservedTokens: '20', limitTokens: 100, remainingTokens: '68' })
  assert.equal(result.userId, '9007199254740993123')
  assert.equal(result.usedTokens, 12)
  assert.equal(result.reservedTokens, 20)
  assert.deepEqual(normalizeAiQuotaConfig({ dailyTokenLimit: '2000', systemDailyTokenLimit: 10000 }), { dailyTokenLimit: 2000, systemDailyTokenLimit: 10000 })
})

test('unresolved usage preserves identifiers and normalizes held token quantities', () => {
  assert.deepEqual(normalizeAiQuotaReservations([{ reservationId: 'lease-1', userId: '9007199254740993123', runId: null, usageDate: '2026-10-14', heldTokens: '103', knownTokens: '5' }]), [{ reservationId: 'lease-1', userId: '9007199254740993123', runId: null, usageDate: '2026-10-14', heldTokens: 103, knownTokens: 5 }])
  assert.throws(() => normalizeAiQuotaReservations([{ reservationId: 'lease-1', userId: 42, runId: 'run-1', usageDate: '2026-10-14', heldTokens: '3' }]), /标识无效/)
})

test('quota boundary rejects invalid, negative, fractional and unsafe counts without fabricating zero', () => {
  for (const value of [undefined, null, '', 'bad', '-1', '-0', '1.5', '1e5', -1, 1.5, Number.NaN, Infinity, '9007199254740993']) {
    assert.throws(() => parseAiTokenCount(value, 'limitTokens'), /AI 额度字段/)
  }
  assert.throws(() => normalizeAiQuotaConfig({ dailyTokenLimit: '0' }), /systemDailyTokenLimit/)
})
