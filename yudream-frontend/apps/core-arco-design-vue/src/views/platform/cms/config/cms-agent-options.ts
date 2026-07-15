export const BUILTIN_CMS_AGENT_CODE = 'builtin-cms-builder'

export interface CmsAgentSelectOption {
  label: string
  value: string
}

interface CmsAgentApplicationLike {
  code?: string
  name?: string
  status?: string
}

export function toCmsAgentOptions(applications: CmsAgentApplicationLike[]): CmsAgentSelectOption[] {
  return applications
    .filter(application => application.status === 'PUBLISHED')
    .map(application => ({
      label: String(application.name || application.code || '').trim(),
      value: String(application.code || '').trim(),
    }))
    .filter(option => Boolean(option.label && option.value))
}

export function resolveCmsAgentCode(options: CmsAgentSelectOption[], current: string): string {
  if (options.some(option => option.value === current)) {
    return current
  }
  return options.find(option => option.value === BUILTIN_CMS_AGENT_CODE)?.value || options[0]?.value || ''
}
