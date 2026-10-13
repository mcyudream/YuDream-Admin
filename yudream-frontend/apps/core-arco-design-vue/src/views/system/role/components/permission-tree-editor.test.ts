import type { Component, ComponentPublicInstance, PropType, SetupContext } from 'vue'
import type { PermissionEditorNode } from './permission-tree-editor'
import type { PermissionItem } from '@/api/modules/system-role'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import * as renderer from 'vue/server-renderer'
import * as catalogModule from '@/utils/permission-catalog'
import { mergeVisiblePermissions } from '@/utils/permission-catalog'
import * as editorModule from './permission-tree-editor'
import { createPermissionTreeCatalog, permissionEditorCheckedKeys, permissionEditorDefaultExpansion, permissionEditorMatches, permissionEditorPage, permissionEditorPageSizes, permissionEditorView } from './permission-tree-editor'

const permissions: PermissionItem[] = [
  { code: 'system:user:view', name: '查看用户', module: '系统管理', status: 'ACTIVE' },
  { code: 'system:user:edit', name: '编辑用户', module: '系统管理', status: 'ACTIVE' },
  { code: 'plugin:forum:category:view', name: '查看论坛分类', module: '论坛分类', status: 'ACTIVE' },
  { code: 'plugin:forum:view', name: '查看论坛', module: '论坛', status: 'ACTIVE' },
  { code: 'plugin:old:use', name: '旧插件', module: '旧插件', status: 'DEPRECATED' },
]

function leafNodes(nodes: PermissionEditorNode[]): PermissionEditorNode[] {
  return nodes.flatMap(node => node.code ? [node] : leafNodes(node.children || []))
}

