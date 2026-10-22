import type { YdAgentApproval } from '@yudream/components'

interface NativeInteraction {
  requestId: string
  kind?: string
  toolName?: string
  arguments?: Record<string, unknown>
}

const PREVIEW_CHARS = 600

/**
 * Harness 原生确认（写入风险工具）→ 工作台待批准卡片：工作区写入/删除给出人能核对的说明（路径、大小、内容预览），
 * 其他工具保留参数 JSON。只读展示，批准与否仍由用户逐次决定。
 */
export function aiApprovalItems(interactions: NativeInteraction[] | undefined): YdAgentApproval[] {
  return (interactions || []).filter(item => item.kind === 'confirmation').map((item) => {
    const args = item.arguments || {}
    const path = typeof args.path === 'string' ? args.path : ''
    if (item.toolName === 'workspace.write' && typeof args.content === 'string') {
      const content = args.content
      const lines = content.split('\n').length
      return {
        id: item.requestId,
        title: `写入工作区文件：${path}`,
        description: `${lines} 行 · ${content.length} 字。批准后创建或覆盖该文件。`,
        command: content.length > PREVIEW_CHARS ? `${content.slice(0, PREVIEW_CHARS)}\n…（其余 ${content.length - PREVIEW_CHARS} 字）` : content,
        scope: '仅我的工作区，仅本次写入',
      }
    }
    if (item.toolName === 'workspace.delete') {
      return { id: item.requestId, title: `删除工作区文件：${path}`, description: '批准后移入回收站，30 天内可在文件管理器中恢复。', scope: '仅我的工作区，仅本次删除' }
    }
    if (item.toolName === 'workspace.move') {
      return { id: item.requestId, title: `移动工作区文件：${String(args.from ?? '')} → ${String(args.to ?? '')}`, description: '批准后移动或重命名（目录连同内容）。', scope: '仅我的工作区，仅本次移动' }
    }
    if (item.toolName === 'workspace.run') {
      const language = args.language === 'node' ? 'Node' : 'Python'
      const code = typeof args.code === 'string' ? args.code : ''
      const inputs = Array.isArray(args.inputs) ? args.inputs.map(String) : []
      const parts = [
        typeof args.entry === 'string' && args.entry ? `入口 ${args.entry}` : '直接运行下方代码',
        inputs.length ? `带入 ${inputs.join('、')}` : '',
        `产物写入 ${typeof args.outputDir === 'string' && args.outputDir ? args.outputDir : 'tmp/runs/（自动清理）'}`,
      ].filter(Boolean)
      return {
        id: item.requestId,
        title: `运行 ${language} 代码`,
        description: `${parts.join(' · ')}。在隔离运行器中执行（无外网、限时限内存），不会覆盖原文件。`,
        ...(code ? { command: code.length > PREVIEW_CHARS ? `${code.slice(0, PREVIEW_CHARS)}\n…（其余 ${code.length - PREVIEW_CHARS} 字）` : code } : {}),
        scope: '仅本次运行',
      }
    }
    return {
      id: item.requestId,
      title: `助手请求执行：${item.toolName || '工具操作'}`,
      description: JSON.stringify(args, null, 2),
      scope: '仅批准本次工具操作',
    }
  })
}
