import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { isLikelyPublicPath, isSiteThemeVisible } from './theme-public-path'

describe('theme-runtime public path gate', () => {
  it('treats public-site prefixes as SITE before the router is ready', () => {
    assert.equal(isLikelyPublicPath({ pathname: '/site', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/site/news', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/embed', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/login', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/wiki/space/page', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/servers', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/activities/12', hash: '' }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/', hash: '#/site' }), true)
  })

  it('treats a guest root path as SITE before the router is ready', () => {
    assert.equal(isLikelyPublicPath({ pathname: '/', hash: '' }, { hasToken: false }), true)
    assert.equal(isLikelyPublicPath({ pathname: '/', hash: '#/' }, { hasToken: false }), true)
  })

  it('keeps admin and unknown paths on the host splash', () => {
    assert.equal(isLikelyPublicPath({ pathname: '/', hash: '' }, { hasToken: true }), false)
    assert.equal(isLikelyPublicPath({ pathname: '/dashboard', hash: '' }), false)
    assert.equal(isLikelyPublicPath({ pathname: '/platform/theme-center', hash: '' }), false)
    assert.equal(isLikelyPublicPath({ pathname: '/sitenot', hash: '' }), false)
  })

  it('keeps SITE CSS on until a non-public route is confirmed', () => {
    assert.equal(isSiteThemeVisible(true, { pathname: '/dashboard', hash: '' }, { hasToken: true }), true)
    assert.equal(isSiteThemeVisible(false, { pathname: '/site', hash: '' }), true)
    assert.equal(isSiteThemeVisible(false, { pathname: '/', hash: '' }, { hasToken: false }), true)
    assert.equal(isSiteThemeVisible(false, { pathname: '/dashboard', hash: '' }, { hasToken: true }), false)
  })
})
