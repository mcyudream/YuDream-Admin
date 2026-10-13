import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
// eslint-disable-next-line test/no-import-node-test
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
    // 启动脚本里的主题缓存键和 data-yudream-* 标记不是用户可见的品牌文案。
    const staticShell = indexHtml.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')

    assert.match(indexHtml, /<title>正在加载<\/title>/)
    assert.doesNotMatch(staticShell, /VITE_APP_TITLE|YuDream/i)
    assert.doesNotMatch(loadingHtml, /VITE_APP_TITLE|YuDream/i)
    assert.doesNotMatch(loadingHtml, /loading\.gif|cubic-11|Minecraft-Tenv2/)
    assert.match(loadingHtml, /class="square"/)
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
