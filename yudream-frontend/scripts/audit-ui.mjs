#!/usr/bin/env node
/**
 * 前端 UI 审查工具：扫描 apps/* 源码中
 *  1. 存在 Fa 等价物却仍使用的 Arco 原生组件（模板 <a-*> 标签 + 脚本导入）
 *  2. 业务样式直接使用 Arco 品牌色阶梯令牌（--primary-N）
 * 输出 audit-report.json 到 yudream-frontend 根目录，供 vite dev 中间件
 * /__yudream-devtools/audit.json 读取并展示在插件开发者调试抽屉的「前端审查」页。
 *
 * 与 eslint 规则 yudream/prefer-fa-component、yudream/no-brand-color-token 共用映射与判定，
 * 此处独立实现文本扫描以覆盖 eslint 不处理的 css/scss 文件。
 */
import { existsSync, globSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ARCO_SCRIPT_API_MAP, ARCO_TAG_TO_FA_MAP, ARCO_TO_FA_MAP } from '../eslint-rules/fa-component-map.mjs'

const FRONTEND_ROOT = fileURLToPath(new URL('..', import.meta.url))
const REPORT_PATH = path.join(FRONTEND_ROOT, 'audit-report.json')

const SCAN_GLOBS = [
  'apps/*/src/**/*.vue',
  'apps/*/src/**/*.ts',
  'apps/*/src/**/*.tsx',
  'apps/*/src/**/*.css',
  'apps/*/src/**/*.scss',
]

const TAG_PATTERN = /<\s*a-([a-z][a-z0-9]*(?:-[a-z0-9]+)*)/g
const PASCAL_TAG_PATTERN = /<\s*A([A-Z][A-Za-z0-9]*)/g
const ARCO_IMPORT_PATTERN = /import\s*(?:type\s*)?\{([^}]*)\}\s*from\s*['"]@arco-design\/web-vue['"]/g
const BRAND_TOKEN_PATTERN = /--primary-\d/g

function lineOf(text, index) {
  let line = 1
  for (let i = 0; i < index; i++) {
    if (text[i] === '\n') {
      line++
    }
  }
  return line
}

function scanFile(relativePath, text) {
  const violations = []
  const arcoUsage = {}
  const isScript = /\.(?:vue|tsx?)$/.test(relativePath)

  if (isScript) {
    for (const match of text.matchAll(TAG_PATTERN)) {
      const tag = match[1]
      arcoUsage[tag] = (arcoUsage[tag] ?? 0) + 1
      const fa = ARCO_TAG_TO_FA_MAP[tag]
      if (fa) {
        violations.push({
          file: relativePath,
          rule: 'prefer-fa-component',
          message: `优先使用 ${fa} 替代 Arco 原生组件 <a-${tag}>，保持组件体系与主题统一。如确需 Arco 原生组件，请 eslint-disable 本行并注明原因。`,
          line: lineOf(text, match.index),
        })
      }
    }
    for (const match of text.matchAll(PASCAL_TAG_PATTERN)) {
      const name = match[1]
      const fa = ARCO_TO_FA_MAP[name]
      if (fa) {
        const tag = name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
        arcoUsage[tag] = (arcoUsage[tag] ?? 0) + 1
        violations.push({
          file: relativePath,
          rule: 'prefer-fa-component',
          message: `优先使用 ${fa} 替代 Arco 原生组件 <a-${tag}>，保持组件体系与主题统一。如确需 Arco 原生组件，请 eslint-disable 本行并注明原因。`,
          line: lineOf(text, match.index),
        })
      }
    }
    for (const match of text.matchAll(ARCO_IMPORT_PATTERN)) {
      for (const rawName of match[1].split(',')) {
        const name = rawName.trim().split(/\s+as\s+/)[0]?.replace(/^type\s+/, '').trim()
        const fa = (name && (ARCO_TO_FA_MAP[name] ?? ARCO_SCRIPT_API_MAP[name])) || null
        if (fa) {
          violations.push({
            file: relativePath,
            rule: 'prefer-fa-component',
            message: `优先使用 ${fa} 替代从 @arco-design/web-vue 导入的 ${name}。如确需 Arco 原生能力，请 eslint-disable 本行并注明原因。`,
            line: lineOf(text, match.index),
          })
        }
      }
    }
  }

  for (const match of text.matchAll(BRAND_TOKEN_PATTERN)) {
    violations.push({
      file: relativePath,
      rule: 'no-brand-color-token',
      message: `业务样式禁止直接使用品牌色令牌 ${match[0]}，主题与主色由后台主题配置统一控制，请改用中性语义变量（--color-bg-* / --color-text-* / --color-border-* / --color-fill-*）。`,
      line: lineOf(text, match.index),
    })
  }

  return { violations, arcoUsage }
}

function main() {
  const files = SCAN_GLOBS.flatMap(pattern =>
    globSync(pattern, { cwd: FRONTEND_ROOT, exclude: ['**/node_modules/**'] }),
  ).sort()

  const violations = []
  const arcoUsage = {}
  let filesScanned = 0

  for (const file of files) {
    const absolute = path.join(FRONTEND_ROOT, file)
    if (!existsSync(absolute)) {
      continue
    }
    filesScanned++
    const text = readFileSync(absolute, 'utf-8')
    const result = scanFile(file.replaceAll(path.sep, '/'), text)
    violations.push(...result.violations)
    for (const [tag, count] of Object.entries(result.arcoUsage)) {
      arcoUsage[tag] = (arcoUsage[tag] ?? 0) + count
    }
  }

  const report = {
    generatedAt: new Date().toISOString(),
    summary: {
      filesScanned,
      violationCount: violations.length,
    },
    arcoComponentUsage: Object.fromEntries(
      Object.entries(arcoUsage).sort((a, b) => b[1] - a[1]),
    ),
    violations,
  }

  writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`)
  console.log(`[audit:ui] 扫描 ${filesScanned} 个文件，发现 ${violations.length} 条审查警告，报告已写入 audit-report.json`)
  if (Object.keys(arcoUsage).length > 0) {
    console.log('[audit:ui] Arco 组件使用分布：')
    for (const [tag, count] of Object.entries(report.arcoComponentUsage)) {
      console.log(`  <a-${tag}> × ${count}`)
    }
  }
}

main()
