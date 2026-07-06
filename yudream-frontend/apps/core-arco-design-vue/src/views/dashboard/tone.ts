export type DashboardTone = 'blue' | 'green' | 'amber' | 'rose' | 'purple' | 'cyan' | 'gray' | 'orange' | string

export function toneIconClass(tone?: DashboardTone) {
  const map: Record<string, string> = {
    blue: 'text-blue-600 dark:text-blue-300',
    green: 'text-green-600 dark:text-green-300',
    amber: 'text-amber-600 dark:text-amber-300',
    rose: 'text-rose-600 dark:text-rose-300',
    purple: 'text-purple-600 dark:text-purple-300',
    cyan: 'text-cyan-600 dark:text-cyan-300',
    gray: 'text-gray-600 dark:text-gray-300',
    orange: 'text-orange-600 dark:text-orange-300',
  }
  return map[tone || 'blue'] || map.blue
}

export function toneBorderClass(tone?: DashboardTone) {
  const map: Record<string, string> = {
    blue: 'border-blue-200 dark:border-blue-900',
    green: 'border-green-200 dark:border-green-900',
    amber: 'border-amber-200 dark:border-amber-900',
    rose: 'border-rose-200 dark:border-rose-900',
    purple: 'border-purple-200 dark:border-purple-900',
    cyan: 'border-cyan-200 dark:border-cyan-900',
    gray: 'border-gray-200 dark:border-gray-800',
    orange: 'border-orange-200 dark:border-orange-900',
  }
  return map[tone || 'blue'] || map.blue
}

export function toneTextClass(tone?: DashboardTone) {
  const map: Record<string, string> = {
    blue: 'text-blue-600 dark:text-blue-300',
    green: 'text-green-600 dark:text-green-300',
    amber: 'text-amber-600 dark:text-amber-300',
    rose: 'text-rose-600 dark:text-rose-300',
    purple: 'text-purple-600 dark:text-purple-300',
    cyan: 'text-cyan-600 dark:text-cyan-300',
    gray: 'text-gray-600 dark:text-gray-300',
    orange: 'text-orange-600 dark:text-orange-300',
  }
  return map[tone || 'blue'] || map.blue
}

export function toneSoftClass(tone?: DashboardTone) {
  const map: Record<string, string> = {
    blue: 'bg-blue-50/70 text-blue-700 dark:bg-blue-950/50 dark:text-blue-200',
    green: 'bg-green-50/70 text-green-700 dark:bg-green-950/50 dark:text-green-200',
    amber: 'bg-amber-50/70 text-amber-700 dark:bg-amber-950/50 dark:text-amber-200',
    rose: 'bg-rose-50/70 text-rose-700 dark:bg-rose-950/50 dark:text-rose-200',
    purple: 'bg-purple-50/70 text-purple-700 dark:bg-purple-950/50 dark:text-purple-200',
    cyan: 'bg-cyan-50/70 text-cyan-700 dark:bg-cyan-950/50 dark:text-cyan-200',
    gray: 'bg-gray-100/70 text-gray-700 dark:bg-gray-800/50 dark:text-gray-200',
    orange: 'bg-orange-50/70 text-orange-700 dark:bg-orange-950/50 dark:text-orange-200',
  }
  return map[tone || 'blue'] || map.blue
}
