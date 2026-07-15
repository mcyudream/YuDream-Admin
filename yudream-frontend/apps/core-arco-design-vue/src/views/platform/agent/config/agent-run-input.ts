const INLINE_TEXT_LIMIT = 256 * 1024

interface TextAttachment {
  name: string
  type: string
  size: number
  text?: string
}

export function agentRunCapabilities(workflowJson: string) {
  try {
    const workflow = JSON.parse(workflowJson || '{}') as { nodes?: Array<{ data?: Record<string, unknown> }> }
    const data = (workflow.nodes || []).map(node => node.data || {})
    return {
      allowImage: data.some(node => node.kind === 'llm' && node.vision === true),
      allowFiles: data.some(node => node.kind === 'document' || (node.kind === 'llm' && node.acceptFiles === true)),
    }
  }
  catch {
    return { allowImage: false, allowFiles: false }
  }
}

export function buildAgentRunInput(input: string, attachments: TextAttachment[]) {
  const text = attachments
    .filter(item => item.text && item.size <= INLINE_TEXT_LIMIT)
    .map(item => `附件 ${item.name}:\n${item.text}`)
    .join('\n\n')
  return [input.trim(), text].filter(Boolean).join('\n\n')
}
