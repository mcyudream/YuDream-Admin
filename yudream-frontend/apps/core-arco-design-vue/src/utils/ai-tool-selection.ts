import type { YdAgentSkill } from '@yudream/components'

/**
 * 对话中的工具 / 技能选择：工具模块默认不启用（与旧行为一致），技能默认全部可用（null）。
 * 只把当前仍可用的选项发给后端；后端按权限与开关再次过滤。
 */
export interface ChatToolOption {
  code: string
  name: string
  description: string
  icon: string
  source: 'system' | 'mcp' | string
  tools: string[]
}

export interface ChatToolOptions {
  modules: ChatToolOption[]
  skills: { name: string, description: string }[]
}

export interface ChatToolSelection {
  modules: string[]
  /** null 表示不限制技能 */
  skills: string[] | null
}

export const emptyToolSelection: ChatToolSelection = { modules: [], skills: null }

export function parseToolSelection(raw: unknown): ChatToolSelection {
  const value = raw && typeof raw === 'object' ? raw as Record<string, unknown> : {}
  const strings = (input: unknown) => Array.isArray(input) ? [...new Set(input.filter((item): item is string => typeof item === 'string' && item.length > 0 && item.length <= 120))].slice(0, 50) : []
  return { modules: strings(value.modules), skills: Array.isArray(value.skills) ? strings(value.skills) : null }
}

export function toggleSkill(selection: ChatToolSelection, allSkills: string[], name: string, enabled: boolean): ChatToolSelection {
  const current = selection.skills ?? allSkills
  const next = enabled ? [...new Set([...current, name])] : current.filter(item => item !== name)
  const everything = allSkills.length > 0 && allSkills.every(item => next.includes(item))
  return { ...selection, skills: everything ? null : next.filter(item => allSkills.includes(item)) }
}

export function skillItems(options: ChatToolOptions, selection: ChatToolSelection): YdAgentSkill[] {
  return options.skills.map(skill => ({ code: skill.name, name: skill.name, description: skill.description, enabled: selection.skills === null || selection.skills.includes(skill.name) }))
}

/** 发送载荷：只带当前可用的模块；技能未限制时不传 */
export function toolPayload(selection: ChatToolSelection, options: ChatToolOptions) {
  const available = new Set(options.modules.map(module => module.code))
  return {
    toolModules: selection.modules.filter(code => available.has(code)),
    ...(selection.skills === null ? {} : { skillCodes: selection.skills }),
  }
}
