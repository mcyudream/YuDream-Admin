import type { VNode } from 'vue'
import type { YdFormFieldBindings } from './types'
import { cloneVNode, createVNode, Fragment } from 'vue'

/** Preserve caller handlers and props while attaching field state to direct YD controls. */
export function bindYdFormControls(children: VNode[], bindings: YdFormFieldBindings): VNode[] {
  let assignedId = false
  const attach = (nodes: VNode[]): VNode[] => nodes.map((node) => {
    if (node.type === Fragment && Array.isArray(node.children)) {
      // Slot blocks point at the original children; cloned field state must patch too.
      return createVNode(Fragment, { ...node.props, key: node.key ?? undefined }, attach(node.children as VNode[]))
    }
    const name = typeof node.type === 'object' && 'name' in node.type ? node.type.name : ''
    if (!name?.startsWith('Yd')) {
      return node
    }
    const extra = { ...bindings, id: assignedId ? undefined : bindings.id }
    assignedId = true
    return cloneVNode(node, { ...extra, ...(node.props?.disabled === undefined ? {} : { disabled: node.props.disabled }), ...(node.props?.id ? { id: node.props.id } : {}) }, true)
  })
  return attach(children)
}
