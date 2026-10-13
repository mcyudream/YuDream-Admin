export type AdminStylePreset = 'vibrant' | 'calm' | 'compact'
export type AdminMotionPreference = 'system' | 'reduced'

/** 管理后台的宿主外观配置，随现有主题配置整体保存。 */
export interface AdminAppearance {
  preset: AdminStylePreset
  motion: AdminMotionPreference
}

/** 兼容尚未保存外观配置的部署，以及未知版本或手动编辑的配置。 */
export function normalizeAdminAppearance(value: unknown): AdminAppearance {
  const source = value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}

  return {
    preset: source.preset === 'calm' || source.preset === 'compact' ? source.preset : 'vibrant',
    motion: source.motion === 'reduced' ? 'reduced' : 'system',
  }
}
