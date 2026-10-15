/**
 * AI 端点供应商预设与默认图标。
 *
 * 图标取自宿主已离线打包的 Iconify `logos` 集合（品牌标志），缺失品牌回退 `morph:cloud`；
 * 与 LobeHub Icons 目录按 provider 取图标的方式一致，但不引入其 React 包或运行时 CDN。
 * 需要新增品牌时，先在离线图标集合中补齐对应图标再登记到本表，不要写无法离线渲染的名称。
 */

export type AiEndpointUsage = 'chat' | 'embedding' | 'rerank'
export type AiProtocol = 'ANTH' | 'CHAT' | 'RESPONSE' | 'EMBEDDING' | 'RERANK'

export interface AiProtocolOption {
  label: string
  value: AiProtocol
  /** 协议说明：告诉管理员该协议会走哪条线协议与默认路径。 */
  description: string
}

/** 聊天端点的三种协议；向量化/重排序端点的协议由用途确定。 */
export const AI_CHAT_PROTOCOL_OPTIONS: AiProtocolOption[] = [
  { label: 'OpenAI Chat Completions', value: 'CHAT', description: 'OpenAI 兼容接口，默认路径 /chat/completions' },
  { label: 'OpenAI Responses', value: 'RESPONSE', description: 'OpenAI Responses 接口，默认路径 /responses' },
  { label: 'Anthropic Messages', value: 'ANTH', description: 'Anthropic Messages 接口，基址填站点根地址' },
]

/** 端点分组展示顺序：聊天 → 向量化 → 重排序。 */
export const AI_USAGE_ORDER: AiEndpointUsage[] = ['chat', 'embedding', 'rerank']

export const AI_USAGE_LABELS: Record<AiEndpointUsage, string> = {
  chat: '聊天端点',
  embedding: '向量化端点',
  rerank: '重排序端点',
}

export const AI_USAGE_HINTS: Record<AiEndpointUsage, string> = {
  chat: '对话生成与工具调用；支持 Chat Completions、Responses、Anthropic Messages 三种协议。',
  embedding: '文本向量化，用于知识库检索与插件语义记忆；独立基址与凭据。',
  rerank: '检索结果重排序；独立基址与凭据，多数服务不提供模型列表接口。',
}

/** 各协议/用途的默认端点路径，与后端保持同一事实。 */
const DEFAULT_PATHS: Record<AiProtocol, string> = {
  CHAT: '/chat/completions',
  RESPONSE: '/responses',
  ANTH: '/v1/messages',
  EMBEDDING: '/embeddings',
  RERANK: '/rerank',
}

export function protocolDefaultPath(protocol: AiProtocol): string {
  return DEFAULT_PATHS[protocol] ?? DEFAULT_PATHS.CHAT
}

export function protocolOptionsFor(usage: AiEndpointUsage): AiProtocolOption[] {
  if (usage === 'embedding') {
    return [{ label: 'Embedding', value: 'EMBEDDING', description: '向量化接口，默认路径 /embeddings' }]
  }
  if (usage === 'rerank') {
    return [{ label: 'Rerank', value: 'RERANK', description: '重排序接口，默认路径 /rerank' }]
  }
  return AI_CHAT_PROTOCOL_OPTIONS
}

export interface AiProviderPreset {
  id: string
  label: string
  usage: AiEndpointUsage
  protocol: AiProtocol
  baseUrl: string
  /** 覆盖协议默认路径；留空表示使用协议默认值。 */
  path?: string
  icon: string
  defaultModel?: string
  models?: string[]
  /** 需要管理员补充的信息，例如 Azure 资源名。 */
  hint?: string
}

/**
 * 内置供应商/兼容服务预设：聊天、向量化、重排序按用途分组，各自带协议与基址默认值。
 * 预设只填充连接参数，不会预填或覆盖凭据。
 */
