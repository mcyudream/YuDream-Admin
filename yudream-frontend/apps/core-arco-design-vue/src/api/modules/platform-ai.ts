import type { AgentApplication, AgentModelOption } from './platform-agent'
import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export default {
  /** CMS/AI 场景的可用 Agent 列表（platform:ai:generate 权限，不走 Agent 管理台权限） */
  availableAgents: () => {
    return systemClient.get<unknown, ApiResponse<AgentApplication[]>>('api/platform/ai/agents/available')
  },
  /** CMS/AI 场景的可用模型清单（provider-first，platform:ai:generate 权限） */
  availableModels: () => {
    return systemClient.get<unknown, ApiResponse<AgentModelOption[]>>('api/platform/ai/models/available', { skipGlobalToast: true })
  },
}
