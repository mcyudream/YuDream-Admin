import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

function isHttpNavigationUrl(url?: string) {
  return /^https?:\/\//i.test((url || '').trim())
}

function navigationHref(item: { url: string, label?: string, embed?: boolean }) {
  const url = (item.url || '').trim()
  if (!item.embed || !isHttpNavigationUrl(url)) {
    return url
  }
  const params = new URLSearchParams({ url })
  if (item.label) {
    params.set('title', item.label)
  }
  return `/embed?${params.toString()}`
}

describe('site navigation embed href', () => {
  it('keeps internal paths unchanged', () => {
    assert.equal(navigationHref({ url: '/wiki', label: '知识库' }), '/wiki')
    assert.equal(isHttpNavigationUrl('/wiki'), false)
  })

  it('rewrites http(s) links with embed to /embed', () => {
    const href = navigationHref({ url: 'https://nmo.net.cn:25569/list', label: '服务器列表', embed: true })
    assert.equal(href.startsWith('/embed?'), true)
    const params = new URLSearchParams(href.slice('/embed?'.length))
    assert.equal(params.get('url'), 'https://nmo.net.cn:25569/list')
    assert.equal(params.get('title'), '服务器列表')
  })

  it('leaves ordinary external links as-is', () => {
    assert.equal(navigationHref({ url: 'https://example.com', label: '官网' }), 'https://example.com')
  })
})
