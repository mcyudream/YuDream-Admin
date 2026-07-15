import type { AgentNodeKind, AgentNodeTemplate } from '../components/types'

export interface AgentNodePaletteGroup {
  title: string
  items: AgentNodeTemplate[]
}

const inputNodes: AgentNodeTemplate[] = [
  { kind: 'start', label: '开始', icon: 'i-ri:play-circle-line', color: '#2563eb', description: '接收用户输入和运行参数', inputName: 'input', outputName: 'str.query' },
  { kind: 'input', label: '参数输入', icon: 'i-ri:login-box-line', color: '#2563eb', description: '声明业务参数或用户输入', inputName: 'request', outputName: 'str.query' },
  { kind: 'document', label: '文档解析', icon: 'i-ri:file-text-line', color: '#4f46e5', description: '从附件或变量提取正文和元数据', inputName: 'File', outputName: 'Document' },
]

const modelNodes: AgentNodeTemplate[] = [
  { kind: 'llm', label: '文本生成', icon: 'i-ri:sparkling-line', color: '#7c3aed', description: '使用聊天模型生成文本或 Markdown', inputName: 'str.query', outputName: 'str.answer' },
  { kind: 'extract', label: '结构化提取', icon: 'i-ri:braces-line', color: '#0f9488', description: '按 JSON Schema 提取结构化字段', inputName: 'str.query', outputName: 'json.data' },
  { kind: 'classify', label: '意图分类', icon: 'i-ri:price-tag-3-line', color: '#0f9488', description: '从预设分类中选择一个意图', inputName: 'str.query', outputName: 'json.classification' },
  { kind: 'vision', label: '视觉理解', icon: 'i-ri:image-ai-line', color: '#db2777', description: '使用视觉模型分析调试图片或变量图片', inputName: 'str.query + image', outputName: 'str.answer' },
  { kind: 'embedding', label: 'Embedding', icon: 'i-ri:focus-3-line', color: '#64748b', description: '将文本转换为向量表示', inputName: 'str.query', outputName: 'vector' },
  { kind: 'rerank', label: 'Rerank', icon: 'i-ri:sort-desc', color: '#d97706', description: '按问题相关性重排检索结果', inputName: 'Array<Document>', outputName: 'Array<Document>' },
]

const knowledgeNodes: AgentNodeTemplate[] = [
  { kind: 'search', label: '知识检索', icon: 'i-ri:book-open-line', color: '#7c3aed', description: '从知识空间检索相关内容', inputName: 'str.query', outputName: 'Array<Document>' },
  { kind: 'vector', label: '向量检索', icon: 'i-ri:bubble-chart-line', color: '#16a34a', description: '按向量相似度召回文档', inputName: 'vector', outputName: 'Array<Document>' },
  { kind: 'citation', label: '引用整理', icon: 'i-ri:double-quotes-l', color: '#0f9488', description: '整理答案的引用来源和片段', inputName: 'answer + documents', outputName: 'Array<Citation>' },
]

const logicNodes: AgentNodeTemplate[] = [
  { kind: 'condition', label: '条件分支', icon: 'i-ri:git-branch-line', color: '#0f9488', description: '根据表达式选择 true 或 false 分支', inputName: 'any', outputName: 'boolean' },
  { kind: 'code', label: 'Python 代码', icon: 'i-ri:code-s-slash-line', color: '#d97706', description: '执行确定性 Python 数据处理逻辑', inputName: 'any', outputName: 'any' },
]

const transformNodes: AgentNodeTemplate[] = [
  { kind: 'template', label: '模板转换', icon: 'i-ri:file-code-line', color: '#7c3aed', description: '通过模板拼接和转换运行上下文', inputName: 'json', outputName: 'str' },
]

const outputNodes: AgentNodeTemplate[] = [
  { kind: 'end', label: '结束', icon: 'i-ri:stop-circle-line', color: '#64748b', description: '汇总并输出最终运行结果', inputName: 'str.answer', outputName: 'result' },
]

export const agentNodePaletteGroups: AgentNodePaletteGroup[] = [
  { title: '输入', items: inputNodes },
  { title: '模型', items: modelNodes },
  { title: '知识', items: knowledgeNodes },
  { title: '逻辑', items: logicNodes },
  { title: '转换', items: transformNodes },
  { title: '输出', items: outputNodes },
]

export const agentNodeTemplates = agentNodePaletteGroups.flatMap(group => group.items)

export const agentLegacyNodeTemplates: AgentNodeTemplate[] = [
  { kind: 'understand', label: '问题理解', icon: 'i-ri:brain-line', color: '#0f9488', description: '历史工作流兼容节点', inputName: 'str.query', outputName: 'json.intent' },
  { kind: 'tool', label: '工具调用', icon: 'i-ri:tools-line', color: '#d97706', description: '历史工作流兼容节点', inputName: 'json', outputName: 'tool.result' },
]

const templatesByKind = new Map<AgentNodeKind, AgentNodeTemplate>()
for (const template of [...agentNodeTemplates, ...agentLegacyNodeTemplates]) {
  templatesByKind.set(template.kind, template)
}

export function findAgentNodeTemplate(kind: AgentNodeKind) {
  return templatesByKind.get(kind)
}
