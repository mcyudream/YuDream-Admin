/* eslint-disable test/no-import-node-test -- 当前工作区使用 Node 原生测试，无 vitest 依赖。 */
import type { YdFilePickerOptions } from './useYdFilePicker'
import assert from 'node:assert/strict'
import test from 'node:test'
import { effectScope } from 'vue'

const pickerModulePath = './useYdFilePicker.ts'

async function createPicker(options?: YdFilePickerOptions) {
  const { useYdFilePicker } = await import(pickerModulePath) as typeof import('./useYdFilePicker')
  return useYdFilePicker(options)
}

async function createPickerInScope(scope: ReturnType<typeof effectScope>) {
  const { useYdFilePicker } = await import(pickerModulePath) as typeof import('./useYdFilePicker')
  return scope.run(() => useYdFilePicker())!
}

interface Listener { callback: EventListener, once: boolean }

class MockInput {
  type = ''
  accept = ''
  multiple = false
  hidden = false
  files: FileList | null = null
  readonly attributes = new Set<string>()
  readonly listeners = new Map<string, Listener[]>()
  parent: MockBody | null = null
  clicks = 0

  setAttribute(name: string) {
    this.attributes.add(name)
  }

  addEventListener(type: string, callback: EventListener, options?: boolean | AddEventListenerOptions) {
    const listeners = this.listeners.get(type) ?? []
    listeners.push({ callback, once: typeof options === 'object' && options.once === true })
    this.listeners.set(type, listeners)
  }

  removeEventListener(type: string, callback: EventListener) {
    this.listeners.set(type, (this.listeners.get(type) ?? []).filter(listener => listener.callback !== callback))
  }

  click() {
    this.clicks++
  }

  remove() {
    this.parent?.removeChild(this)
  }

  emit(type: 'change' | 'cancel') {
    for (const listener of [...(this.listeners.get(type) ?? [])]) {
      if (listener.once) {
        this.removeEventListener(type, listener.callback)
      }
      listener.callback.call(this, { type, target: this, currentTarget: this } as unknown as Event)
    }
  }
}

class MockBody {
  readonly children: MockInput[] = []

  appendChild(input: MockInput) {
    input.parent = this
    this.children.push(input)
    return input
  }

  removeChild(input: MockInput) {
    const index = this.children.indexOf(input)
    if (index >= 0) {
      this.children.splice(index, 1)
    }
    input.parent = null
    return input
  }
}

function installDocument() {
  const original = globalThis.document
  const body = new MockBody()
  const inputs: MockInput[] = []
  const document = {
    body,
    createElement: (tagName: string) => {
      assert.equal(tagName, 'input')
      const input = new MockInput()
      inputs.push(input)
      return input as unknown as HTMLInputElement
    },
  } as unknown as Document
  globalThis.document = document
  return {
    body,
    inputs,
    restore() {
      globalThis.document = original
    },
  }
}

function file(name: string, relativePath?: string) {
  const result = new File([name], name, { type: 'text/plain' })
  if (relativePath) {
    Object.defineProperty(result, 'webkitRelativePath', { value: relativePath })
  }
  return result
}

test('opens the native picker with defaults and resolves selected File objects with cleanup', async () => {
  const dom = installDocument()
  try {
    const picker = await createPicker({ accept: '.zip,application/zip', multiple: true })
    const pending = picker.open()
    const input = dom.inputs[0]!
    assert.equal(input.type, 'file')
    assert.equal(input.accept, '.zip,application/zip')
    assert.equal(input.multiple, true)
    assert.equal(input.hidden, true)
    assert.equal(input.clicks, 1)
    assert.equal(dom.body.children.length, 1)

    const selected = [file('a.zip'), file('b.zip')]
    input.files = selected as unknown as FileList
    input.emit('change')
    assert.deepEqual(await pending, selected)
    assert.equal(dom.body.children.length, 0)
    assert.equal(input.parent, null)
    assert.equal(input.listeners.get('change')?.length, 0)
    assert.equal(input.listeners.get('cancel')?.length, 0)
  }
  finally {
    dom.restore()
  }
})

