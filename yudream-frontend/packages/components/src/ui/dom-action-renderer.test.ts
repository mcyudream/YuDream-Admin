/* eslint-disable test/no-import-node-test -- 当前工作区使用 Node 原生测试，无 vitest 依赖。 */
import type { YdDomActionOptions } from './dom-action-renderer'
import assert from 'node:assert/strict'
import test from 'node:test'

class FakeText {
  readonly nodeType = 3
  parentNode: FakeElement | null = null
  nodeValue: string

  constructor(nodeValue: string) {
    this.nodeValue = nodeValue
  }

  get textContent() { return this.nodeValue }
  set textContent(value: string) { this.nodeValue = value }
}

class FakeElement {
  readonly nodeType = 1
  readonly tagName: string
  readonly children: Array<FakeElement | FakeText> = []
  readonly attributes = new Map<string, string>()
  readonly listeners = new Map<string, Set<EventListener>>()
  readonly style = { display: '' }
  parentNode: FakeElement | null = null
  className = ''
  type = ''
  title = ''
  disabled = false
  readonly ownerDocument: FakeDocument

  constructor(ownerDocument: FakeDocument, tagName: string) {
    this.ownerDocument = ownerDocument
    this.tagName = tagName.toUpperCase()
  }

  get nextSibling() {
    if (!this.parentNode) {
      return null
    }
    const siblings = this.parentNode.children
    return siblings[siblings.indexOf(this) + 1] ?? null
  }

  get textContent() { return this.children.map(child => child.textContent ?? '').join('') }
  set textContent(value: string) {
    this.children.forEach((child) => {
      child.parentNode = null
    })
    this.children.length = 0
    if (value) {
      this.insertBefore(this.ownerDocument.createTextNode(value), null)
    }
  }

  appendChild(child: FakeElement | FakeText) { return this.insertBefore(child, null) }
  insertBefore(child: FakeElement | FakeText, anchor: FakeElement | FakeText | null) {
    child.parentNode?.removeChild(child)
    const index = anchor ? this.children.indexOf(anchor) : -1
    if (index < 0) {
      this.children.push(child)
    }
    else { this.children.splice(index, 0, child) }
    child.parentNode = this
    return child
  }

  removeChild(child: FakeElement | FakeText) {
    const index = this.children.indexOf(child)
    if (index >= 0) {
      this.children.splice(index, 1)
    }
    child.parentNode = null
    return child
  }

  setAttribute(name: string, value: string) { this.attributes.set(name, String(value)) }
  removeAttribute(name: string) { this.attributes.delete(name) }
  getAttribute(name: string) { return this.attributes.get(name) ?? null }
  addEventListener(type: string, listener: EventListener) {
    const listeners = this.listeners.get(type) ?? new Set<EventListener>()
    listeners.add(listener)
    this.listeners.set(type, listeners)
  }

  removeEventListener(type: string, listener: EventListener) { this.listeners.get(type)?.delete(listener) }
  clickEvent() {
    const event = {
      type: 'click',
      target: this,
      currentTarget: this,
      preventDefault() {},
      stopPropagation() {},
    } as unknown as MouseEvent
    for (const listener of [...(this.listeners.get('click') ?? [])]) {
      listener.call(this, event)
    }
    return event
  }
}

class FakeDocument {
  createElement(tagName: string) { return new FakeElement(this, tagName) as unknown as HTMLElement }
  createTextNode(value: string) { return new FakeText(value) }
}

function installDocument() {
  const original = globalThis.document
  const document = new FakeDocument()
  globalThis.document = document as unknown as Document
  return {
    document,
    restore() { globalThis.document = original },
  }
}

async function loadActionHarness() {
  const vue = await import('vue')
  const rendererModulePath = './dom-action-renderer.ts'
  const { mountYdDomAction } = await import(rendererModulePath) as typeof import('./dom-action-renderer')
  const TestAction = vue.defineComponent({
    name: 'TestYdAction',
    inheritAttrs: false,
    setup(_props, { attrs, slots, expose }) {
      const element = vue.ref<HTMLButtonElement | null>(null)
      expose({
        get element() {
          return element.value
        },
      })
      return () => vue.h('button', { ...attrs, ref: element }, slots.default?.())
    },
  })
  const TestIcon = vue.defineComponent({
    name: 'TestYdIcon',
    props: { name: { type: String, required: true } },
    setup(props) { return () => vue.h('span', { 'data-icon': props.name, 'aria-hidden': 'true' }) },
  })
  return {
    effectScope: vue.effectScope,
    h: vue.h,
    mount: (container: Element, options: YdDomActionOptions) => mountYdDomAction(container, TestAction, TestIcon, options),
  }
}

