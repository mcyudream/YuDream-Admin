/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildYdMonacoThemeColors, canFormatCodeEditor, formatCodeEditorJson, resolveCodeEditorInteractivity, resolveYdEditorFieldAttrs, resolveYdMonacoLanguageId, resolveYdMonacoThemeName, withYdColorAlpha } from './code-editor-model'
import { resolveYdMarkdownEditorTheme } from './markdown-editor.types'

const palette = {
  background: '#ffffff',
  foreground: '#1d2129',
  gutterBackground: '#f7f8fa',
  lineNumber: '#86909c',
  lineHighlight: '#f2f3f5',
  cursor: '#165dff',
  selection: '#165dff59',
  inactiveSelection: '#165dff33',
  selectionHighlight: '#165dff40',
  findMatch: '#165dff66',
  whitespace: '#86909c80',
}

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

test('Markdown 与 Monaco 明暗主题跟随宿主，主题名固定且成对', () => {
  assert.equal(resolveYdMarkdownEditorTheme(false), 'light')
  assert.equal(resolveYdMarkdownEditorTheme(true), 'dark')
  assert.equal(resolveYdMonacoThemeName(false), 'yd-light')
  assert.equal(resolveYdMonacoThemeName(true), 'yd-dark')
})

test('宿主语言标识映射到 Monaco 语言 id，纯文本落到 plaintext', () => {
  assert.equal(resolveYdMonacoLanguageId('text'), 'plaintext')
  assert.equal(resolveYdMonacoLanguageId('json'), 'json')
  assert.equal(resolveYdMonacoLanguageId('css'), 'css')
  assert.equal(resolveYdMonacoLanguageId('html'), 'html')
  assert.equal(resolveYdMonacoLanguageId('javascript'), 'javascript')
})

test('选区配色只叠加主色透明度，且绝不写 selectionForeground', () => {
  assert.equal(withYdColorAlpha('#165dff', 0.35), '#165dff59')
  assert.equal(withYdColorAlpha('#165dff', 0.2), '#165dff33')
  assert.equal(withYdColorAlpha('#165dFF', 1), '#165dffff')
  assert.equal(withYdColorAlpha('#165dff80', 0.5), '#165dff80')
  assert.equal(withYdColorAlpha('rgb(var(--primary-6))', 0.35), 'rgb(var(--primary-6))')
  const colors = buildYdMonacoThemeColors(palette)
  assert.equal(colors['editor.selectionBackground'], '#165dff59')
  assert.equal(colors['editor.selectionHighlightBackground'], '#165dff40')
  assert.equal(colors['editor.inactiveSelectionBackground'], '#165dff33')
  assert.equal(colors['editor.background'], '#ffffff')
  assert.equal(colors['editor.foreground'], '#1d2129')
  assert.equal(colors['editorGutter.background'], '#f7f8fa')
  assert.equal(colors['editorLineNumber.foreground'], '#86909c')
  assert.equal(colors['editor.lineHighlightBackground'], '#f2f3f5')
  assert.equal(colors['editorWhitespace.foreground'], '#86909c80')
  assert.equal(Object.keys(colors).includes('editor.selectionForeground'), false)
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