export const AI_PROVIDER_PRESETS: AiProviderPreset[] = [
  // ---------------- 聊天端点 ----------------
  {
    id: 'openai',
    label: 'OpenAI',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.openai.com/v1',
    icon: 'logos:openai',
    defaultModel: 'gpt-4o-mini',
  },
  {
    id: 'openai-responses',
    label: 'OpenAI（Responses）',
    usage: 'chat',
    protocol: 'RESPONSE',
    baseUrl: 'https://api.openai.com/v1',
    icon: 'logos:openai',
  },
  {
    id: 'anthropic',
    label: 'Anthropic Claude',
    usage: 'chat',
    protocol: 'ANTH',
    baseUrl: 'https://api.anthropic.com',
    icon: 'logos:anthropic',
  },
  {
    id: 'kimi',
    label: 'Kimi（Moonshot）',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.moonshot.cn/v1',
    icon: 'logos:moonshot-ai',
    defaultModel: 'kimi-k2.6',
  },
  {
    id: 'deepseek',
    label: 'DeepSeek',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.deepseek.com',
    icon: 'logos:deepseek',
    defaultModel: 'deepseek-v4-pro',
  },
  {
    id: 'gemini',
    label: 'Google Gemini（兼容模式）',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    icon: 'logos:google-gemini',
  },
  {
    id: 'dashscope',
    label: '阿里云百炼（通义千问）',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    icon: 'logos:qwen',
  },
  {
    id: 'zhipu',
    label: '智谱 GLM',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    icon: 'morph:cloud',
  },
  {
    id: 'siliconflow',
    label: '硅基流动 SiliconFlow',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.siliconflow.cn/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'volcengine-ark',
    label: '火山方舟（豆包）',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    icon: 'morph:cloud',
  },
  {
    id: 'hunyuan',
    label: '腾讯混元',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'stepfun',
    label: '阶跃星辰 StepFun',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.stepfun.com/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'minimax',
    label: 'MiniMax',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.minimax.chat/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'xai',
    label: 'xAI Grok',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.x.ai/v1',
    icon: 'logos:grok',
  },
  {
    id: 'mistral',
    label: 'Mistral AI',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.mistral.ai/v1',
    icon: 'logos:mistral-ai',
  },
  {
    id: 'groq',
    label: 'Groq',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://api.groq.com/openai/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'openrouter',
    label: 'OpenRouter',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://openrouter.ai/api/v1',
    icon: 'logos:openrouter',
  },
  {
    id: 'azure-openai',
    label: 'Azure OpenAI',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'https://{resource}.openai.azure.com/openai/v1',
    icon: 'logos:microsoft-azure',
    hint: '把 {resource} 换成实际资源名，并按部署名登记模型',
  },
  {
    id: 'ollama',
    label: 'Ollama（本机）',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'http://127.0.0.1:11434/v1',
    icon: 'logos:ollama',
    hint: '本机/内网地址需保持可访问',
  },
  {
    id: 'vllm',
    label: 'vLLM / 自建兼容端点',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: 'http://127.0.0.1:8000/v1',
    icon: 'morph:cloud',
    hint: '填写自建 OpenAI 兼容服务的实际地址',
  },
  {
    id: 'custom-chat',
    label: '自定义聊天端点',
    usage: 'chat',
    protocol: 'CHAT',
    baseUrl: '',
    icon: 'morph:cloud',
  },

  // ---------------- 向量化端点 ----------------
  {
    id: 'openai-embedding',
    label: 'OpenAI 向量化',
    usage: 'embedding',
    protocol: 'EMBEDDING',
    baseUrl: 'https://api.openai.com/v1',
    icon: 'logos:openai',
    defaultModel: 'text-embedding-3-small',
  },
  {
    id: 'dashscope-embedding',
    label: '阿里云百炼向量化',
    usage: 'embedding',
    protocol: 'EMBEDDING',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    icon: 'logos:qwen',
  },
  {
    id: 'siliconflow-embedding',
    label: '硅基流动向量化',
    usage: 'embedding',
    protocol: 'EMBEDDING',
    baseUrl: 'https://api.siliconflow.cn/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'jina-embedding',
    label: 'Jina Embeddings',
    usage: 'embedding',
    protocol: 'EMBEDDING',
    baseUrl: 'https://api.jina.ai/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'ollama-embedding',
    label: 'Ollama 向量化（本机）',
    usage: 'embedding',
    protocol: 'EMBEDDING',
    baseUrl: 'http://127.0.0.1:11434/v1',
    icon: 'logos:ollama',
  },
  {
    id: 'custom-embedding',
    label: '自定义向量化端点',
    usage: 'embedding',
    protocol: 'EMBEDDING',
    baseUrl: '',
    icon: 'morph:cloud',
  },

  // ---------------- 重排序端点 ----------------
  {
    id: 'jina-rerank',
    label: 'Jina Rerank',
    usage: 'rerank',
    protocol: 'RERANK',
    baseUrl: 'https://api.jina.ai/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'siliconflow-rerank',
    label: '硅基流动重排序',
    usage: 'rerank',
    protocol: 'RERANK',
    baseUrl: 'https://api.siliconflow.cn/v1',
    icon: 'morph:cloud',
  },
  {
    id: 'cohere-rerank',
    label: 'Cohere Rerank',
    usage: 'rerank',
    protocol: 'RERANK',
    baseUrl: 'https://api.cohere.com/v2',
    icon: 'morph:cloud',
  },
  {
    id: 'custom-rerank',
    label: '自定义重排序端点',
    usage: 'rerank',
    protocol: 'RERANK',
    baseUrl: '',
    icon: 'morph:cloud',
  },
]

export function presetsFor(usage: AiEndpointUsage): AiProviderPreset[] {
  return AI_PROVIDER_PRESETS.filter(preset => preset.usage === usage)
}

export function findPreset(id: string | undefined): AiProviderPreset | undefined {
  return id ? AI_PROVIDER_PRESETS.find(preset => preset.id === id) : undefined
}

/** provider 编码 → 品牌图标；未登记品牌回退中性图标。 */
const PROVIDER_ICONS: Record<string, string> = {
  openai: 'logos:openai',
  azure: 'logos:microsoft-azure',
  'azure-openai': 'logos:microsoft-azure',
  anthropic: 'logos:anthropic',
  claude: 'logos:anthropic',
  google: 'logos:google-gemini',
  gemini: 'logos:google-gemini',
  deepseek: 'logos:deepseek',
  kimi: 'logos:moonshot-ai',
  moonshot: 'logos:moonshot-ai',
  qwen: 'logos:qwen',
  dashscope: 'logos:qwen',
  xai: 'logos:grok',
  grok: 'logos:grok',
  mistral: 'logos:mistral-ai',
  ollama: 'logos:ollama',
  openrouter: 'logos:openrouter',
  nvidia: 'logos:nvidia',
  perplexity: 'logos:perplexity',
  meta: 'logos:meta',
  huggingface: 'logos:hugging-face',
}

export function providerIcon(...candidates: (string | undefined)[]): string {
  for (const candidate of candidates) {
    if (!candidate) {
      continue
    }
    const key = candidate.trim().toLowerCase()
    if (PROVIDER_ICONS[key]) {
      return PROVIDER_ICONS[key]
    }
    if (key.startsWith('logos:') || key.startsWith('morph:') || key.startsWith('i-')) {
      return candidate
    }
  }
  return 'morph:cloud'
}
