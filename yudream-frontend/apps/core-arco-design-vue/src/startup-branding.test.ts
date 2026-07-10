import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, it } from 'node:test'
import { fileURLToPath } from 'node:url'
import { applyStartupBranding, initializeStartupBranding } from './startup-branding'

describe('startup branding', () => {
  it('applies the public site name to the document title and loading name', () => {
    const loadingName = { textContent: '' }
    const document = {
      title: '正在加载',
      querySelector: (selector: string) => selector === '.loading-container .name' ? loadingName : null,
    }

    applyStartupBranding(document, '自定义站点')

    assert.equal(document.title, '自定义站点')
    assert.equal(loadingName.textContent, '自定义站点')
  })

  it('keeps the static shell neutral before public settings load', () => {
    const appDir = fileURLToPath(new URL('..', import.meta.url))
    const indexHtml = readFileSync(`${appDir}/index.html`, 'utf8')
    const loadingHtml = readFileSync(`${appDir}/loading.html`, 'utf8')

    assert.match(indexHtml, /<title>正在加载<\/title>/)
    assert.doesNotMatch(indexHtml, /VITE_APP_TITLE|YuDream/i)
    assert.doesNotMatch(loadingHtml, /VITE_APP_TITLE|YuDream/i)
  })

  it('continues startup when public settings do not respond', async () => {
    let applied = false

    await initializeStartupBranding(
      () => new Promise(() => {}),
      () => applied = true,
      1,
    )

    assert.equal(applied, true)
  })
})
