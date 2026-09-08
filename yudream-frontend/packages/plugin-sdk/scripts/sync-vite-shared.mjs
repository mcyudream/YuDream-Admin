import { readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const packageDir = resolve(scriptDir, '..')

const viteSharedSource = await readFile(resolve(packageDir, 'src/vite-shared.ts'), 'utf8')
// 根入口被 Node 直接加载，相对 URL 需改指 src/ 下的宿主桥接模块
const viteSharedEntry = viteSharedSource.replaceAll("new URL('./", "new URL('./src/")
const viteSharedDeclaration = `export declare function yuDreamPluginSharedAliases(): {
  vue: string
  'vue-router': string
  '@yudream/components': string
}
`

const unoConfigSource = await readFile(resolve(packageDir, 'src/uno-config.ts'), 'utf8')
// uno-config 入口不引用包内相对文件，去掉类型标注即可作为纯 ESM 发布
const unoConfigEntry = unoConfigSource
  .replace(/^import type .*\n/gm, '')
  .replace(/: YuDreamPluginUnoCssOptions = \{\}/g, ' = {}')
  .replace(/\): UserConfig \{/g, ') {')
  .replace(/^export interface YuDreamPluginUnoCssOptions \{[^]*?\n\}\n\n/gm, '')
const unoConfigDeclaration = `import type { UserConfig } from 'unocss'
import type { Plugin } from 'vite'

export interface YuDreamPluginUnoCssOptions {
  extraContent?: Array<string | RegExp>
}

export declare function yuDreamPluginUnoConfig(options?: YuDreamPluginUnoCssOptions): UserConfig
export declare function yuDreamPluginUnoCss(options?: YuDreamPluginUnoCssOptions): Plugin[]
`

await writeFile(resolve(packageDir, 'vite-shared.js'), viteSharedEntry, 'utf8')
await writeFile(resolve(packageDir, 'vite-shared.d.ts'), viteSharedDeclaration, 'utf8')
await writeFile(resolve(packageDir, 'uno.config.js'), unoConfigEntry, 'utf8')
await writeFile(resolve(packageDir, 'uno.config.d.ts'), unoConfigDeclaration, 'utf8')
