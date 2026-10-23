import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { resolveStudioHref, siteAccountAction } from './site-studio-links'

describe('site account action links', () => {
  it('maps /profile and /logout to account actions', () => {
    assert.equal(siteAccountAction('/profile'), 'profile')
    assert.equal(siteAccountAction('/logout'), 'logout')
    assert.equal(siteAccountAction('/profile?tab=security'), 'profile')
    assert.equal(siteAccountAction('/logout?next=/site'), 'logout')
    assert.equal(siteAccountAction('/profile/'), 'profile')
    assert.equal(siteAccountAction(' /logout '), 'logout')
  })

  it('ignores other links, fragments and empty values', () => {
    assert.equal(siteAccountAction('/'), undefined)
    assert.equal(siteAccountAction('/site'), undefined)
    assert.equal(siteAccountAction('/profiles'), undefined)
    assert.equal(siteAccountAction('/site/profile'), undefined)
    assert.equal(siteAccountAction('/wiki'), undefined)
    assert.equal(siteAccountAction('#profile'), undefined)
    assert.equal(siteAccountAction('https://example.com/logout'), undefined)
    assert.equal(siteAccountAction(''), undefined)
    assert.equal(siteAccountAction(null), undefined)
    assert.equal(siteAccountAction(undefined), undefined)
  })
})

describe('resolveStudioHref', () => {
  it('keeps host-routed account links intact with data-yd-host-link', () => {
    assert.equal(resolveStudioHref('/profile', '/site', true), '/profile')
    assert.equal(resolveStudioHref('/logout', '/site', true), '/logout')
  })

  it('scopes plain site-relative links to /site', () => {
    assert.equal(resolveStudioHref('/profile', '/site'), '/site/profile')
    assert.equal(resolveStudioHref('/wiki', '/site'), '/wiki')
  })
})
