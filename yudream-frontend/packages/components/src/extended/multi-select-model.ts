import type { YdMultiSelectOption } from './YdMultiSelect.vue'

export function normalizeYdMultiSelectValue(next: unknown, options: YdMultiSelectOption[], current: string[] = []): string[] | undefined {
  if (!Array.isArray(next)) {
    return undefined
  }
  const optionByValue = new Map(options.map(option => [option.value, option]))
  const nextAllowed = new Set(next.filter((item): item is string => {
    const option = typeof item === 'string' ? optionByValue.get(item) : undefined
    return Boolean(option && !option.disabled)
  }))
  const retainedLocked = current.filter((item) => {
    const option = optionByValue.get(item)
    return !option || option.disabled
  })
  const result: string[] = []
  for (const item of [...current, ...next]) {
    if (typeof item !== 'string' || result.includes(item)) {
      continue
    }
    if (retainedLocked.includes(item) || nextAllowed.has(item)) {
      result.push(item)
    }
  }
  return result
}

export function removeYdMultiSelectValue(current: string[], value: string): string[] {
  return [...new Set(current.filter(item => item !== value))]
}

export function equalYdMultiSelectValues(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((item, index) => item === right[index])
}

export function resolveYdMultiSelectInputAttrs(input: {
  id?: string
  invalid?: boolean | string
  describedby?: string
  label?: string
}) {
  return {
    'id': input.id,
    'aria-invalid': input.invalid === true || input.invalid === 'true' ? 'true' as const : undefined,
    'aria-describedby': input.describedby,
    'aria-label': input.label,
  }
}

export function resolveYdMultiSelectInputState(allowSearch: boolean, disabled: boolean) {
  return { readonly: !allowSearch && !disabled, disabled }
}

export function sliceYdMultiSelectTags<T>(tags: T[], maxCount?: number): { visible: T[], hiddenCount: number } {
  const visible = maxCount === undefined ? tags : tags.slice(0, Math.max(0, maxCount))
  return { visible, hiddenCount: tags.length - visible.length }
}
