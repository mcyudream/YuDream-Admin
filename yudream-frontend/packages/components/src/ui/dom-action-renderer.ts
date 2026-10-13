import type { Component, HTMLAttributes, Ref, VNodeChild } from 'vue'
import { getCurrentScope, h, onScopeDispose, render, unref } from 'vue'

export interface YdDomActionOptions {
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  ariaLabel: string
  title?: string
  class?: HTMLAttributes['class']
  pressed?: boolean
  text?: string
  icon?: string
  content?: () => VNodeChild
  onClick?: (event: MouseEvent) => void
}

export type YdDomActionUpdate = Partial<YdDomActionOptions>

export interface YdDomActionInstance {
  readonly element: HTMLButtonElement | null
  update: (options: YdDomActionUpdate) => void
  dispose: () => void
}

/** Internal renderer seam, kept private from the public package barrel for lifecycle tests. */
export function mountYdDomAction(
  container: Element,
  actionComponent: Component,
  iconComponent: Component,
  initial: YdDomActionOptions,
): YdDomActionInstance {
  const host = container.ownerDocument.createElement('span')
  host.setAttribute('data-yd-dom-action-host', '')
  host.style.display = 'contents'
  container.appendChild(host)

  let options: YdDomActionOptions = { ...initial, type: initial.type ?? 'button' }
  let button: HTMLButtonElement | null = null
  let disposed = false

  function renderAction(clickable = true) {
    const children: VNodeChild[] = []
    if (clickable) {
      if (options.icon) {
        children.push(h(iconComponent, { 'name': options.icon, 'aria-hidden': 'true' }))
      }
      if (options.text) {
        children.push(options.text)
      }
      if (options.content) {
        children.push(options.content())
      }
    }
    const vnode = h(actionComponent, {
      'type': options.type,
      'disabled': options.disabled,
      'title': options.title,
      'class': options.class,
      'aria-label': options.ariaLabel,
      'aria-pressed': options.pressed,
      'onClick': clickable ? (event: MouseEvent) => options.onClick?.(event) : undefined,
    }, { default: () => children })
    render(vnode, host)
    const exposed = vnode.component?.exposed as { element?: HTMLButtonElement | Ref<HTMLButtonElement | null> | null } | null
    button = unref(exposed?.element) ?? null
  }

  function dispose() {
    if (disposed) {
      return
    }
    disposed = true
    renderAction(false)
    render(null, host)
    host.parentNode?.removeChild(host)
    button = null
  }

  const instance: YdDomActionInstance = {
    get element() { return button },
    update(next) {
      if (disposed) {
        return
      }
      options = { ...options, ...next }
      renderAction()
    },
    dispose,
  }

  try {
    renderAction()
  }
  catch (error) {
    dispose()
    throw error
  }
  if (getCurrentScope()) {
    onScopeDispose(dispose)
  }
  return instance
}
