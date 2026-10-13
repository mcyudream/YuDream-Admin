/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createYdMarkdownUploadCallback, createYdMarkdownUploadLifecycle } from './markdown-editor-lifecycle'

test('Markdown upload callback is once-only for an active editor generation', () => {
  const lifecycle = createYdMarkdownUploadLifecycle()
  const received: string[][] = []
  const callback = createYdMarkdownUploadCallback<string[]>(lifecycle, lifecycle.ticket(), value => received.push(value))
  callback(['first'])
  callback(['duplicate'])
  assert.deepEqual(received, [['first']])
})

test('readonly/disabled invalidation and unmount discard callbacks from pending uploads', () => {
  const lifecycle = createYdMarkdownUploadLifecycle()
  const received: string[][] = []
  const pendingTicket = lifecycle.ticket()
  const callback = createYdMarkdownUploadCallback<string[]>(lifecycle, pendingTicket, value => received.push(value))
  lifecycle.invalidate()
  lifecycle.invalidate()
  callback(['late after state change'])

  const unmountTicket = lifecycle.ticket()
  const afterUnmount = createYdMarkdownUploadCallback<string[]>(lifecycle, unmountTicket, value => received.push(value))
  lifecycle.dispose()
  afterUnmount(['late after unmount'])
  assert.deepEqual(received, [])
})
