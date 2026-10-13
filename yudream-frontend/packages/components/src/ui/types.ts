import type { CSSProperties, HTMLAttributes } from 'vue'

export type YdUiSize = 'mini' | 'small' | 'medium' | 'large'
export type YdFormValidateStatus = 'success' | 'warning' | 'error' | 'validating'
export type YdFormValidateTrigger = 'change' | 'input' | 'focus' | 'blur'

/** Stable validation contract; callers never import the underlying form engine. */
export interface YdFormRule<FieldValue = unknown> {
  type?: 'string' | 'number' | 'boolean' | 'array' | 'object' | 'email' | 'url' | 'ip'
  required?: boolean
  message?: string
  length?: number
  maxLength?: number
  minLength?: number
  match?: RegExp
  uppercase?: boolean
  lowercase?: boolean
  min?: number
  max?: number
  equal?: number
  positive?: boolean
  negative?: boolean
  true?: boolean
  false?: boolean
  includes?: unknown[]
  deepEqual?: unknown
  empty?: boolean
  hasKeys?: string[]
  validator?: (value: FieldValue | undefined, callback: (error?: string) => void) => void
}

export interface YdFormError {
  label: string
  field: string
  value: unknown
  type: string
  isRequiredError: boolean
  message: string
}
export type YdFormErrors = Record<string, YdFormError>
export interface YdFormFieldData {
  value?: unknown
  status?: YdFormValidateStatus
  message?: string
}
export interface YdFormSubmitData {
  values: Record<string, unknown>
  errors: YdFormErrors | undefined
}
export interface YdFormInstance {
  validate: (callback?: (errors: YdFormErrors | undefined) => void) => Promise<YdFormErrors | undefined>
  validateField: (field: string | string[], callback?: (errors: YdFormErrors | undefined) => void) => Promise<YdFormErrors | undefined>
  resetFields: (field?: string | string[]) => void
  clearValidate: (field?: string | string[]) => void
  setFields: (data: Record<string, YdFormFieldData>) => void
  scrollToField: (field: string) => void
}
export interface YdFormColumn {
  span?: number
  offset?: number
  flex?: string | number
}
export interface YdFormProps {
  model: Record<string, any>
  rules?: Record<string, YdFormRule<any> | YdFormRule<any>[]>
  layout?: 'horizontal' | 'vertical' | 'inline'
  size?: YdUiSize
  disabled?: boolean
  id?: string
  labelAlign?: 'left' | 'right'
  labelColProps?: YdFormColumn
  wrapperColProps?: YdFormColumn
  labelColStyle?: CSSProperties
  wrapperColStyle?: CSSProperties
  autoLabelWidth?: boolean
  scrollToFirstError?: boolean
  class?: HTMLAttributes['class']
}
export interface YdFormItemProps {
  field?: string
  label?: string
  /** Explicit control id when the field contains custom nested content. */
  controlId?: string
  tooltip?: string
  showColon?: boolean
  noStyle?: boolean
  disabled?: boolean
  help?: string
  extra?: string
  required?: boolean
  rules?: YdFormRule<any> | YdFormRule<any>[]
  validateStatus?: YdFormValidateStatus
  validateTrigger?: YdFormValidateTrigger | YdFormValidateTrigger[]
  labelColProps?: YdFormColumn
  wrapperColProps?: YdFormColumn
  labelColStyle?: CSSProperties
  wrapperColStyle?: CSSProperties
  labelColFlex?: string | number
  hideLabel?: boolean
  hideAsterisk?: boolean
  asteriskPosition?: 'start' | 'end'
  contentFlex?: boolean
  feedback?: boolean
  class?: HTMLAttributes['class']
}
export interface YdFormFieldBindings {
  'id': string
  'disabled': boolean
  'aria-invalid': boolean
  'aria-describedby': string
  'onUpdate:modelValue': () => void
  'onInput': () => void
  'onChange': () => void
  'onBlur': () => void
  'onFocus': () => void
}
export interface YdInputTagItem {
  value: string | number
  label?: string
  closable?: boolean
}
export interface YdFilePickerInstance {
  open: () => void
  reset: () => void
  focus: () => void
  blur: () => void
  element: Readonly<import('vue').ShallowRef<HTMLInputElement | null>>
}
export type YdInputTagValue = string | number | YdInputTagItem
export interface YdInputTagInstance {
  focus: () => void
  blur: () => void
}
