export type DashboardTone = 'blue' | 'green' | 'amber' | 'rose' | 'purple' | 'cyan' | 'gray' | 'orange' | string

// Keep legacy card metadata compatible; visual emphasis follows the active theme.
export function toneIconClass(tone?: DashboardTone) {
  return tone === 'green' ? 'yd-dashboard-tone-success' : tone === 'amber' || tone === 'orange' ? 'yd-dashboard-tone-warning' : tone === 'gray' ? 'yd-dashboard-tone-neutral' : 'yd-dashboard-tone-info'
}

export function toneBorderClass(_tone?: DashboardTone) {
  return 'border-[var(--color-border-2)]'
}

export function toneTextClass(_tone?: DashboardTone) {
  return 'text-[var(--color-text-1)]'
}

export function toneSoftClass(_tone?: DashboardTone) {
  return 'bg-[var(--color-fill-2)] text-[var(--color-text-2)]'
}
