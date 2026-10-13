#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
/** Strict YD-only source audit. Report fields remain compatible with devtools. */
import { existsSync, globSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { isImplementationBoundary, scanUiSource } from '../eslint-rules/fa-component-map.mjs'
import { scanBrandTokenUsage } from './brand-token-scanner.mjs'

const FRONTEND_ROOT = fileURLToPath(new URL('..', import.meta.url))
const REPORT_PATH = path.join(FRONTEND_ROOT, 'audit-report.json')
const DEFAULT_BASELINE_PATH = path.join(FRONTEND_ROOT, 'eslint-rules/yd-only-baseline.json')
const SCAN_GLOBS = [
  'apps/core-arco-design-vue/src/**/*.vue',
  'apps/core-arco-design-vue/src/**/*.ts',
  'apps/core-arco-design-vue/src/**/*.tsx',
  'apps/core-arco-design-vue/src/**/*.css',
  'apps/core-arco-design-vue/src/**/*.scss',
  'apps/component-showcase/src/**/*.vue',
  'apps/component-showcase/src/**/*.ts',
  'apps/component-showcase/src/**/*.tsx',
  'apps/component-showcase/src/**/*.css',
  'apps/component-showcase/src/**/*.scss',
]
const gitSourceOption = process.argv.find(argument => argument.startsWith('--source=git:'))
const gitRef = gitSourceOption?.slice('--source=git:'.length)
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '..')
if (gitSourceOption && (!gitRef || !/^[\w./-]+$/.test(gitRef) || gitRef.startsWith('-'))) {
  throw new Error('Invalid git source reference')
}

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
  const violations = scanUiSource(text, relativePath).map(item => ({
    file: relativePath,
    rule: item.rule,
    message: item.message,
    line: lineOf(text, item.index),
  }))
  if (!isImplementationBoundary(relativePath)) {
    for (const match of scanBrandTokenUsage(text)) {
      violations.push({
        file: relativePath,
        rule: 'no-brand-color-token',
        message: `业务样式禁止直接使用品牌色令牌 ${match.token}，请改用中性语义变量。`,
        line: lineOf(text, match.index),
      })
    }
  }
  return violations
}

const files = gitRef
  ? execFileSync('git', ['ls-tree', '-r', '--name-only', gitRef, '--', 'yudream-frontend/apps'], { cwd: REPO_ROOT, encoding: 'utf8' })
      .split(/\r?\n/)
      .filter(file => /^yudream-frontend\/apps\/(?:core-arco-design-vue|component-showcase)\/src\/.*\.(?:vue|ts|tsx|css|scss)$/.test(file))
      .map(file => file.slice('yudream-frontend/'.length))
      .sort()
  : SCAN_GLOBS.flatMap(pattern => globSync(pattern, { cwd: FRONTEND_ROOT, exclude: ['**/node_modules/**'] })).sort()
const violations = []
let filesScanned = 0
for (const file of files) {
  const absolute = path.join(FRONTEND_ROOT, file)
  if (!gitRef && !existsSync(absolute)) {
    continue
  }
  filesScanned++
  const text = gitRef
    ? execFileSync('git', ['show', `${gitRef}:yudream-frontend/${file}`], { cwd: REPO_ROOT, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 })
    : readFileSync(absolute, 'utf8')
  violations.push(...scanFile(file.replaceAll(path.sep, '/'), text))
}

const report = {
  generatedAt: new Date().toISOString(),
  source: gitRef ? `git:${gitRef}` : 'working-tree',
  summary: { filesScanned, violationCount: violations.length },
  arcoComponentUsage: {},
  violations,
}
writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`)
console.log(`[audit:ui] 扫描 ${filesScanned} 个文件，发现 ${violations.length} 条 YD-only 违规，报告已写入 audit-report.json`)

function findingCounts(items) {
  const counts = {}
  for (const item of items) {
    const key = JSON.stringify([item.file, item.rule, item.message])
    counts[key] = (counts[key] ?? 0) + 1
  }
  return counts
}

const baselineOption = process.argv.find(argument => argument.startsWith('--baseline='))
const writeBaseline = process.argv.includes('--write-baseline')
if (writeBaseline) {
  if (!gitRef) {
    throw new Error('基线只能从明确的已提交 Git 版本生成，请指定 --source=git:HEAD；不得接受工作区新增违规。')
  }
  const commit = execFileSync('git', ['rev-parse', gitRef], { cwd: REPO_ROOT, encoding: 'utf8' }).trim()
  writeFileSync(DEFAULT_BASELINE_PATH, `${JSON.stringify({ sourceCommit: commit, findings: findingCounts(violations) }, null, 2)}\n`)
  console.log(`[audit:ui] 已更新严格门禁基线 ${path.relative(FRONTEND_ROOT, DEFAULT_BASELINE_PATH)}`)
}
else if (baselineOption) {
  const baselinePath = path.resolve(FRONTEND_ROOT, baselineOption.slice('--baseline='.length))
  const baseline = JSON.parse(readFileSync(baselinePath, 'utf8')).findings ?? {}
  const current = findingCounts(violations)
  const regressions = Object.entries(current).filter(([key, count]) => count > (baseline[key] ?? 0))
  if (regressions.length) {
    console.error(`[audit:ui] 检出 ${regressions.length} 类基线外新增违规：`)
    for (const [key, count] of regressions) {
      console.error(`  ${key} × ${count - (baseline[key] ?? 0)}`)
    }
    process.exitCode = 1
  }
  else {
    console.log(`[audit:ui] 严格门禁通过，违规数未超过基线 ${Object.values(baseline).reduce((sum, count) => sum + count, 0)}。`)
  }
}
else if (violations.length) {
  process.exitCode = 1
}
