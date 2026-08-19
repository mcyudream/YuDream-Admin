import assert from 'node:assert/strict'
import test from 'node:test'
import {
  canvasFitZoom,
  cmsCanvasDevices,
  chromeCssKey,
  chromeCanvasPreviewCss,
  chromeFrameTemplate,
  chromeRuntimeCss,
  chromeTemplate,
  extractChromeCss,
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
  assert.match(chromeTemplate('header'), /summary class="ghost site-layout-header__action"/)
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

test('shares chrome runtime css without editor-only markers', () => {
  const customCss = '.site-layout-header { background: #080817; }'
  const runtimeCss = chromeRuntimeCss('HEADER_FOOTER', customCss)

  assert.match(chromeCanvasPreviewCss(), /content:\s*attr\(data-yb-chrome\)/)
  assert.doesNotMatch(runtimeCss, /content:\s*attr\(data-yb-chrome\)/)
  assert.ok(runtimeCss.indexOf('--yb-site-bg') < runtimeCss.indexOf(customCss))
  assert.match(runtimeCss, /font-size:\s*16px/)
})

test('lets the authenticated account button inherit custom header styling', () => {
  const runtimeCss = chromeRuntimeCss()
  const header = chromeTemplate('header')

  assert.match(header, /site-layout-header__auth[\s\S]*site-layout-header__account[\s\S]*summary class="ghost/)
  assert.match(runtimeCss, /site-layout-header__account summary[^{]*\{[^}]*background:\s*transparent/i)
  assert.match(runtimeCss, /site-layout-header__account summary[^{]*\{[^}]*color:\s*inherit/i)
})

test('keeps public and canvas chrome palettes identical by default', () => {
  const previewCss = chromeCanvasPreviewCss()

  assert.match(previewCss, /--yb-site-header-bg:\s*rgba\(255, 255, 255, 0\.94\)/)
  assert.match(previewCss, /html\.dark, body\.dark/)
  assert.match(previewCss, /--yb-site-header-bg:\s*rgba\(15, 23, 42, 0\.86\)/)
})

test('extracts and prioritizes chrome CSS stored inside page CSS', () => {
  const pageCss = `.yb-ai-hero { color: white; }\n.site-layout-header { background: #080817; }\n@media (max-width: 760px) { .site-layout-header__nav { gap: 2px; } .yb-ai-hero { gap: 10px; } }`
  const chromeOnly = extractChromeCss(pageCss)
  const runtimeCss = chromeRuntimeCss('HEADER_FOOTER', chromeOnly)

  assert.match(chromeOnly, /\.site-layout-header \{ background: #080817; \}/)
  assert.match(chromeOnly, /\.site-layout-header__nav \{ gap: 2px; \}/)
  assert.doesNotMatch(chromeOnly, /yb-ai-hero/)
  assert.match(runtimeCss, /main\.site-page \.site-layout-header \{ background: #080817; \}/)
})

test('uses stable CMS device widths and calculates a fit zoom', () => {
  assert.deepEqual(cmsCanvasDevices(), [
    { id: 'desktop', name: '桌面', width: '1440px' },
    { id: 'tablet', name: '平板', width: '768px' },
    { id: 'mobile', name: '手机', width: '390px' },
  ])
  assert.equal(canvasFitZoom(1200, 1440), 81)
  assert.equal(canvasFitZoom(1600, 1440), 100)
  assert.equal(canvasFitZoom(300, 1440), 25)
})
