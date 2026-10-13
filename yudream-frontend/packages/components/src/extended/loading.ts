import type { YdLoadingType } from './YdLoading.vue'
import { createVNode, getCurrentInstance, onScopeDispose, render } from 'vue'
import YdLoading from './YdLoading.vue'

export function useYdLoading(options: {
  type?: YdLoadingType
  size?: number
  text?: string
} = {}) {
  if (typeof document === 'undefined') {
    return { close() {
    } }
  }
  const container = document.createElement('div')
  const instance = getCurrentInstance()
  const node = createVNode(YdLoading, { ...options, fullscreen: true })
  if (instance) {
    node.appContext = instance.appContext
  }
  document.body.append(container)
  render(node, container)
  let closed = false
  function close() {
    if (closed) {
      return
    }
    ;
    closed = true
    render(null, container)
    container.remove()
  }
  if (instance) {
    onScopeDispose(close)
  }
  return { close }
}
