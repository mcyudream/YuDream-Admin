#!/usr/bin/env node
// The syntax scanner is maintained with the host's YD contract documentation.
import { execFileSync } from 'node:child_process'
import { existsSync, globSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { findThemeBrandDeclarationOffsets, resolvePluginThemeCssSources, scanBrandTokenUsage } from './brand-token-scanner.mjs'
import { scanUiSyntax } from './ui-source-scanner.mjs'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const root = path.resolve(frontend, '..')
const require = createRequire(import.meta.url)
const resolver = readFileSync(require.resolve('@yudream/components/resolver'), 'utf8')
const publicNames = new Set([...resolver.matchAll(/'((?:Fa|Yd|useYd)[A-Za-z0-9]+)'/g)].map(match => match[1].replace(/^Fa/, 'Yd')))
const thirdPartySources = ['@arco-design/', 'reka-ui', 'radix-vue', 'shadcn-vue', 'element-plus', 'ant-design-vue', 'naive-ui', 'vuetify', 'primevue', 'bootstrap-vue', 'tdesign-vue']
const baselinePath = path.join(root, 'ci/yd-ui-baseline.json')
const source = process.argv.find(argument => argument.startsWith('--source=git:'))?.slice('--source=git:'.length)
if (source && (!/^[A-Za-z0-9_./-]+$/.test(source) || source.startsWith('-'))) {
  throw new Error('Invalid git source reference')
}
const files = source
  ? execFileSync('git', ['ls-tree', '-r', '--name-only', source, '--', 'yudream-frontend/packages'], { cwd: root, encoding: 'utf8' })
      .split(/\r?\n/).map(file => file.replace(/^yudream-frontend\//, ''))
  : ['vue', 'ts', 'tsx', 'css', 'scss'].flatMap(extension => globSync(`packages/plugin-*/src/**/*.${extension}`, { cwd: frontend, exclude: ['**/node_modules/**'] }))
      .map(file => file.replaceAll(path.sep, '/'))
const webFiles = files.filter(file => /^packages\/plugin-[^/]+\/src\/.*\.(?:vue|ts|tsx|css|scss)$/.test(file)
  && !/^packages\/plugin-(?:mobile-ui|[^/]+-mobile)\//.test(file)).sort()
const violations = []
const themeAssetCache = new Map()
function readRepositoryFile(file) {
  if (source) {
    try {
      return execFileSync('git', ['show', `${source}:${file}`], { cwd: root, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 })
    }
    catch {
      return undefined
    }
  }
  const absolute = path.join(root, file)
  return existsSync(absolute) ? readFileSync(absolute, 'utf8') : undefined
}

function declaredSiteThemeAssets(pluginCode) {
  if (themeAssetCache.has(pluginCode)) {
    return themeAssetCache.get(pluginCode)
  }
  const javaRoot = `yudream-plugins/yudream-plugin-${pluginCode}/src/main/java`
  const javaFiles = source
    ? execFileSync('git', ['ls-tree', '-r', '--name-only', source, '--', javaRoot], { cwd: root, encoding: 'utf8' }).split(/\r?\n/).filter(file => file.endsWith('.java'))
    : globSync(`${javaRoot}/**/*.java`, { cwd: root })
  const assets = resolvePluginThemeCssSources({
    pluginCode,
    javaSources: javaFiles.map(readRepositoryFile).filter(Boolean),
    entrySource: readRepositoryFile(`yudream-frontend/packages/plugin-${pluginCode}/src/index.ts`) ?? '',
    viteConfigSource: readRepositoryFile(`yudream-frontend/packages/plugin-${pluginCode}/vite.config.ts`) ?? '',
  })
  themeAssetCache.set(pluginCode, assets)
  return assets
}

for (const file of webFiles) {
  const content = source
    ? execFileSync('git', ['show', `${source}:yudream-frontend/${file}`], { cwd: root, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 })
    : readFileSync(path.join(frontend, file), 'utf8')
  const lineOf = index => content.slice(0, index).split('\n').length
  if (!/\.(?:css|scss)$/.test(file)) {
    for (const finding of scanUiSyntax(content, file, { publicNames, thirdPartySources, requireResponsiveTables: true })) {
      violations.push({ file, rule: finding.rule, message: finding.message, line: lineOf(finding.index) })
    }
  }
  const pluginMatch = /^packages\/plugin-([^/]+)\/(.+)$/.exec(file)
  const siteThemeAssets = pluginMatch ? declaredSiteThemeAssets(pluginMatch[1]) : []
  const themeAsset = siteThemeAssets.find(asset => asset.path === pluginMatch?.[2])
  const allowedDeclarationOffsets = /\.(?:css|scss)$/.test(file) && themeAsset
    ? findThemeBrandDeclarationOffsets(content, themeAsset.scope)
    : new Set()
  for (const match of scanBrandTokenUsage(content, { allowedDeclarationOffsets })) {
    violations.push({ file, rule: 'no-brand-color-token', message: `业务样式禁止私有品牌色令牌 ${match.token}`, line: lineOf(match.index) })
  }
}
const report = { source: source ? `git:${source}` : 'working-tree', summary: { filesScanned: webFiles.length, violationCount: violations.length }, violations }
writeFileSync(path.join(frontend, 'audit-report.json'), `${JSON.stringify(report, null, 2)}\n`)
console.log(`[audit:ui] ${webFiles.length} Web source files, ${violations.length} YD contract findings; audit-report.json contains full details.`)
const counts = {}
for (const finding of violations) {
  const key = JSON.stringify([finding.file, finding.rule, finding.message])
  counts[key] = (counts[key] ?? 0) + 1
}
if (process.argv.includes('--write-baseline')) {
  if (!source) {
    throw new Error('Baseline requires --source=git:HEAD; working-tree violations cannot be accepted.')
  }
  const sourceCommit = execFileSync('git', ['rev-parse', source], { cwd: root, encoding: 'utf8' }).trim()
  writeFileSync(baselinePath, `${JSON.stringify({ sourceCommit, findings: counts }, null, 2)}\n`)
}
else if (process.argv.includes('--baseline')) {
  if (!existsSync(baselinePath)) {
    throw new Error('Missing initial migration baseline')
  }
  const baseline = JSON.parse(readFileSync(baselinePath, 'utf8')).findings ?? {}
  const regressions = Object.entries(counts).filter(([key, count]) => count > (baseline[key] ?? 0))
  for (const [key, count] of regressions.slice(0, 12)) {
    console.error(`[audit:ui] ${key} +${count - (baseline[key] ?? 0)}`)
  }
  if (regressions.length) {
    console.error(`[audit:ui] ${regressions.length} new/increased finding types; migration gate failed.`)
    process.exitCode = 1
  }
  else {
    console.log('[audit:ui] No new violations relative to the committed migration baseline.')
  }
}
else if (violations.length) {
  process.exitCode = 1
}