test('renders a themed action, updates accessible state and content, and releases Vue listeners on dispose', async () => {
  const dom = installDocument()
  try {
    const { mount } = await loadActionHarness()
    const container = dom.document.createElement('div') as unknown as Element
    let clicks = 0
    let received: MouseEvent | undefined
    const action = mount(container, {
      ariaLabel: '切换音效',
      title: '像素音效开关',
      class: 'sound-toggle',
      pressed: true,
      text: '♪',
      onClick: (event) => {
        clicks++
        received = event
      },
    })
    const button = action.element as unknown as FakeElement
    assert.equal(button.tagName, 'BUTTON')
    assert.equal(button.type, 'button')
    assert.equal(button.getAttribute('aria-label'), '切换音效')
    assert.equal(button.getAttribute('aria-pressed'), 'true')
    assert.equal(button.className, 'sound-toggle')
    assert.equal(button.textContent, '♪')
    button.clickEvent()
    assert.equal(clicks, 1)
    assert.equal(received?.currentTarget, button)

    action.update({ disabled: true, pressed: false, text: '×', class: 'sound-toggle is-off' })
    assert.equal(action.element, button)
    assert.equal(button.disabled, true)
    assert.equal(button.getAttribute('aria-pressed'), 'false')
    assert.equal(button.className, 'sound-toggle is-off')
    assert.equal(button.textContent, '×')

    const host = container.children[0] as unknown as FakeElement
    action.dispose()
    action.dispose()
    assert.equal(host.parentNode, null)
    assert.equal(host.children.length, 0)
    button.clickEvent()
    assert.equal(clicks, 1)
    action.update({ text: 'ignored' })
    assert.equal(container.children.length, 0)
  }
  finally {
    dom.restore()
  }
})

test('supports themed graphical content and independently disposes multiple actions in one container', async () => {
  const dom = installDocument()
  try {
    const { h, mount } = await loadActionHarness()
    const container = dom.document.createElement('div') as unknown as Element
    const first = mount(container, {
      ariaLabel: '强调色',
      title: '强调色',
      class: 'palette-toggle',
      content: () => h('span', { 'class': 'palette-squares', 'aria-hidden': 'true' }, [
        h('span', { style: { background: '#3c8527' } }),
        h('span', { style: { background: '#b02e26' } }),
        h('span', { style: { background: '#3c44aa' } }),
        h('span', { style: { background: '#c8a03a' } }),
      ]),
    })
    const second = mount(container, { ariaLabel: '音效', icon: 'morph:volume-2' })
    assert.equal(container.children.length, 2)
    assert.notEqual(first.element, second.element)
    assert.equal((first.element as unknown as FakeElement).textContent, '')
    first.dispose()
    assert.equal(container.children.length, 1)
    assert.equal(second.element !== null, true)
    second.dispose()
    assert.equal(container.children.length, 0)
  }
  finally {
    dom.restore()
  }
})

test('scope cleanup disposes its mounted action and detached containers remain safe to dispose', async () => {
  const dom = installDocument()
  try {
    const { effectScope, mount } = await loadActionHarness()
    const container = dom.document.createElement('div') as unknown as Element
    const scope = effectScope()
    const action = scope.run(() => mount(container, { ariaLabel: 'Scoped action', text: 'run' }))!
    assert.equal(container.children.length, 1)
    scope.stop()
    assert.equal(container.children.length, 0)

    const detached = mount(container, { ariaLabel: 'Detached action', text: 'run' })
    const host = container.children[0] as unknown as FakeElement
    ;(container as unknown as FakeElement).removeChild(host)
    assert.doesNotThrow(() => detached.dispose())
    assert.equal(host.children.length, 0)
    assert.equal(action.element, null)
  }
  finally {
    dom.restore()
  }
})
