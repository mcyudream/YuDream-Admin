import type { VNode } from 'vue'
import type { YdFormFieldBindings } from './types'
import { cloneVNode, createVNode, Fragment } from 'vue'

export function isYdFormLabelFallbackTarget(element: (Pick<HTMLElement, 'tagName' | 'isContentEditable' | 'hasAttribute'> & { disabled?: boolean, getAttribute?: (name: string) => string | null }) | null | undefined): boolean {
  if (!element || element.disabled || element.getAttribute?.('aria-disabled') === 'true') {
    return false
  }
  const tagName = element.tagName.toLowerCase()
  const labelable = ['input', 'select', 'textarea', 'button', 'meter', 'progress', 'output'].includes(tagName)
  return !labelable && (element.isContentEditable || element.hasAttribute('tabindex'))
}

/** Preserve caller handlers and props while attaching field state to direct YD controls. */
export function bindYdFormControls(children: VNode[], bindings: YdFormFieldBindings): VNode[] {
  let assignedId = false
  const attach = (nodes: VNode[]): VNode[] => nodes.map((node) => {
    if (node.type === Fragment && Array.isArray(node.children)) {
      // Slot blocks point at the original children; cloned field state must patch too.
      return createVNode(Fragment, { ...node.props, key: node.key ?? undefined }, attach(node.children as VNode[]))
    }
    const name = typeof node.type === 'object' && '__name' in node.type
      ? node.type.__name
      : typeof node.type === 'object' && 'name' in node.type
        ? node.type.name
        : ''
    if (!name?.startsWith('Yd')) {
      return node
    }
    const extra = { ...bindings, id: assignedId ? undefined : bindings.id }
    assignedId = true
    return cloneVNode(node, { ...extra, ...(node.props?.disabled === undefined ? {} : { disabled: node.props.disabled }), ...(node.props?.id ? { id: node.props.id } : {}) }, true)
  })
  return attach(children)
}