test('cancel event resolves an empty list and releases its input', async () => {
  const dom = installDocument()
  try {
    const pending = (await createPicker()).open()
    const input = dom.inputs[0]!
    input.emit('cancel')
    assert.deepEqual(await pending, [])
    assert.equal(dom.body.children.length, 0)
    assert.equal(input.listeners.get('change')?.length, 0)
    assert.equal(input.listeners.get('cancel')?.length, 0)
  }
  finally {
    dom.restore()
  }
})

test('cancel settles the active promise and releases its input', async () => {
  const dom = installDocument()
  try {
    const picker = await createPicker()
    const pending = picker.open()
    const input = dom.inputs[0]!
    picker.cancel()
    assert.deepEqual(await pending, [])
    assert.equal(input.parent, null)
    assert.equal(dom.body.children.length, 0)
  }
  finally {
    dom.restore()
  }
})

test('a repeated open settles the previous promise before starting the new picker', async () => {
  const dom = installDocument()
  try {
    const picker = await createPicker()
    const previous = picker.open()
    const previousInput = dom.inputs[0]!
    const current = picker.open({ accept: 'image/*', multiple: true })
    assert.deepEqual(await previous, [])
    assert.equal(previousInput.parent, null)
    assert.equal(dom.body.children.length, 1)
    const currentInput = dom.inputs[1]!
    assert.equal(currentInput.accept, 'image/*')
    assert.equal(currentInput.multiple, true)
    const selected = [file('photo.png')]
    currentInput.files = selected as unknown as FileList
    currentInput.emit('change')
    assert.deepEqual(await current, selected)
    assert.equal(dom.body.children.length, 0)
  }
  finally {
    dom.restore()
  }
})

test('dispose settles the active promise, removes the input, and prevents later opens', async () => {
  const dom = installDocument()
  try {
    const picker = await createPicker()
    const pending = picker.open()
    const input = dom.inputs[0]!
    picker.dispose()
    assert.deepEqual(await pending, [])
    assert.equal(input.parent, null)
    assert.equal(dom.body.children.length, 0)
    assert.deepEqual(await picker.open(), [])
    assert.equal(dom.inputs.length, 1)
  }
  finally {
    dom.restore()
  }
})

test('scope disposal releases the picker and settles its pending promise', async () => {
  const dom = installDocument()
  try {
    const scope = effectScope()
    const picker = await createPickerInScope(scope)
    const pending = picker.open()
    const input = dom.inputs[0]!
    scope.stop()
    assert.deepEqual(await pending, [])
    assert.equal(input.parent, null)
    assert.equal(dom.body.children.length, 0)
    assert.deepEqual(await picker.open(), [])
  }
  finally {
    dom.restore()
  }
})

test('directory mode preserves browser File objects and their relative paths', async () => {
  const dom = installDocument()
  try {
    const picker = await createPicker({ directory: true, multiple: true })
    const pending = picker.open()
    const input = dom.inputs[0]!
    assert.equal(input.attributes.has('webkitdirectory'), true)
    const nested = file('readme', 'docs/guides/readme.md')
    input.files = [nested] as unknown as FileList
    input.emit('change')
    const selected = await pending
    assert.equal(selected[0], nested)
    assert.equal(selected[0]?.webkitRelativePath, 'docs/guides/readme.md')
  }
  finally {
    dom.restore()
  }
})

test('SSR open resolves empty without touching the document', async () => {
  const original = globalThis.document
  try {
    Reflect.deleteProperty(globalThis, 'document')
    assert.deepEqual(await (await createPicker()).open(), [])
  }
  finally {
    Reflect.defineProperty(globalThis, 'document', { configurable: true, writable: true, value: original })
  }
})
