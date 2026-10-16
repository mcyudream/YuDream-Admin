/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createWikiEditorState } from './wiki-editor-state'

function draft(body: string) {
  return {
    title: '页面',
    body,
    pageType: 'concept',
    related: [],
    tags: [],
    sources: [],
    summary: '',
  }
}

test('refreshing the same page does not replace unsaved content', () => {
  const editor = createWikiEditorState()
  editor.load('space:page', draft('original'))
  editor.state.fields.body = 'edited'
  editor.load('space:page', draft('original'))
  assert.equal(editor.state.fields.body, 'edited')
  assert.equal(editor.dirty.value, true)
  assert.equal(editor.state.conflict, false)
})

test('a remote change while editing requires explicit resolution', () => {
  const editor = createWikiEditorState()
  editor.load('space:page', draft('original'))
  editor.state.fields.body = 'edited'
  editor.load('space:page', draft('updated elsewhere'))
  assert.equal(editor.state.conflict, true)
  assert.equal(editor.state.fields.body, 'edited')
  editor.discard()
  assert.equal(editor.dirty.value, false)
  assert.equal(editor.state.conflict, false)
})

test('late save acknowledgements do not clear newer edits or another page', () => {
  const editor = createWikiEditorState()
  editor.load('space:first', draft('original'))
  editor.state.fields.body = 'submitted'
  const first = editor.ticket()
  editor.state.fields.body = 'newer'
  assert.equal(editor.acknowledge(first), true)
  assert.equal(editor.dirty.value, true)
  editor.load('space:second', draft('second'))
  assert.equal(editor.acknowledge(first), false)
  assert.equal(editor.state.fields.body, 'second')
  assert.equal(editor.dirty.value, false)
})
