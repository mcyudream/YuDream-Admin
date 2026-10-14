import type { ComponentPublicInstance } from 'vue'
import type { AiFrontendAction } from '@/api/modules/platform-ai-runs'
import { computed, onActivated, onBeforeUnmount, onDeactivated, shallowReactive } from 'vue'
import { isSafeAiActionCode } from '@/utils/ai-assistant-context'

interface RegisteredAiAction extends AiFrontendAction {
  element: HTMLElement
  permission: string
}

const actions = shallowReactive(new Map<string, RegisteredAiAction>())

export function registerAiAssistantAction(action: AiFrontendAction & { permission: string }, element: HTMLElement): () => void {
  if (!isSafeAiActionCode(action.code) || !action.label.trim() || !action.routeName.trim() || action.routeName.length > 180 || !action.permission.trim()) {
    throw new Error('AI 页面操作描述无效')
  }
  if (!(element instanceof HTMLElement) || element.ownerDocument !== document) {
    throw new Error('AI 页面操作必须绑定当前文档中的真实元素')
  }
  const registered: RegisteredAiAction = { ...action, label: action.label.trim().slice(0, 80), element }
  actions.set(action.code, registered)
  return () => {
    if (actions.get(action.code) === registered) {
      actions.delete(action.code)
    }
  }
}

export function disposePluginAiActions(pluginCode: string) {
  const prefix = `plugin:${pluginCode}:`
  for (const code of actions.keys()) {
    if (code.startsWith(prefix)) {
      actions.delete(code)
    }
  }
}

/** Page-owned refs are the only highlight targets. Registration never grants execution/click privileges. */
export function useAiAssistantAction(code: string, label: string, permission: string) {
  if (!/^[a-z][a-z0-9:.-]{1,119}$/.test(code)) {
    throw new Error('AI 操作标识无效')
  }
  const route = useRoute()
  let element: HTMLElement | undefined
  let unregister: (() => void) | undefined
  let active = true
  const register = () => {
    if (!active || !element || typeof route.name !== 'string') {
      return
    }
    unregister = registerAiAssistantAction({ code, label, routeName: route.name, permission }, element)
  }
  const remove = () => {
    unregister?.()
    unregister = undefined
  }
  onActivated(() => {
    active = true
    register()
  })
  onDeactivated(() => {
    active = false
    remove()
  })
  onBeforeUnmount(remove)
  return (value: Element | ComponentPublicInstance | null) => {
    remove()
    const candidate = value && '$el' in value ? value.$el : value
    element = candidate instanceof HTMLElement ? candidate : undefined
    register()
  }
}

export function useAiAssistantActions() {
  const route = useRoute()
  const { auth } = useAppAuth()
  const visible = computed(() => [...actions.values()].filter(action => action.routeName === route.name && auth(action.permission) && action.element.isConnected))
  const catalog = computed<AiFrontendAction[]>(() => visible.value.map(({ code, label, routeName }) => ({ code, label, routeName })))
  function highlight(code: string): AiFrontendAction {
    const action = visible.value.find(item => item.code === code)
    if (!action || action.element.getClientRects().length === 0 || action.element.hasAttribute('disabled') || action.element.getAttribute('aria-disabled') === 'true') {
      throw new Error('当前页面操作不可用或没有权限')
    }
    const element = action.element
    element.scrollIntoView({ block: 'center', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
    element.focus({ preventScroll: true })
    element.classList.add('yd-ai-assistant-highlight')
    window.setTimeout(() => element.classList.remove('yd-ai-assistant-highlight'), 4000)
    return { code: action.code, label: action.label, routeName: action.routeName }
  }
  return { catalog, highlight }
}
