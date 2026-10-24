import type { AiFrontendContext, AiFrontendRoute, AiFrontendToolRequest } from '@/api/modules/platform-ai-runs'

export interface AiAssistantPreferences {
  routeAwareness: boolean
  pageAwareness: boolean
  navigationTools: boolean
  highlightTools: boolean
  tourTools: boolean
  shortcut: 'mod-shift-y' | 'alt-shift-a' | 'disabled'
  /** 工作区执行模式：按账号记住，恢复时仍需通过权限校验降级 */
  workspaceMode: 'ask' | 'plan' | 'code'
}

export const defaultAiAssistantPreferences: AiAssistantPreferences = {
  routeAwareness: false,
  pageAwareness: false,
  navigationTools: false,
  highlightTools: false,
  tourTools: false,
  shortcut: 'mod-shift-y',
  workspaceMode: 'ask',
}

export function parseAiAssistantPreferences(value: unknown): AiAssistantPreferences {
  const raw = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  return {
    routeAwareness: raw.routeAwareness === true,
    pageAwareness: raw.pageAwareness === true,
    navigationTools: raw.navigationTools === true,
    highlightTools: raw.highlightTools === true,
    tourTools: raw.tourTools === true,
    shortcut: raw.shortcut === 'alt-shift-a' || raw.shortcut === 'disabled' ? raw.shortcut : 'mod-shift-y',
    workspaceMode: raw.workspaceMode === 'plan' || raw.workspaceMode === 'code' ? raw.workspaceMode : 'ask',
  }
}

/** Catalog contains only static, permission-filtered menu pages. URL queries/IDs never become model context. */
export function isSafeAiRoute(route: AiFrontendRoute): boolean {
  return !!route.name && route.name.length <= 180 && !!route.title && route.title.length <= 100
    && /^\/(?!\/)[^:?#\\*]*$/.test(route.path)
    && !['/login', '/setup', '/oauth/authorize', '/verify-email'].includes(route.path)
}

/** Includes the host-added plugin namespace, which is longer than a plugin's local action code. */
export function isSafeAiActionCode(value: unknown): value is string {
  return typeof value === 'string' && /^[a-z][a-z0-9:._-]{1,179}$/.test(value)
}

export function parseAiFrontendToolRequest(value: unknown): AiFrontendToolRequest | undefined {
  if (!value || typeof value !== 'object') {
    return undefined
  }
  const raw = value as Record<string, unknown>
  if (typeof raw.toolCallId !== 'string' || !raw.toolCallId || raw.toolCallId.length > 200) {
    return undefined
  }
  // Must mirror CmsStudioClientTools.NAMES / STUDIO_AGENT_TOOLS exactly: a missing name is dropped
  // here and the run's native suspension then waits forever (cms.studio.theme once stalled every design run).
  const studioNames = ['cms.studio.outline', 'cms.studio.find', 'cms.studio.inspect', 'cms.studio.theme', 'cms.studio.guide', 'cms.studio.read', 'cms.studio.apply', 'cms.studio.validate', 'cms.studio.check'] as const
  if (studioNames.includes(raw.name as typeof studioNames[number])) {
    // Only exact AST tool names enter the registered executor. The executor validates command/model/context.
    if (!raw.arguments || typeof raw.arguments !== 'object' || Array.isArray(raw.arguments)) {
      return undefined
    }
    return {
      toolCallId: raw.toolCallId,
      name: raw.name as typeof studioNames[number],
      arguments: raw.arguments as Record<string, unknown>,
      expiresAt: typeof raw.expiresAt === 'number' ? raw.expiresAt : typeof raw.expiresAt === 'string' && Number.isFinite(Date.parse(raw.expiresAt)) ? Date.parse(raw.expiresAt) : undefined,
    }
  }
  if (raw.name !== 'ui.navigate' && raw.name !== 'ui.highlight' && raw.name !== 'ui.tour') {
    return undefined
  }
  if (!raw.arguments || typeof raw.arguments !== 'object' || Array.isArray(raw.arguments)) {
    return undefined
  }
  const args = raw.arguments as Record<string, unknown>
  // Reject arbitrary selectors, scripts, URL params and additional action payloads.
  if (Object.keys(args).some(key => key !== 'routeName' && key !== 'actionCode' && key !== 'tourCode')) {
    return undefined
  }
  const routeName = typeof args.routeName === 'string' ? args.routeName : undefined
  const actionCode = typeof args.actionCode === 'string' ? args.actionCode : undefined
  const tourCode = typeof args.tourCode === 'string' ? args.tourCode : undefined
  if (raw.name === 'ui.navigate' && (!routeName || routeName.length > 180)) {
    return undefined
  }
  if (raw.name === 'ui.highlight' && !isSafeAiActionCode(actionCode)) {
    return undefined
  }
  if (raw.name === 'ui.tour' && !isSafeAiActionCode(tourCode)) {
    return undefined
  }
  return {
    toolCallId: raw.toolCallId,
    name: raw.name,
    arguments: { ...(routeName ? { routeName } : {}), ...(actionCode ? { actionCode } : {}), ...(tourCode ? { tourCode } : {}) },
    expiresAt: typeof raw.expiresAt === 'number' ? raw.expiresAt : typeof raw.expiresAt === 'string' && Number.isFinite(Date.parse(raw.expiresAt)) ? Date.parse(raw.expiresAt) : undefined,
  }
}

export function frontendContextForPreferences(context: AiFrontendContext, preferences: AiAssistantPreferences): AiFrontendContext | undefined {
  const result: AiFrontendContext = {}
  if (preferences.routeAwareness && context.route?.name && context.route.name.length <= 180 && context.route.title && context.route.title.length <= 100) {
    result.route = context.route
  }
  if (preferences.navigationTools) {
    result.routes = context.routes?.filter(isSafeAiRoute) ?? []
  }
  if (preferences.pageAwareness || preferences.highlightTools) {
    result.actions = context.actions ?? []
  }
  if (preferences.tourTools && context.tours?.length) {
    result.tours = context.tours
  }
  return Object.keys(result).length
    ? {
        ...result,
        navigationToolsEnabled: preferences.navigationTools,
        highlightToolsEnabled: preferences.highlightTools,
        tourToolsEnabled: preferences.tourTools,
      }
    : undefined
}

export function matchesAiAssistantShortcut(event: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'shiftKey' | 'altKey' | 'isComposing' | 'repeat'>, shortcut: AiAssistantPreferences['shortcut']): boolean {
  if (event.isComposing || event.repeat || !event.shiftKey) {
    return false
  }
  if (shortcut === 'mod-shift-y') {
    return event.key.toLowerCase() === 'y' && (event.ctrlKey || event.metaKey) && !event.altKey
  }
  if (shortcut === 'alt-shift-a') {
    return event.key.toLowerCase() === 'a' && event.altKey && !event.ctrlKey && !event.metaKey
  }
  return false
}
