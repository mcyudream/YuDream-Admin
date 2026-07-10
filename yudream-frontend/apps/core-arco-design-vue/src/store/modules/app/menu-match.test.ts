import assert from 'node:assert/strict'
import test from 'node:test'
import { findBestMenuGroupIndex } from './menu-match'

test('prefers an exact menu path over an earlier shorter prefix match', () => {
  const groups = [
    {
      children: [{ path: '/platform/plugins/minecraft-server' }],
    },
    {
      children: [{
        path: '/system/logs',
        children: [{ path: '/platform/plugins/minecraft-server/admin' }],
      }],
    },
  ]

  assert.equal(
    findBestMenuGroupIndex(groups, '/platform/plugins/minecraft-server/admin'),
    1,
  )
})

test('prefers the longest matching path prefix', () => {
  const groups = [
    { children: [{ path: '/platform' }] },
    { children: [{ path: '/platform/plugins' }] },
  ]

  assert.equal(
    findBestMenuGroupIndex(groups, '/platform/plugins/example/detail'),
    1,
  )
})

test('only matches prefixes at a path segment boundary', () => {
  const groups = [
    { children: [{ path: '/foo' }] },
    { children: [{ path: '/foobar' }] },
  ]

  assert.equal(findBestMenuGroupIndex(groups, '/foobar/detail'), 1)
})

test('returns negative one when no menu path matches', () => {
  const groups = [{ children: [{ path: '/system' }] }]

  assert.equal(findBestMenuGroupIndex(groups, '/platform'), -1)
})
