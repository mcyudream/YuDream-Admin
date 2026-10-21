/** Quantities may be decimal strings from Java Long JSON serialization; IDs never enter this converter. */
export function parseAiTokenCount(value: unknown, field: string): number {
  if (typeof value !== 'number' && (typeof value !== 'string' || !/^\d+$/.test(value))) {
    throw new Error(`AI 额度字段 ${field} 无效`)
  }
  const count = typeof value === 'number' ? value : Number(value)
  if (!Number.isSafeInteger(count) || count < 0) {
    throw new Error(`AI 额度字段 ${field} 超出有效范围`)
  }
  return count
}

function quotaRecord(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('AI 额度响应格式无效')
  }
  return value as Record<string, unknown>
}

export function normalizeAiQuota(value: unknown) {
  const quota = quotaRecord(value)
  if (typeof quota.userId !== 'string' || typeof quota.usageDate !== 'string') {
    throw new TypeError('AI 额度用户或日期字段无效')
  }
  return {
    userId: quota.userId,
    usageDate: quota.usageDate,
    usedTokens: parseAiTokenCount(quota.usedTokens, 'usedTokens'),
    reservedTokens: quota.reservedTokens === undefined ? undefined : parseAiTokenCount(quota.reservedTokens, 'reservedTokens'),
    uncertainTokens: quota.uncertainTokens === undefined ? undefined : parseAiTokenCount(quota.uncertainTokens, 'uncertainTokens'),
    limitTokens: parseAiTokenCount(quota.limitTokens, 'limitTokens'),
    remainingTokens: parseAiTokenCount(quota.remainingTokens, 'remainingTokens'),
    /** 管理员不限额：只显示已用量 */
    unlimited: quota.unlimited === true,
  }
}

export function normalizeAiQuotaConfig(value: unknown) {
  const config = quotaRecord(value)
  const result = {
    dailyTokenLimit: parseAiTokenCount(config.dailyTokenLimit, 'dailyTokenLimit'),
    systemDailyTokenLimit: parseAiTokenCount(config.systemDailyTokenLimit, 'systemDailyTokenLimit'),
  }
  if (result.dailyTokenLimit <= 0 || result.systemDailyTokenLimit <= 0) {
    throw new Error('AI 每日预算必须大于 0')
  }
  return result
}

export function normalizeAiQuotaReservations(value: unknown) {
  if (!Array.isArray(value)) {
    throw new TypeError('AI 待核对用量响应格式无效')
  }
  return value.map((value) => {
    const reservation = quotaRecord(value)
    if (typeof reservation.reservationId !== 'string' || typeof reservation.userId !== 'string' || typeof reservation.usageDate !== 'string' || (reservation.runId !== null && typeof reservation.runId !== 'string')) {
      throw new Error('AI 待核对用量标识无效')
    }
    return { reservationId: reservation.reservationId, userId: reservation.userId, usageDate: reservation.usageDate, runId: reservation.runId, heldTokens: parseAiTokenCount(reservation.heldTokens, 'heldTokens'), knownTokens: parseAiTokenCount(reservation.knownTokens, 'knownTokens') }
  })
}
