import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../..', import.meta.url))
const components = path.join(root, 'yudream-frontend/packages/components')
const canonicalPath = path.join(root, 'docs/yd-ui-guidelines.md')
const canonical = readFileSync(canonicalPath, 'utf8').replaceAll('\r\n', '\n').trimEnd()
const resolver = readFileSync(path.join(components, 'resolver.ts'), 'utf8')
function arrayNames(name) {
  const block = resolver.match(new RegExp(`const ${name} = \\[([\\s\\S]*?)\\] as const`))?.[1]
  if (!block) {
    throw new Error(`Cannot read resolver ${name}`)
  }
  return [...block.matchAll(/'([A-Z0-9]+)'/gi)].map(match => match[1])
}
const names = [...new Set([
  ...arrayNames('BASIC_COMPONENT_NAMES').map(name => name.replace(/^Fa/, 'Yd')),
  ...arrayNames('YD_COMPONENT_NAMES'),
])]
const catalog = canonical.split('## 7. 已公开的组件目录')[1]?.split('### 7.1')[0] ?? ''
const documented = new Set([...catalog.matchAll(/`(Yd[A-Za-z0-9]+)`/g)].map(match => match[1]))
const exportSource = ['src/index.ts', 'src/adapters/index.ts', 'src/extended/index.ts', 'src/ai/index.ts', 'src/ui/index.ts', 'src/technical/index.ts']
  .filter(file => existsSync(path.join(components, file)))
  .map(file => readFileSync(path.join(components, file), 'utf8'))
  .join('\n')
const bridge = readFileSync(path.join(root, 'yudream-frontend/packages/plugin-sdk/src/host-components.ts'), 'utf8')
const missing = names.filter(name => !documented.has(name) || !new RegExp(`\\b${name}\\b`).test(exportSource) || !new RegExp(`\\b${name}\\b`).test(bridge))
const unknown = [...documented].filter(name => !names.includes(name))
if (missing.length || unknown.length) {
  throw new Error(`YD catalog/export/SDK mismatch: missing=${missing.join(',')} unknown=${unknown.join(',')}`)
}

const preamble = '本文件是插件仓的同步副本，权威来源为宿主仓 `yudream-admim` 的 `docs/yd-ui-guidelines.md`。组件选型、能力边界或视觉规则变更时，先更新宿主权威文档，并在同一工作项同步本副本、两仓 AGENTS 与相关 skill/reference；不要在副本独立扩展公共组件契约。\n\n适用范围为 Vue Web 宿主、共享业务 UI 与插件。React Native 插件不导入 Vue YD 组件，而由独立的 `@yudream/plugin-mobile-ui` 适配器承载对应的视觉、状态与交互原则；未具备的移动能力先在该适配层封装。下文组件目录描述宿主公开源码的目标契约，不能证明插件当前安装版本已含这些入口。插件接入前必须确认 components 与 SDK 都已发布、验签并由宿主共享桥实际提供；不修改为未发布版本，不复制宿主代码，不提交本地宿主路径别名。'
const pluginText = `${canonical.replace('# YD 视觉与组件使用规范\n\n', `# YD 视觉与组件使用规范\n\n${preamble}\n\n`)
  .replace(/\[[^\]]+\]\((frontend-appearance|yd-components|yd-agent-workbench|yd-form-components|yd-select-components)\.md\)/g, (_, name) => `宿主仓的 \`docs/${name}.md\``)}\n`
const explicit = process.argv.find(argument => argument.startsWith('--plugins='))?.slice('--plugins='.length)
const pluginRoot = explicit ? path.resolve(explicit) : path.resolve(root, '..', 'yudream-admin-plugins')
const target = path.join(pluginRoot, 'docs/yd-ui-guidelines.md')
if (existsSync(path.join(pluginRoot, 'AGENTS.md'))) {
  if (process.argv.includes('--check')) {
    if (!existsSync(target) || readFileSync(target, 'utf8').replaceAll('\r\n', '\n') !== pluginText) {
      throw new Error('Plugin YD guideline mirror is stale; run pnpm sync:ui-docs')
    }
  }
  else {
    writeFileSync(target, pluginText, 'utf8')
  }
}
else if (explicit) {
  throw new Error('Plugin repository not found')
}
console.log(`[YD docs] ${names.length} components match docs, exports and SDK; plugin mirror ${existsSync(target) ? 'checked' : 'not mounted'}.`)
