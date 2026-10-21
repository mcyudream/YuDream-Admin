import type { SiteDocument } from '../types/site-studio'
import assert from 'node:assert/strict'
import test from 'node:test'
import { createStudioDocument } from './site-studio'
import { compileStudioPublicSkin } from './site-studio-skin'
import { validateStudioDocument } from './site-studio-validation'

function skinned(): SiteDocument {
  const doc = createStudioDocument('皮肤')
  doc.theme.tokens = { gs: '2', shadow: '2px 2px 0 #000', unused: '#fff' }
  doc.theme.classes = [{
    name: 'btn',
    styles: { 'background': '#707070', 'border': 'calc(3px*var(--gs)) solid transparent', 'text-shadow': 'var(--shadow)', 'padding': '0 18px', 'min-height': '40px' },
    states: { hover: { filter: 'brightness(1.2)' } },
  }]
  doc.features = {
    publicSkin: { chrome: true, dark: true, background: '#000000', surface: '#111111', text: '#ffffff', muted: '#aaaaaa', border: '#333333', primary: '#3c8527', primaryText: '#ffffff', classes: { button: 'btn' } },
  }
  return doc
}

test('public skin reuses theme class visuals for hosted pages without layout properties', () => {
  const doc = skinned()
  validateStudioDocument(doc)
  const css = compileStudioPublicSkin(doc)
  assert.match(css, /html \.studio-shadow-layout :is\(\[data-slot="button"\][^{]*\)\{background:#707070 !important;border:calc\(3px\*var\(--gs\)\) solid transparent !important;text-shadow:var\(--shadow\) !important\}/)
  assert.match(css, /\[data-slot="tabs-trigger"\]\):hover\{filter:brightness\(1\.2\) !important\}/)
  // 只带出被引用的主题变量，且限定在嵌入区域
  assert.match(css, /html \.studio-shadow-layout\{--gs:2;--shadow:2px 2px 0 #000\}/)
  assert.doesNotMatch(css, /--unused/)
  assert.doesNotMatch(css, /padding:0 18px|min-height:40px/)
})

test('public skin class mapping must reference existing theme classes and known roles', () => {
  const unknown = skinned()
  unknown.features!.publicSkin!.classes = { panel: 'missing' }
  assert.throws(() => validateStudioDocument(unknown), /已有主题样式类/)
  const role = skinned()
  ;(role.features!.publicSkin!.classes as Record<string, string>).footer = 'btn'
  assert.throws(() => validateStudioDocument(role), /未知字段/)
})
