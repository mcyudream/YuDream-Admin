import type { YdComboboxOption } from './YdCombobox.vue'

export function getYdComboboxCreateValue(query: string, options: YdComboboxOption[]): string | undefined {
  const value = query.trim()
  if (!value) {
    return undefined
  }
  const normalized = value.toLocaleLowerCase()
  const exists = options.some(option => option.value.toLocaleString().toLocaleLowerCase() === normalized || option.label.toLocaleLowerCase() === normalized)
  return exists ? undefined : value
}
