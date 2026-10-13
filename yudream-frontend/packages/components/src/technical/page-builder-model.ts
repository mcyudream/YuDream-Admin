export interface YdPageBuilderInput {
  htmlContent?: string
  cssContent?: string
  builderProjectJson?: string
}

export type YdPageBuilderLoad
  = | { kind: 'project', project: Record<string, unknown> }
    | {
      kind: 'html'
      htmlContent: string
      cssContent: string
      projectError?: Error
    }

export function resolveYdPageBuilderLoad(input: YdPageBuilderInput): YdPageBuilderLoad {
  const projectJson = input.builderProjectJson?.trim() || ''
  if (projectJson) {
    try {
      const value: unknown = JSON.parse(projectJson)
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        return { kind: 'project', project: value as Record<string, unknown> }
      }
      return { kind: 'html', htmlContent: input.htmlContent || '', cssContent: input.cssContent || '', projectError: new Error('构建器项目数据必须是 JSON object') }
    }
    catch (cause) {
      return { kind: 'html', htmlContent: input.htmlContent || '', cssContent: input.cssContent || '', projectError: cause instanceof Error ? cause : new Error('构建器项目数据解析失败') }
    }
  }
  return { kind: 'html', htmlContent: input.htmlContent || '', cssContent: input.cssContent || '' }
}

export function sameYdPageBuilderSnapshot(left: YdPageBuilderInput, right: YdPageBuilderInput): boolean {
  return (left.htmlContent || '') === (right.htmlContent || '')
    && (left.cssContent || '') === (right.cssContent || '')
    && (left.builderProjectJson || '') === (right.builderProjectJson || '')
}

export function resolveYdPageBuilderChangeMode(
  next: YdPageBuilderInput,
  previous: YdPageBuilderInput,
  lastEmitted: YdPageBuilderInput | null,
): 'ignore' | 'project' | 'html' {
  if (lastEmitted && sameYdPageBuilderSnapshot(next, lastEmitted)) {
    return 'ignore'
  }
  if ((next.builderProjectJson || '') !== (previous.builderProjectJson || '') && (next.builderProjectJson || '').trim()) {
    return 'project'
  }
  return 'html'
}
