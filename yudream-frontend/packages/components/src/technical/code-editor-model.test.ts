/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { applyExternalCodeEditorValue, canFormatCodeEditor, destroyCodeEditorView, formatCodeEditorJson, resolveCodeEditorInteractivity, resolveYdEditorFieldAttrs, ydCodeEditorThemeStyles } from './code-editor-model'
import { resolveYdMarkdownEditorTheme } from './markdown-editor.types'

function fakeView(initial: string) {
  let value = initial
  let dispatches = 0
  let destroys = 0
  return {
    get value() { return value },
    get dispatches() { return dispatches },
    get destroys() { return destroys },
    state: {
      get doc() {
        return {
          get length() { return value.length },
          toString: () => value,
        }
      },
    },
    dispatch({ changes }: { changes: { from: number, to: number, insert: string } }) {
      value = `${value.slice(0, changes.from)}${changes.insert}${value.slice(changes.to)}`
      dispatches += 1
    },
    destroy() { destroys += 1 },
  }
}

test('外部模型变化只在文档不同时提交一次，并写入完整受控值', () => {
  const view = fakeView('{"a":1}')
  assert.equal(applyExternalCodeEditorValue(view, '{"a":1}'), false)
  assert.equal(view.dispatches, 0)
  assert.equal(applyExternalCodeEditorValue(view, '{"a":2}'), true)
  assert.equal(view.value, '{"a":2}')
  assert.equal(view.dispatches, 1)
})

test('JSON 格式化只处理有效 JSON，且只允许可编辑 JSON 模式', () => {
  assert.equal(formatCodeEditorJson('{"a":1}'), '{\n  "a": 1\n}')
  assert.equal(formatCodeEditorJson('{bad'), undefined)
  assert.equal(canFormatCodeEditor('json', false, false), true)
  assert.equal(canFormatCodeEditor('json', true, false), false)
  assert.equal(canFormatCodeEditor('json', false, true), false)
  assert.equal(canFormatCodeEditor('html', false, false), false)
})

test('只读与禁用语义分别暴露给编辑能力和无障碍状态', () => {
  assert.deepEqual(resolveCodeEditorInteractivity(true, false), { editable: false, ariaReadonly: 'true', ariaDisabled: 'false' })
  assert.deepEqual(resolveCodeEditorInteractivity(false, true), { editable: false, ariaReadonly: 'false', ariaDisabled: 'true' })
  assert.deepEqual(resolveCodeEditorInteractivity(false, false), { editable: true, ariaReadonly: 'false', ariaDisabled: 'false' })
})

test('销毁编辑器实例时释放底层 view 并清空实例引用', () => {
  const view = fakeView('')
  assert.equal(destroyCodeEditorView(view), null)
  assert.equal(view.destroys, 1)
  assert.equal(destroyCodeEditorView(null), null)
})

test('Markdown 明暗主题跟随宿主，CodeMirror 颜色读取主题变量', () => {
  assert.equal(resolveYdMarkdownEditorTheme(false), 'light')
  assert.equal(resolveYdMarkdownEditorTheme(true), 'dark')
  assert.equal(ydCodeEditorThemeStyles['&'].backgroundColor, 'var(--color-bg-1)')
  assert.equal(ydCodeEditorThemeStyles['.cm-gutters'].color, 'var(--color-text-3)')
})

test('表单绑定的 id、错误状态和说明文本到达实际编辑输入且合并错误文案', () => {
  assert.deepEqual(resolveYdEditorFieldAttrs({ id: 'content', invalid: true, describedby: 'content-help', error: 'required' }), {
    id: 'content',
    invalid: true,
    describedby: 'content-help content-error',
    errorId: 'content-error',
  })
  assert.deepEqual(resolveYdEditorFieldAttrs({ id: 'content', invalid: false }), {
    id: 'content',
    invalid: false,
    describedby: undefined,
    errorId: undefined,
  })
})
