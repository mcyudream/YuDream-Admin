import type { AcceptableValue } from 'reka-ui'
import { isEqual } from 'es-toolkit'

export interface SelectOption {
  label: string
  value: AcceptableValue
  disabled?: boolean
}

export interface SelectGroupOption {
  label: string
  options: SelectOption[]
}

export type SelectModelValue = AcceptableValue | AcceptableValue[]
type SelectOptions = readonly (SelectOption | SelectGroupOption)[]

// Reka Select 的空字符串项必须转换为稳定的非空内部值。
const EMPTY_STRING_ITEM_VALUE = '__FA_SELECT_EMPTY_STRING__'

function isGroup(option: SelectOption | SelectGroupOption): option is SelectGroupOption {
  return 'options' in option && Array.isArray(option.options)
}

export function createSelectOptionKeyResolver() {
  const objectKeys = new WeakMap<object, number>()
  let nextObjectKey = 0
  const objectKey = (value: object) => {
    let key = objectKeys.get(value)
    if (key === undefined) {
      key = nextObjectKey++
      objectKeys.set(value, key)
    }
    return key
  }
  return (option: SelectOption | SelectGroupOption): string => {
    if (isGroup(option)) {
      return `group:${objectKey(option)}`
    }
    const value = option.value
    if (value !== null && typeof value === 'object') {
      return `item:object:${objectKey(value)}`
    }
    return `item:${typeof value}:${Object.is(value, -0) ? '-0' : String(value)}`
  }
}

export function flattenSelectOptions(options: SelectOptions): SelectOption[] {
  return options.flatMap(option => isGroup(option) ? option.options : [option])
}

function objectLabel(value: Record<string, unknown>): string {
  return typeof value.label === 'string' && value.label.trim() ? value.label : '已选项'
}

export function resolveSelectSelection(
  options: SelectOptions,
  model: SelectModelValue | undefined,
  multiple: boolean,
): SelectOption[] {
  if (model === undefined) {
    return []
  }
  const values = multiple
    ? (Array.isArray(model) ? model : [model])
    : (Array.isArray(model) ? [] : [model])
  const available = flattenSelectOptions(options)
  const selection: SelectOption[] = []
  for (const value of values) {
    if (selection.some(option => isEqual(option.value, value))) {
      continue
    }
    const known = available.find(option => isEqual(option.value, value))
    if (known) {
      selection.push(known)
    }
    else if (value !== null && (multiple || typeof value === 'object')) {
      // 搜索或暂时缺失的候选不能让已有多选 ID 消失，也不能误选第一项。
      selection.push({
        value,
        label: typeof value === 'object' ? objectLabel(value) : String(value) || '已选项',
      })
    }
  }
  return selection
}

export function mapSelectValueToInternal(model: undefined): undefined
export function mapSelectValueToInternal(model: AcceptableValue[]): AcceptableValue[]
export function mapSelectValueToInternal(model: AcceptableValue): AcceptableValue
export function mapSelectValueToInternal(model: SelectModelValue | undefined): SelectModelValue | undefined
export function mapSelectValueToInternal(model: SelectModelValue | undefined): SelectModelValue | undefined {
  return Array.isArray(model)
    ? model.map(value => value === '' ? EMPTY_STRING_ITEM_VALUE : value)
    : model === '' ? EMPTY_STRING_ITEM_VALUE : model
}

export function mapSelectValueFromInternal(model: undefined): undefined
export function mapSelectValueFromInternal(model: AcceptableValue[]): AcceptableValue[]
export function mapSelectValueFromInternal(model: AcceptableValue): AcceptableValue
export function mapSelectValueFromInternal(model: SelectModelValue | undefined): SelectModelValue | undefined
export function mapSelectValueFromInternal(model: SelectModelValue | undefined): SelectModelValue | undefined {
  return Array.isArray(model)
    ? model.map(value => value === EMPTY_STRING_ITEM_VALUE ? '' : value)
    : model === EMPTY_STRING_ITEM_VALUE ? '' : model
}

function valueSearchText(value: AcceptableValue): string {
  if (value === null) {
    return ''
  }
  if (typeof value !== 'object') {
    return String(value)
  }
  try {
    return JSON.stringify(value, (_key, part) => typeof part === 'bigint' ? String(part) : part) || ''
  }
  catch {
    return typeof value.label === 'string' ? value.label : ''
  }
}

export function filterSelectOptions(options: SelectOptions, query: string): (SelectOption | SelectGroupOption)[] {
  const keyword = query.trim().toLocaleLowerCase()
  if (!keyword) {
    return [...options]
  }
  const matches = (option: SelectOption) => option.label.toLocaleLowerCase().includes(keyword)
    || valueSearchText(option.value).toLocaleLowerCase().includes(keyword)
  return options.flatMap((option): (SelectOption | SelectGroupOption)[] => {
    if (!isGroup(option)) {
      return matches(option) ? [option] : []
    }
    if (option.label.toLocaleLowerCase().includes(keyword)) {
      return [option]
    }
    const filtered = option.options.filter(matches)
    return filtered.length ? [{ ...option, options: filtered }] : []
  })
}
