import assert from 'node:assert/strict'
import test from 'node:test'
import {
  chromeCssKey,
  chromeCanvasPreviewCss,
  chromeFrameTemplate,
  chromeTemplate,
  isChromeStyleAction,
  readChromeCss,
  writeChromeCss,
} from './cms-chrome'

test('keeps chrome structure fixed while reading and writing zone CSS', () => {
  const settings: Record<string, string> = { navigationJson: '[]' }

  writeChromeCss(settings, 'header', '.yb-chrome-header { color: red; }')

  assert.equal(chromeCssKey('header'), 'chromeHeaderCss')
  assert.equal(readChromeCss(settings, 'header'), '.yb-chrome-header { color: red; }')
  assert.equal(settings.navigationJson, '[]')
  assert.match(chromeTemplate('header'), /data-yb-chrome="header"/)
  assert.match(chromeTemplate('header'), /class="site-layout-header"/)
  assert.match(chromeTemplate('header'), /data-yb-chrome-slot="logo"/)
  assert.match(chromeTemplate('header'), /data-yb-chrome-slot="navigation"/)
  assert.match(chromeTemplate('header'), /data-visible-when="guest"/)
  assert.match(chromeTemplate('header'), /data-visible-when="logged-in"/)
  assert.match(chromeTemplate('header'), /site-layout-header__account/)
  assert.match(chromeTemplate('footer'), /data-yb-chrome="footer"/)
  assert.match(chromeTemplate('footer'), /class="site-layout-footer"/)
  assert.match(chromeFrameTemplate('<section class="hero"></section>'), /data-yb-home-content/)
  assert.match(chromeFrameTemplate('<section class="hero"></section>'), /class="hero"/)
  assert.match(chromeCanvasPreviewCss(), /\.site-layout-header/)
  assert.match(chromeCanvasPreviewCss(), /data-yb-chrome/)
})

test('builds the locked chrome frame for the selected site layout mode', () => {
  const copyrightFrame = chromeFrameTemplate('<section class="hero"></section>', 'HEADER_COPYRIGHT')
  const adminFrame = chromeFrameTemplate('<section class="hero"></section>', 'ADMIN')

  assert.match(copyrightFrame, /site-layout-copyright/)
  assert.doesNotMatch(copyrightFrame, /class="site-layout-footer"/)
  assert.match(adminFrame, /site-layout-frame/)
  assert.match(adminFrame, /site-admin-sidebar/)
  assert.match(adminFrame, /layout-admin/)
  assert.match(chromeCanvasPreviewCss('ADMIN'), /site-admin-sidebar/)
})

test('allows only style actions for locked chrome targets', () => {
  assert.equal(isChromeStyleAction('set-css'), true)
  assert.equal(isChromeStyleAction('append-css'), true)
  assert.equal(isChromeStyleAction('set-styles'), true)
  assert.equal(isChromeStyleAction('set-html'), false)
  assert.equal(isChromeStyleAction('remove-selected'), false)
  assert.equal(isChromeStyleAction('load-project'), false)
})
