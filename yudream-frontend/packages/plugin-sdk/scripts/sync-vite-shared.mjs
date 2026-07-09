import { readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const packageDir = resolve(scriptDir, '..')
const sourcePath = resolve(packageDir, 'src/vite-shared.ts')
const outputJsPath = resolve(packageDir, 'vite-shared.js')
const outputDtsPath = resolve(packageDir, 'vite-shared.d.ts')

const source = await readFile(sourcePath, 'utf8')
const rootEntry = source.replaceAll("new URL('./", "new URL('./src/")
const declaration = `export declare function yuDreamPluginSharedAliases(): {
  vue: string
  'vue-router': string
  '@yudream/components': string
}
`

await writeFile(outputJsPath, rootEntry, 'utf8')
await writeFile(outputDtsPath, declaration, 'utf8')
