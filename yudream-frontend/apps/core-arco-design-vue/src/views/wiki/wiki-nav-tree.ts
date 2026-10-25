import type { WikiNode } from '@/api/modules/platform-wiki'

/**
 * 公开阅读侧栏的展示分组（纯函数）。
 *
 * 摄取管道为每份原文档建一个 path=source-{sourceId} 的镜像页，且早期生成的页面散落在
 * 顶层而不是「LLM 生成」目录里——按数据直出会让原始资料占据首屏、生成内容却散落各处。
 * 这里统一为：原文档镜像从树中摘出（并入侧栏底部「原文档」区，与原文档条目同一入口），
 * 其余顶层页面收进「LLM 生成」组（与同名目录合并），让生成的可读内容成为第一展示目标。
 */

/** 原文档镜像页路径：摄取管道按 source-{sourceId} 命名 */
const MIRROR_PATH = /^source-\d+$/

export interface WikiNavTreeGroup {
  /** 收纳全部生成内容的目录节点（树中已有同名目录时在其基础上合并顶层散页） */
  group: WikiNode
  /** 其余顶层目录（非 LLM 生成目录的原有层级） */
  rest: WikiNode[]
  /** 被摘出的原文档镜像页（与原文档条目同源，仅在没有原文档条目时兜底展示） */
  mirrors: WikiNode[]
}

export function groupWikiNavTree(tree: WikiNode[]): WikiNavTreeGroup {
  const mirrors: WikiNode[] = []
  const folders: WikiNode[] = []
  const pages: WikiNode[] = []
  for (const node of tree) {
    if (node.nodeType === 'PAGE' && MIRROR_PATH.test(node.path || node.slug || '')) {
      mirrors.push(node)
    }
    else if (node.nodeType === 'DIRECTORY') {
      folders.push(node)
    }
    else {
      pages.push(node)
    }
  }
  const llmFolder = folders.find(node => (node.title || '').trim() === 'LLM 生成')
  const rest = folders.filter(node => node !== llmFolder)
  let group: WikiNode
  if (llmFolder) {
    group = { ...llmFolder, children: [...(llmFolder.children || []), ...pages] }
  }
  else if (pages.length) {
    group = {
      id: 'llm-generated-group',
      title: 'LLM 生成',
      slug: 'llm-gen',
      path: 'llm-gen',
      nodeType: 'DIRECTORY',
      sort: 0,
      indexStatus: 'INDEXED',
      children: [...pages],
    }
  }
  else if (folders.length) {
    // 没有任何顶层页面时不造空组：目录原样保留（此时页面散落也无从谈起）
    const [first, ...others] = folders
    group = { ...first, children: [...(first.children || [])] }
    rest.length = 0
    rest.push(...others)
  }
  else {
    // 树为空（接口尚未返回/空间无内容）：无组可展示
    group = {
      id: 'llm-generated-empty',
      title: 'LLM 生成',
      slug: 'llm-gen',
      path: 'llm-gen',
      nodeType: 'DIRECTORY',
      sort: 0,
      indexStatus: 'INDEXED',
      children: [],
    }
  }
  return { group, rest, mirrors }
}
