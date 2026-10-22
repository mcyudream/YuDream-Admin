import type { ChatWorkspaceEntry } from '../api/modules/chat-workspace.ts'
import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceTree, formatWorkspaceBytes, workspaceEditorLanguage, workspaceIsImage, workspaceJoin, workspaceNameError, workspaceParent } from './workspace-tree.ts'

function entry(path: string, kind: ChatWorkspaceEntry['kind'] = 'FILE'): ChatWorkspaceEntry {
  return { id: path, path, kind, size: 1, contentType: null, revision: 1, updatedBy: 'USER', updatedAt: '2026-10-22T10:00:00' }
}

test('tree nests by path, folders first, natural order, orphans at root', () => {
  const tree = buildWorkspaceTree([entry('b.md'), entry('src', 'DIRECTORY'), entry('src/file10.py'), entry('src/file2.py'), entry('src/lib', 'DIRECTORY'), entry('a', 'DIRECTORY'), entry('lost/x.txt')])
  assert.deepEqual(tree.map(node => node.key), ['a', 'src', 'b.md', 'lost/x.txt'])
  assert.deepEqual(tree[1]!.children!.map(node => node.label), ['lib', 'file2.py', 'file10.py'])
  assert.deepEqual(tree[0]!.children, [])
  assert.equal(tree[2]!.isLeaf, true)
})

test('path helpers, languages, images, sizes and name validation', () => {
  assert.equal(workspaceParent('a/b/c.md'), 'a/b')
  assert.equal(workspaceParent('c.md'), '')
  assert.equal(workspaceJoin('', 'x'), 'x')
  assert.equal(workspaceJoin('a', 'x'), 'a/x')
  assert.equal(workspaceEditorLanguage('src/App.TS'), 'javascript')
  assert.equal(workspaceEditorLanguage('notes/README'), 'text')
  assert.equal(workspaceEditorLanguage('data.jsonl'), 'json')
  assert.equal(workspaceIsImage({ path: 'a.bin', contentType: 'image/png' }), true)
  assert.equal(workspaceIsImage({ path: 'photo.JPG', contentType: null }), true)
  assert.equal(workspaceIsImage({ path: 'evil.svg', contentType: 'text/plain' }), false)
  assert.equal(formatWorkspaceBytes(512), '512 B')
  assert.equal(formatWorkspaceBytes(1536), '1.5 KB')
  assert.equal(formatWorkspaceBytes(500 * 1024 * 1024), '500 MB')
  assert.equal(workspaceNameError('  '), '名称不能为空')
  assert.notEqual(workspaceNameError('..'), '')
  assert.notEqual(workspaceNameError('a\\b'), '')
  assert.equal(workspaceNameError('报告 (2).pdf'), '')
})
