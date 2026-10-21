import type { ExcelImportResult } from './excel'

export type ExcelImportFeedbackKind = 'success' | 'warning' | 'error'

export interface TemplateImportFeedback extends ExcelImportResult {
  created?: number
  updated?: number
  skipped?: number
  warnings?: string[]
}

/** 模板导入反馈必须如实表达部分失败，不能因为请求成功就把失败行伪装成成功。 */
export function templateImportFeedbackKind(result: TemplateImportFeedback): ExcelImportFeedbackKind {
  if (result.failed > 0) {
    return 'error'
  }
  return result.warnings?.length ? 'warning' : 'success'
}
