import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { isLikelyPublicPath } from './theme-public-path'

describe('theme-runtime public path gate', () => {
  it('treats public-site prefixes as SITE before the router is ready', () => {
    assert.equal(isLikelyPublicPath({ pathname: '/site', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/site/news', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/login', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/wiki/space/page', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/servers', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/activities/12', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/', hash: '#/site' }), true)
  })

  it('keeps admin and unknown paths on the host splash', () => {
    assert.equal(isLikelyPublicPath({ pathname: '/', hash: '' }), false)
    assert.equal(isLikelyPublicPath({ pathname: '/dashboard', hash: '' }), false)
    assert.equal(isLikelyPublicPath({ pathname: '/platform/theme-center', hash: '' }), false)
    assert.equal(isLikelyPublicPath({ pathname: '/sitenot', hash: '' }), false)
  })
})
