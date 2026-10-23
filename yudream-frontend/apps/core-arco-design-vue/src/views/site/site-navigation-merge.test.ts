import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { buildNavigationTree, manualNavigationItems, mergeSiteNavigation, parseNavigationItems } from './site-navigation-merge'

const WIKI_SPACES = [
  { name: '新手指南', slug: 'newbie' },
  { name: '服务器规则', slug: 'rules' },
  { name: '图片使用指南', slug: 'images' },
]

function flat(items: ReturnType<typeof mergeSiteNavigation>) {
  return items.flatMap(item => [
    `${item.label}${item.visible === false ? '(隐藏)' : ''}@${item.url}`,
    ...(item.children || []).map(child => `${child.label}@${child.url}`),
  ])
}

describe('site navigation merge', () => {
  it('auto-appends plugin pages, market and wiki after manual items', () => {
    const nav = mergeSiteNavigation(
      JSON.stringify([{ id: 'nav-0', label: '首页', url: '/site' }]),
      [{ id: 'plugin-x', label: '地图', url: '/map', visible: true, sort: 0 }],
      true,
      true,
    )
    assert.deepEqual(flat(nav), ['首页@/site', '地图@/map', '插件市场@/market', '知识库@/wiki'])
  })

  it('hidden manual entry suppresses the auto-appended entry with the same url', () => {
    const nav = mergeSiteNavigation(
      JSON.stringify([
        { id: 'nav-0', label: '首页', url: '/site' },
        { id: 'nav-1', label: '服务器', url: '/servers', visible: false },
        { id: 'nav-2', label: '知识库', url: '/wiki', visible: false },
      ]),
      [{ id: 'plugin-x', label: '服务器', url: '/servers', visible: true, sort: 0 }],
      true,
      true,
    )
    const labels = flat(nav)
    assert.equal(labels.includes('服务器@/servers'), false, '隐藏条目应压制插件同地址追加')
    assert.equal(labels.includes('知识库@/wiki'), false, '隐藏条目应压制知识库能力入口')
    assert.deepEqual(labels, ['首页@/site', '插件市场@/market'])
  })

  it('hides children of a hidden parent instead of flattening them', () => {
    const tree = buildNavigationTree(mergeSiteNavigation(
      JSON.stringify([
        { id: 'nav-0', label: '首页', url: '/site' },
        { id: 'nav-1', label: '知识库', url: '/wiki', visible: false, children: [{ id: 'nav-1-0', label: '新手指南', url: '/wiki/newbie' }] },
      ]),
      [],
      false,
      false,
    ))
    assert.deepEqual(flat(tree), ['首页@/site'])
  })

  it('expands wiki space children for entries with autoChildren', () => {
    const tree = buildNavigationTree(mergeSiteNavigation(
      JSON.stringify([{ id: 'nav-5', label: '知识库', url: '/wiki', autoChildren: true }]),
      [],
      false,
      true,
      WIKI_SPACES,
    ))
    const wiki = tree.find(item => item.url === '/wiki')
    assert.ok(wiki)
    assert.deepEqual((wiki.children || []).map(child => child.url), ['/wiki/newbie', '/wiki/rules', '/wiki/images'])
  })

  it('manual children win over auto space children and keep sorting', () => {
    const tree = buildNavigationTree(mergeSiteNavigation(
      JSON.stringify([{
        id: 'nav-5',
        label: '知识库',
        url: '/wiki',
        autoChildren: true,
        children: [
          { id: 'nav-5-1', label: '红石与生电（自建）', url: '/wiki/custom', sort: 0 },
          { id: 'nav-5-0', label: '规则（手动）', url: '/wiki/rules', sort: 1 },
        ],
      }]),
      [],
      false,
      true,
      WIKI_SPACES,
    ))
    const wiki = tree.find(item => item.url === '/wiki')
    assert.deepEqual((wiki?.children || []).map(child => child.label), ['红石与生电（自建）', '规则（手动）', '新手指南', '图片使用指南'])
  })

  it('does not expand auto children for non-wiki entries, hidden parents or items without id', () => {
    const nav = mergeSiteNavigation(
      JSON.stringify([
        { id: 'nav-0', label: '首页', url: '/site', autoChildren: true },
        { id: 'nav-1', label: '知识库', url: '/wiki', autoChildren: true, visible: false },
        { label: '知识库无ID', url: '/wiki/', autoChildren: true },
      ]),
      [],
      false,
      true,
      WIKI_SPACES,
    )
    assert.equal(nav.filter(item => item.parentId).length, 0)
    assert.equal(nav.some(item => item.visible === false), false)
  })

  it('manual-only mode still filters hidden entries and expands spaces without capability entries', () => {
    const items = manualNavigationItems(
      JSON.stringify([
        { id: 'nav-0', label: '首页', url: '/site' },
        { id: 'nav-1', label: '插件市场', url: '/market', visible: false },
        { id: 'nav-2', label: '知识库', url: '/wiki', autoChildren: true },
      ]),
      WIKI_SPACES,
    )
    const tree = buildNavigationTree(items)
    assert.deepEqual(flat(tree), ['首页@/site', '知识库@/wiki', '新手指南@/wiki/newbie', '服务器规则@/wiki/rules', '图片使用指南@/wiki/images'])
  })

  it('parseNavigationItems keeps hidden entries only on request', () => {
    const json = JSON.stringify([
      { id: 'a', label: '显示', url: '/a', visible: true },
      { id: 'b', label: '隐藏', url: '/b', visible: false },
    ])
    assert.deepEqual(parseNavigationItems(json).map(item => item.id), ['a'])
    assert.deepEqual(parseNavigationItems(json, { keepHidden: true }).map(item => item.id), ['a', 'b'])
  })
})
