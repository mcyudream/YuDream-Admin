import type { YdCodeEditorLanguage, YdTreeNode } from '@yudream/components'
import type { ChatWorkspaceEntry } from '@/api/modules/chat-workspace'

/** 工作区文件管理器的纯函数：目录树、编辑器语言、路径与大小格式。 */

export interface WorkspaceTreeNode extends YdTreeNode {
  entry: ChatWorkspaceEntry
  children?: WorkspaceTreeNode[]
}

export function workspaceName(path: string) {
  return path.slice(path.lastIndexOf('/') + 1)
}

export function workspaceParent(path: string) {
  const slash = path.lastIndexOf('/')
  return slash < 0 ? '' : path.slice(0, slash)
}

export function workspaceJoin(directory: string, name: string) {
  return directory ? `${directory}/${name}` : name
}

/** 目录在前、按名称自然排序；条目按路径挂到父目录下（父目录缺失时挂根） */
export function buildWorkspaceTree(entries: ChatWorkspaceEntry[]): WorkspaceTreeNode[] {
  const nodes = new Map<string, WorkspaceTreeNode>()
  for (const entry of entries) {
    nodes.set(entry.path, { key: entry.path, label: workspaceName(entry.path), entry, ...(entry.kind === 'DIRECTORY' ? { children: [] } : { isLeaf: true }) })
  }
  const roots: WorkspaceTreeNode[] = []
  for (const node of nodes.values()) {
    const parent = nodes.get(workspaceParent(node.entry.path))
    if (parent?.children) {
      parent.children.push(node)
    }
    else {
      roots.push(node)
    }
  }
  const collator = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' })
  const sort = (items: WorkspaceTreeNode[]) => {
    items.sort((a, b) => Number(b.entry.kind === 'DIRECTORY') - Number(a.entry.kind === 'DIRECTORY') || collator.compare(a.label, b.label))
    items.forEach(item => item.children && sort(item.children))
    return items
  }
  return sort(roots)
}

const LANGUAGES: Record<string, YdCodeEditorLanguage> = {
  json: 'json',
  jsonl: 'json',
  js: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  ts: 'javascript',
  tsx: 'javascript',
  jsx: 'javascript',
  vue: 'html',
  html: 'html',
  htm: 'html',
  xml: 'html',
  svg: 'html',
  css: 'css',
  scss: 'css',
  less: 'css',
}

export function workspaceEditorLanguage(path: string): YdCodeEditorLanguage {
  const name = workspaceName(path)
  const extension = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : ''
  return LANGUAGES[extension] ?? 'text'
}

export function workspaceIsImage(entry: Pick<ChatWorkspaceEntry, 'path' | 'contentType'>) {
  return /^image\/(?:png|jpe?g|gif|webp|svg\+xml|bmp)$/.test(entry.contentType ?? '')
    || /\.(?:png|jpe?g|gif|webp|bmp)$/i.test(entry.path)
}

export function formatWorkspaceBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value >= 100 ? Math.round(value) : value.toFixed(1)} ${units[unit]}`
}

/** 新名称校验：返回错误提示，合法时返回空串 */
export function workspaceNameError(name: string) {
  const value = name.trim()
  if (!value) {
    return '名称不能为空'
  }
  if (value === '.' || value === '..' || value.includes('\\') || [...value].some(char => char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127)) {
    return '名称不能是 . 或 ..，也不能包含反斜杠或控制字符'
  }
  return ''
}
