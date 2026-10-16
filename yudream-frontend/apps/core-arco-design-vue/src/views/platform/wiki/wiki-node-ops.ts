import type { ApiResponse } from '@/api/modules/system-client'
import systemClient from '@/api/modules/system-client'

/**
 * Wiki 节点移动：本文件是 Wiki 目录内的本地封装，不动共享 `platform-wiki.ts`。
 *
 * 已核对的真实后端契约（yudream-interfaces WikiController + WikiAppService.moveNode）：
 * - 路由：类级 `@RequestMapping("/api/platform/wiki")` + `@PostMapping("/nodes/{id}/move")`
 *   → `POST /api/platform/wiki/nodes/{id}/move`
 * - 请求体：`WikiNodeMoveRequest { String parentId }`；null 或空白串表示移到根节点，
 *   非空时服务端执行 `Long.valueOf(parentId)`，所以必须传数字字符串（Snowflake ID 走字符串，禁止 Number(id)）
 * - 权限：`platform:wiki:edit`
 * - 语义：目标父节点必须存在且属于同一知识库（否则「父节点不存在或不属于当前知识库」）；
 *   父节点必须能容纳该类型（只有 DIRECTORY 能包含子节点，页面不能）；
 *   整棵受影响子树重算路径，已发布节点会同步索引路径；节点不能移动到自身/自身子树
 * - 响应：`Result<Void>`
 *
 * 建议后续在共享 API 模块补上并删除本文件（签名与上面语义一致）：
 *   export const moveWikiNode = (id: string, parentId?: string) =>
 *     systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}/move`, { parentId: parentId ?? null })
 */
export function moveWikiNode(id: string, parentId: string | null) {
  return systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}/move`, { parentId })
}
