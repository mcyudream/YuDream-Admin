import assert from 'node:assert/strict'
import test from 'node:test'
import { findThemeBrandDeclarationOffsets, resolvePluginThemeCssSources, scanBrandTokenUsage } from './brand-token-scanner.mjs'

test('negative, sibling, attribute text and mixed grouping cannot imitate a SITE anchor', () => {
  const css = ':not(.site-page){--primary-6: 1 2 3} .site-page + .dark{--primary-6: 1 2 3} :is(.site-page,body){--primary-6: 1 2 3} [data-label=".site-page"]{--primary-6: 1 2 3}'
  assert.equal(findThemeBrandDeclarationOffsets(css, 'SITE').size, 0)
  assert.equal(scanBrandTokenUsage(css).length, 4)
})

test('only declarations in explicit SITE theme selectors may define brand-step variables', () => {
  const css = `
.site-page, .site-chrome { --primary-6: 78 122 46; }
@media (prefers-color-scheme: dark) {
  html[data-accent='red'] :is(.site-page, .site-chrome) { --primary-6: 103 194 58; }
}
.admin-page { --primary-6: 103 194 58; }
.site-page .admin-page { --primary-6: 103 194 58; }
`
  const allowed = findThemeBrandDeclarationOffsets(css, 'SITE')
  const findings = scanBrandTokenUsage(css, { allowedDeclarationOffsets: allowed })

  assert.equal(allowed.size, 2)
  assert.equal(findings.length, 2)
  assert.ok(findings.every(finding => css.slice(finding.index).startsWith('--primary-6')))
})

test('theme declarations do not exempt business consumption or global/cross-domain selectors', () => {
  const css = `
.site-page { --primary-6: 78 122 46; color: rgb(var(--primary-6)); }
:root { --primary-6: 78 122 46; }
.dark { --primary-6: 78 122 46; }
html[data-accent='red'] :is(.site-page, .admin-page) { --primary-6: 103 194 58; }
html:has(.site-page) { --primary-6: 103 194 58; }
`
  const allowed = findThemeBrandDeclarationOffsets(css, 'SITE')
  const findings = scanBrandTokenUsage(css, { allowedDeclarationOffsets: allowed })

  assert.equal(allowed.size, 1)
  assert.equal(findings.length, 5)
  assert.ok(findings.some(finding => finding.index === css.indexOf('var(--primary-6)') + 4))
})

test('SITE theme declarations stay in SITE selectors and ADMIN theme declarations stay at root selectors', () => {
  const adminCss = ':root { --primary-6: 78 122 46; } .dark { --primary-6: 103 194 58; } .site-page { --primary-6: 1 2 3; }'
  const allowed = findThemeBrandDeclarationOffsets(adminCss, 'ADMIN')
  assert.equal(scanBrandTokenUsage(adminCss, { allowedDeclarationOffsets: allowed }).length, 1)
  assert.equal(findThemeBrandDeclarationOffsets(adminCss, 'UNKNOWN').size, 0)
})

test('source resolution requires matching single-scope metadata and the bundled style asset', () => {
  const javaSource = `@PluginTheme(code = "neco-pixel", scopes = {PluginThemeScope.SITE}, styles = {"style.css"}) class Theme {}`
  const args = {
    pluginCode: 'neco-pixel',
    javaSources: [javaSource],
    entrySource: "import './theme.css'\nimport './business.css'\n",
    viteConfigSource: "build: { lib: { cssFileName: 'style' } }",
  }

  assert.deepEqual(resolvePluginThemeCssSources(args), [{ path: 'src/theme.css', scope: 'SITE' }])
  const adminSource = javaSource.replace('PluginThemeScope.SITE', 'PluginThemeScope.ADMIN')
  assert.deepEqual(resolvePluginThemeCssSources({ ...args, javaSources: [adminSource] }), [{ path: 'src/theme.css', scope: 'ADMIN' }])
  assert.deepEqual(resolvePluginThemeCssSources({ ...args, javaSources: [javaSource, adminSource] }), [])
  assert.deepEqual(resolvePluginThemeCssSources({ ...args, javaSources: [javaSource.replace('neco-pixel', 'other-plugin')] }), [])
  assert.deepEqual(resolvePluginThemeCssSources({ ...args, entrySource: "import './business.css'" }), [])
  assert.deepEqual(resolvePluginThemeCssSources({ ...args, viteConfigSource: "cssFileName: 'styles'" }), [])
})