async function compileEditor(ssr: boolean): Promise<Component> {
  const source = await readFile(new URL('./PermissionTreeEditor.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  const compiled = compileScript(descriptor, { id: 'permission-tree-contract', inlineTemplate: true, templateOptions: { ssr } })
  const output = ts.transpileModule(`const { ref, computed, watch } = require('vue');\n${compiled.content}`, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const module = { exports: {} as { default?: Component } }
  const dependencies: Record<string, unknown> = { vue, 'vue/server-renderer': renderer, '@/utils/permission-catalog': catalogModule, './permission-tree-editor': editorModule }
  runInNewContext(output, {
    module,
    exports: module.exports,
    require: (name: string) => {
      assert.ok(name in dependencies, `unexpected runtime dependency: ${name}`)
      return dependencies[name]
    },
    setTimeout,
    clearTimeout,
  })
  return module.exports.default!
}

interface TestNode {
  parent: TestNode | null
  children: TestNode[]
}

interface TreeStubProps {
  data: PermissionEditorNode[]
  virtualList: boolean
  defaultExpandAll: boolean
  expandedKeys: string[]
  checkedKeys: string[]
}

interface PaginationStubProps {
  page: number
  size: number
  sizes: number[]
  total: number
}

const treeStubProps = {
  data: { type: Array as PropType<PermissionEditorNode[]>, required: true },
  virtualList: { type: Boolean, required: true },
  defaultExpandAll: { type: Boolean, required: true },
  expandedKeys: { type: Array as PropType<string[]>, required: true },
  checkedKeys: { type: Array as PropType<string[]>, required: true },
} as const

const paginationStubProps = {
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  sizes: { type: Array as PropType<number[]>, required: true },
  total: { type: Number, required: true },
} as const

type TreeStubContext = SetupContext<{ 'update:checkedKeys': (codes: string[]) => void }>
type InputStubContext = SetupContext<{ 'update:modelValue': (keyword: string) => void }>
type PaginationStubContext = SetupContext<{ 'update:page': (page: number) => void, 'update:size': (size: number) => void }>

function testNode(): TestNode {
  return { parent: null, children: [] }
}

function createTestRenderer() {
  return vue.createRenderer<TestNode, TestNode>({
    createElement: testNode,
    createText: testNode,
    createComment: testNode,
    setText: () => {},
    setElementText: () => {},
    patchProp: () => {},
    parentNode: node => node.parent,
    nextSibling: (node) => {
      const children = node.parent?.children || []
      return children[children.indexOf(node) + 1] || null
    },
    insert: (node, parent, anchor) => {
      if (node.parent) {
        node.parent.children.splice(node.parent.children.indexOf(node), 1)
      }
      const position = anchor ? parent.children.indexOf(anchor) : parent.children.length
      parent.children.splice(position, 0, node)
      node.parent = parent
    },
    remove: (node) => {
      if (node.parent) {
        node.parent.children.splice(node.parent.children.indexOf(node), 1)
        node.parent = null
      }
    },
  })
}

describe('permission tree editor rendering and selection semantics', () => {
  it('passes only the current page to the actual editor even after its expand-all command', async () => {
    const component = await compileEditor(true)
    const large = Array.from({ length: 500 }, (_, index) => ({ code: `plugin:owner-${index}:view`, name: `查看资源 ${index}`, module: `资源 ${index}`, status: 'ACTIVE' as const }))
    for (const expand of [false, true]) {
      let captured: { data?: PermissionEditorNode[], virtualList?: boolean, defaultExpandAll?: boolean, expandedKeys?: string[], checkedKeys?: string[] } | undefined
      let pagination: { total?: number, size?: number, sizes?: number[] } | undefined
      const app = vue.createSSRApp(component, { permissions: large, modelValue: ['plugin:owner-0:view', 'plugin:missing:use', '*'] })
      app.mixin({
        serverPrefetch() {
          const exposed = this.$.exposed
          if (expand && typeof exposed?.expandAll === 'function') {
            exposed.expandAll()
          }
        },
      })
      app.component('YdTree', vue.defineComponent({
        props: treeStubProps,
        setup(props: Readonly<TreeStubProps>) {
          captured = props
          return () => vue.h('div')
        },
      }))
      app.component('YdPagination', vue.defineComponent({
        props: paginationStubProps,
        setup(props: Readonly<PaginationStubProps>) {
          pagination = props
          return () => vue.h('div')
        },
      }))
      for (const name of ['YdInput', 'YdButton', 'YdAlert', 'YdLoading', 'YdTag', 'YdEmpty']) {
        app.component(name, { render: () => vue.h('span') })
      }
      await renderer.renderToString(app)
      assert.equal(captured?.virtualList, true)
      assert.equal(captured?.defaultExpandAll, false)
      assert.equal(leafNodes(captured?.data || []).length, 100)
      assert.equal(captured?.expandedKeys?.length, expand ? 201 : 1)
      assert.deepEqual(captured?.checkedKeys?.filter(key => key.startsWith('plugin:')), ['plugin:owner-0:view'])
      assert.ok(captured?.checkedKeys?.includes('resource:plugin:owner-0'))
      assert.ok(!captured?.checkedKeys?.includes('domain:plugin'))
      assert.equal(pagination?.total, 500)
      assert.equal(pagination?.size, 100)
      assert.deepEqual([...pagination?.sizes || []], [50, 100, 200])
    }
  })

  it('preserves collapsed state through global search and resets paging while actual page checks retain hidden grants', async () => {
    const component = await compileEditor(false)
    const large = Array.from({ length: 205 }, (_, index) => ({ code: `plugin:owner-${index}:view`, name: `查看资源 ${index}`, module: `资源 ${index}`, status: 'ACTIVE' as const }))
    const allCodes = createPermissionTreeCatalog(large).leafCodes
    const selected = vue.ref([allCodes[0]!, allCodes[60]!, allCodes[150]!, 'plugin:missing:use', '*'])
    let instance: { collapseAll: () => void } | undefined
    let tree: { data: PermissionEditorNode[], expandedKeys: string[] } | undefined
    let pager: { page: number, size: number, total: number } | undefined
    let search: (keyword: string) => void = () => {}
    let changePage: (page: number) => void = () => {}
    let changeSize: (size: number) => void = () => {}
    let check: (codes: string[]) => void = () => {}
    const app = createTestRenderer().createApp({
      render: () => vue.h(component, {
        'permissions': large,
        'modelValue': selected.value,
        'onUpdate:modelValue': (codes: string[]) => selected.value = codes,
        'ref': (value: ComponentPublicInstance | null) => instance = value as unknown as typeof instance,
      }),
    })
    app.component('YdTree', vue.defineComponent({
      props: treeStubProps,
      emits: ['update:checkedKeys'],
      setup(props: Readonly<TreeStubProps>, { emit }: TreeStubContext) {
        tree = props
        check = codes => emit('update:checkedKeys', codes)
        return () => vue.h('div')
      },
    }))
    app.component('YdInput', vue.defineComponent({
      props: { modelValue: { type: String, required: true } },
      emits: ['update:modelValue'],
      setup(_props: Readonly<{ modelValue: string }>, { emit }: InputStubContext) {
        search = keyword => emit('update:modelValue', keyword)
        return () => vue.h('div')
      },
    }))
    app.component('YdPagination', vue.defineComponent({
      props: paginationStubProps,
      emits: ['update:page', 'update:size'],
      setup(props: Readonly<PaginationStubProps>, { emit }: PaginationStubContext) {
        pager = props
        changePage = page => emit('update:page', page)
        changeSize = size => emit('update:size', size)
        return () => vue.h('div')
      },
    }))
    for (const name of ['YdButton', 'YdAlert', 'YdLoading', 'YdTag', 'YdEmpty']) {
      app.component(name, { render: () => vue.h('span') })
    }
    app.mount(testNode())
    instance!.collapseAll()
    await vue.nextTick()
    assert.deepEqual([...tree!.expandedKeys], [])
    changePage(3)
    await vue.nextTick()
    assert.equal(leafNodes(tree!.data).length, 5)
    search(allCodes[204]!)
    await new Promise(resolve => setTimeout(resolve, 220))
    await vue.nextTick()
    assert.equal(pager!.page, 1)
    assert.equal(pager!.total, 1)
    assert.deepEqual(leafNodes(tree!.data).map(node => node.code), [allCodes[204]])
    assert.equal(tree!.expandedKeys.length, 3)
    search('')
    await vue.nextTick()
    assert.equal(pager!.page, 1)
    assert.deepEqual([...tree!.expandedKeys], [])
    assert.equal(leafNodes(tree!.data).length, 100)
    changePage(2)
    changeSize(50)
    await vue.nextTick()
    assert.equal(pager!.page, 1)
    assert.equal(leafNodes(tree!.data).length, 50)
    changePage(2)
    await vue.nextTick()
    const dataBeforeCheck = tree!.data
    check([])
    await vue.nextTick()
    assert.deepEqual([...selected.value], [allCodes[0], allCodes[150], 'plugin:missing:use', '*'])
    assert.equal(tree!.data, dataBeforeCheck)
    check([...allCodes.slice(50, 100), 'domain:plugin'])
    await vue.nextTick()
    assert.equal(tree!.data, dataBeforeCheck)
    assert.ok(selected.value.includes(allCodes[0]!))
    assert.ok(selected.value.includes(allCodes[150]!))
    assert.ok(selected.value.includes('*'))
    assert.ok(selected.value.includes('plugin:missing:use'))
    assert.ok(!selected.value.includes('domain:plugin'))
    assert.equal(selected.value.length, 54)
    app.unmount()
  })

  it('keeps a virtual viewport even when every branch of a large catalog is expanded', () => {
    const catalog = createPermissionTreeCatalog(Array.from({ length: 10000 }, (_, index) => ({ code: `plugin:owner-${index}:view`, name: `查看资源 ${index}`, module: `资源 ${index}`, status: 'ACTIVE' })))
    const view = permissionEditorView(catalog)
    assert.equal(view.leafCodes.length, 10000)
    assert.ok(view.branchKeys.length > 10000)
    assert.equal(view.virtualList, true)
    assert.equal(view.defaultExpandAll, false)
    assert.deepEqual(permissionEditorDefaultExpansion(view), ['domain:plugin'])
    const search = permissionEditorView(catalog, '查看')
    assert.equal(search.leafCodes.length, 10000)
    assert.equal(search.virtualList, true)
    assert.equal(search.defaultExpandAll, false)
  })

  it('reuses the original full tree and leaf objects instead of rebuilding data after checks', () => {
    const catalog = createPermissionTreeCatalog(permissions)
    const full = permissionEditorView(catalog)
    assert.equal(full.data, catalog.data)
    assert.equal(permissionEditorView(catalog).data, full.data)
    const filtered = permissionEditorView(catalog, '编辑用户')
    const fullLeaf = full.data[0]?.children?.[0]?.children?.find(node => node.variant === 'outline')?.children?.[0]
    const filteredLeaf = filtered.data[0]?.children?.[0]?.children?.[0]?.children?.[0]
    assert.equal(filteredLeaf, fullLeaf)
  })

  it('searches normalized resources and categories while retaining matching ancestor paths and counts', () => {
    const catalog = createPermissionTreeCatalog(permissions)
    const filtered = permissionEditorView(catalog, '分类')
    assert.deepEqual(filtered.leafCodes, ['plugin:forum:category:view'])
    assert.equal(filtered.data[0]?.label, '插件（1）')
    assert.equal(filtered.data[0]?.children?.[0]?.label, '论坛（1）')
    assert.deepEqual(permissionEditorView(catalog, '操作').leafCodes, ['system:user:edit'])
    assert.ok(!catalog.leafCodes.includes('plugin:old:use'))
  })

  it('preserves search-hidden, unavailable and wildcard grants during filtered checking', () => {
    const catalog = createPermissionTreeCatalog(permissions)
    const filtered = permissionEditorView(catalog, '编辑用户')
    const original = ['system:user:view', 'system:user:edit', 'plugin:old:use', 'plugin:missing:use', '*']
    const visible = new Set(filtered.leafCodes)
    assert.deepEqual(mergeVisiblePermissions(original, [], visible), ['system:user:view', 'plugin:old:use', 'plugin:missing:use', '*'])
    assert.deepEqual(new Set(mergeVisiblePermissions(original, ['system:user:edit', 'domain:system'], visible)), new Set(original))
  })

  it('caps tree processing to each page and reuses cached leaves and ancestor identities', () => {
    const catalog = createPermissionTreeCatalog(Array.from({ length: 10000 }, (_, index) => ({ code: `plugin:owner-${index}:view`, name: `查看资源 ${index}`, module: `资源 ${index}`, status: 'ACTIVE' })))
    const matching = permissionEditorMatches(catalog)
    assert.equal(matching, catalog.leafCodes)
    for (const size of permissionEditorPageSizes) {
      const view = permissionEditorPage(catalog, matching, 2, size)
      assert.equal(view.leafCodes.length, size)
      assert.deepEqual(view.leafCodes, matching.slice(size, size * 2))
      assert.equal(view.data[0]?.label, `插件（${size}）`)
      assert.equal(view.branchKeys.length, size * 2 + 1)
      assert.equal(view.virtualList, true)
      assert.equal(view.defaultExpandAll, false)
      assert.equal(view.from, size + 1)
      assert.equal(view.to, size * 2)
      assert.deepEqual(permissionEditorDefaultExpansion(view), ['domain:plugin'])
      for (const leaf of leafNodes(view.data)) {
        assert.equal(leaf, catalog.nodeIndex.get(leaf.code!))
      }
    }
    assert.equal(permissionEditorPage(catalog, matching, 1, 100000).leafCodes.length, 200)
    assert.equal(catalog.data[0]?.label, '插件（10000）')
  })

  it('searches beyond the current page and clamps page bounds for reduced or empty results', () => {
    const catalog = createPermissionTreeCatalog(Array.from({ length: 205 }, (_, index) => ({ code: `plugin:owner-${index}:view`, name: `权限名称_${index}_`, module: `资源 ${index}`, status: 'ACTIVE' })))
    const matching = permissionEditorMatches(catalog)
    const farCode = matching[204]!
    const query = permissionEditorMatches(catalog, farCode.toLocaleUpperCase())
    assert.deepEqual(query, [farCode])
    const filtered = permissionEditorPage(catalog, query, 3, 100)
    assert.equal(filtered.page, 1)
    assert.deepEqual(filtered.leafCodes, [farCode])
    const last = permissionEditorPage(catalog, matching, 100, 100)
    assert.equal(last.page, 3)
    assert.equal(last.from, 201)
    assert.equal(last.to, 205)
    assert.equal(last.pageCount, 3)
    for (const invalid of [-1, 0, Number.NaN, Number.POSITIVE_INFINITY]) {
      assert.equal(permissionEditorPage(catalog, matching, invalid, 100).page, 1)
    }
    const empty = permissionEditorPage(catalog, permissionEditorMatches(catalog, '不存在的目录'), 3, 100)
    assert.equal(empty.page, 1)
    assert.equal(empty.from, 0)
    assert.equal(empty.to, 0)
    assert.deepEqual(empty.data, [])
    assert.equal(permissionEditorPage(catalog, matching, 1, 0).size, 100)
  })

  it('keeps other pages, hidden search results, unavailable permissions and wildcard when changing this page', () => {
    const catalog = createPermissionTreeCatalog(permissions)
    const matching = permissionEditorMatches(catalog)
    const page = permissionEditorPage(catalog, matching, 1, 1)
    const otherPage = permissionEditorPage(catalog, matching, 2, 1).leafCodes[0]!
    const original = [...page.leafCodes, otherPage, 'plugin:old:use', 'plugin:missing:use', '*']
    const visible = new Set(page.leafCodes)
    assert.deepEqual(mergeVisiblePermissions(original, [], visible), [otherPage, 'plugin:old:use', 'plugin:missing:use', '*'])
    const selected = permissionEditorCheckedKeys(page, original)
    assert.ok(selected.some(key => key.startsWith('category:')))
    assert.ok(selected.includes('domain:system'))
    assert.deepEqual(new Set(mergeVisiblePermissions(original, selected, visible)), new Set(original))
    assert.ok(!mergeVisiblePermissions(original, selected, visible).some(key => key.startsWith('category:') || key.startsWith('domain:') || key.startsWith('resource:')))
    const hiddenSearch = permissionEditorPage(catalog, permissionEditorMatches(catalog, '编辑用户'), 1, 100)
    assert.deepEqual(mergeVisiblePermissions(['system:user:view', 'system:user:edit', 'plugin:forum:view', '*'], [], new Set(hiddenSearch.leafCodes)), ['system:user:view', 'plugin:forum:view', '*'])
  })

  it('derives complete groups from current-page leaves without marking partially selected groups complete', () => {
    const catalog = createPermissionTreeCatalog(permissions)
    const page = permissionEditorPage(catalog, permissionEditorMatches(catalog, '系统管理'), 1, 100)
    const partial = permissionEditorCheckedKeys(page, ['system:user:view'])
    assert.ok(partial.includes('category:system:user:view'))
    assert.ok(!partial.includes('resource:system:user'))
    assert.ok(!partial.includes('domain:system'))
    const complete = permissionEditorCheckedKeys(page, ['system:user:view', 'system:user:edit'])
    assert.ok(complete.includes('resource:system:user'))
    assert.ok(complete.includes('domain:system'))
    const oneLeafPage = permissionEditorPage(catalog, permissionEditorMatches(catalog, '系统管理'), 1, 1)
    assert.ok(permissionEditorCheckedKeys(oneLeafPage, oneLeafPage.leafCodes).includes('resource:system:user'))
    assert.equal(oneLeafPage.data[0]?.children?.[0]?.label, '用户管理（1）')
  })
})
