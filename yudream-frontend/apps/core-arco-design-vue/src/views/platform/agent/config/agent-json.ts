export const defaultAgentToolInputSchema = JSON.stringify({
  type: 'object',
  properties: {
    name: { type: 'string', description: '名称' },
  },
  required: ['name'],
}, null, 2)

export const defaultAgentToolOutputExample = JSON.stringify({
  success: true,
  message: 'Hello, YuDream',
}, null, 2)

export function isJsonObject(value: string) {
  try {
    const parsed = JSON.parse(value)
    return parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
  }
  catch {
    return false
  }
}

export function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  }
  catch {
    return value
  }
}
