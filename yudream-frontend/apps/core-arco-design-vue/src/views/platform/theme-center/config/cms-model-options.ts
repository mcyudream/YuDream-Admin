import type { AgentModelOption } from '@/api/modules/platform-agent'

/** CMS 助手模型选择项：value 形如 `providerCode/modelCode` */
export interface CmsModelSelectOption {
  label: string
  value: string
  providerCode: string
  modelCode: string
  vision: boolean
  defaultModel: boolean
}

export function toCmsModelOptions(models: AgentModelOption[]): CmsModelSelectOption[] {
  return models
    .filter(model => model.configured && model.kind === 'chat')
    .map(model => ({
      label: `${model.providerName || model.providerCode} · ${model.modelName || model.modelCode}`,
      value: `${model.providerCode}/${model.modelCode}`,
      providerCode: model.providerCode,
      modelCode: model.modelCode,
      vision: Boolean(model.vision),
      defaultModel: Boolean(model.defaultModel),
    }))
    .sort((left, right) => Number(right.defaultModel) - Number(left.defaultModel))
}

/** 解析当前选中值：失效时回退默认模型，再回退第一项 */
export function resolveCmsModelValue(options: CmsModelSelectOption[], current: string): string {
  if (options.some(option => option.value === current)) {
    return current
  }
  return options.find(option => option.defaultModel)?.value || options[0]?.value || ''
}

export function findCmsModelOption(options: CmsModelSelectOption[], value: string): CmsModelSelectOption | null {
  return options.find(option => option.value === value) || null
}
