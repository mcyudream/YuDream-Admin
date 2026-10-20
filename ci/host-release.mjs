#!/usr/bin/env node
/**
 * ci/host-release.mjs —— 宿主发版工具（零外部依赖，Node >= 22）
 *
 * 用法：
 *   node ci/host-release.mjs check-changes [--base <ref>] [--json]
 *   node ci/host-release.mjs prepare [--base <ref>] [--rc] [--write] [--json]
 *   node ci/host-release.mjs validate --tag <tag> [--allow-dirty] [--json]
 *   node ci/host-release.mjs build-version --tag <tag> [--dotenv <path>] [--allow-dirty] [--json]
 *
 * 约定（记录 schema 见 release/changes/README.md）：
 *   - 版本：严格 semver 正式版 X.Y.Z / 候选版 X.Y.Z-rc.N（禁止前导零、构建元数据、其他预发布形态）；
 *     tag 形如 v1.5.0、v1.5.0-rc.2，且 tag 指向提交的根 pom yudream.host.version 必须与版本正式段一致。
 *   - 变更记录：release/changes/*.json，level ∈ major|minor|patch|none，聚合最高档位决定升版
 *     （none 视为至少 patch）；notes 聚合为确定性中文 release notes。
 *   - 状态：release/host-release.json 记录 baseline 与已冻结的正式发布（tag 存在后输入不可改）。
 *   - 运行时版本：根 pom yudream.host.build-version 默认 ${yudream.host.version}-dev.local，
 *     裸 mvn 构建不冒充正式版；CI 用 -Dyudream.host.build-version=<build-version 输出> 注入精确版本。
 *
 * 仓库定位：环境变量 HOST_RELEASE_REPO_ROOT，或缺省从当前目录向上找含 pom.xml 的目录。
 * 退出码：0 成功；1 校验失败；2 用法/环境错误。所有失败信息走 stderr（中文）。
 */
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

// ---------------------------------------------------------------------------
// 常量与基础
// ---------------------------------------------------------------------------
const LEVELS = { none: 0, patch: 1, minor: 2, major: 3 }
const OFFICIAL_TAG_RE = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/
const RC_TAG_RE = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)-rc\.([1-9]\d*)$/
const VERSION_PROP = 'yudream.host.version'
const POM_FILE = 'pom.xml'
const STATE_FILE = 'release/host-release.json'
const CHANGES_DIR = 'release/changes'
const NOTES_DIR = 'release/notes'
// 无需变更记录的文件（仅文档/CI/发版辅助的提交自动按 none 处理）
const EXEMPT_FILE_RES = [
  /^docs\//, /^yd-docs\//, /^ci\//, /^release\//, /^\.gitlab-ci\.yml$/,
  /^\.gitignore$/, /^\.gitattributes$/, /^LICENSE$/, /\.md$/,
]
// 发版准备提交只改宿主版本属性时视为 none（打 tag 前的升版提交本身不要求记录）
const PROP_DIFF_LINE_RE =
  /^[+-]\s*(<!--.*-->|<yudream\.host\.(?:version|build-version)>.*<\/yudream\.host\.(?:version|build-version)>)\s*$/

class ReleaseError extends Error {
  constructor(code, message) {
    super(message)
    this.code = code
  }
}
const fail = (code, message) => {
  throw new ReleaseError(code, message)
}

let ROOT = null // 仓库根，首次使用时解析

function resolveRepoRoot() {
  if (process.env.HOST_RELEASE_REPO_ROOT) {
    const dir = path.resolve(process.env.HOST_RELEASE_REPO_ROOT)
    if (!fs.existsSync(path.join(dir, POM_FILE))) {
      fail('E-REPO', `HOST_RELEASE_REPO_ROOT 指向的目录缺少 ${POM_FILE}: ${dir}`)
    }
    return dir
  }
  let dir = process.cwd()
  // eslint-disable-next-line no-constant-condition
  while (true) {
    if (fs.existsSync(path.join(dir, POM_FILE))) return dir
    const up = path.dirname(dir)
    if (up === dir) break
    dir = up
  }
  fail('E-REPO', '无法定位仓库根（未找到 pom.xml）；可在 CI 显式设置 HOST_RELEASE_REPO_ROOT')
}

function git(args, { allowFail = false } = {}) {
  const r = spawnSync('git', args, { cwd: ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  if (r.error) fail('E-GIT', `git 不可用: ${r.error.message}`)
  if (r.status !== 0 && !allowFail) {
    fail('E-GIT', `git ${args.join(' ')} 失败: ${(r.stderr || r.stdout || '').trim()}`)
  }
  return { code: r.status, out: r.stdout || '', err: r.stderr || '' }
}

// ---------------------------------------------------------------------------
// semver（严格：正式版 X.Y.Z / 候选版 X.Y.Z-rc.N）
// ---------------------------------------------------------------------------
export function parseTag(tag) {
  if (typeof tag !== 'string') return null
  let m = OFFICIAL_TAG_RE.exec(tag)
  if (m) {
    return { tag, version: `${m[1]}.${m[2]}.${m[3]}`, major: +m[1], minor: +m[2], patch: +m[3], rc: null }
  }
  m = RC_TAG_RE.exec(tag)
  if (m) {
    return {
      tag, version: `${m[1]}.${m[2]}.${m[3]}-rc.${m[4]}`,
      major: +m[1], minor: +m[2], patch: +m[3], rc: +m[4],
    }
  }
  return null
}

export function parseVersion(version) {
  return version && typeof version === 'string' ? parseTag(`v${version}`) : null
}

function officialPart(p) {
  return `${p.major}.${p.minor}.${p.patch}`
}

// a < b → -1；正式版排在同版本全部 rc 之后
export function compareVersions(a, b) {
  const pa = parseVersion(a)
  const pb = parseVersion(b)
  if (!pa || !pb) fail('E-VERSION', `无法比较版本: ${a} / ${b}`)
  for (const k of ['major', 'minor', 'patch']) {
    if (pa[k] !== pb[k]) return pa[k] < pb[k] ? -1 : 1
  }
  const ar = pa.rc === null ? Infinity : pa.rc
  const br = pb.rc === null ? Infinity : pb.rc
  return ar === br ? 0 : ar < br ? -1 : 1
}

export function bumpVersion(version, level) {
  const p = parseVersion(version)
  if (!p) fail('E-VERSION', `无法解析版本: ${version}`)
  if (level === 'major') return `${p.major + 1}.0.0`
  if (level === 'minor') return `${p.major}.${p.minor + 1}.0`
  return `${p.major}.${p.minor}.${p.patch + 1}`
}

// from -> to 实际代表的升版档位
export function impliedLevel(from, to) {
  const a = parseVersion(from)
  const b = parseVersion(to)
  if (!a || !b) fail('E-VERSION', `无法解析版本: ${from} / ${to}`)
  if (b.major > a.major) return 'major'
  if (b.minor > a.minor) return 'minor'
  if (b.patch > a.patch) return 'patch'
  return 'none'
}

// ---------------------------------------------------------------------------
// git 查询
// ---------------------------------------------------------------------------
function resolveCommit(ref) {
  for (const spec of [`${ref}^{commit}`, `refs/tags/${ref}^{commit}`, `refs/heads/${ref}^{commit}`]) {
    if (git(['rev-parse', '--verify', '--quiet', spec], { allowFail: true }).code === 0) {
      return git(['rev-parse', '--verify', `${spec}`]).out.trim()
    }
  }
  return null
}

function commitExists(hash) {
  return git(['cat-file', '-e', `${hash}^{commit}`], { allowFail: true }).code === 0
}

function isAncestor(a, b) {
  return git(['merge-base', '--is-ancestor', a, b], { allowFail: true }).code === 0
}

function listCommits(base, head) {
  const out = git(['log', '--no-merges', '--pretty=format:%H%x00%P%x00%s', `${base}..${head}`]).out
  return out
    .split('\n')
    .filter((line) => line.length > 0)
    .map((line) => {
      const [hash, parents, subject] = line.split('\0')
      return { hash, parents, subject }
    })
}

function commitFiles(hash) {
  return git(['diff-tree', '--no-commit-id', '--name-only', '-r', hash], { allowFail: true })
    .out.split('\n')
    .filter(Boolean)
}

function isExemptFile(file) {
  return EXEMPT_FILE_RES.some((re) => re.test(file))
}

// 提交是否只改了宿主版本属性（发版准备提交的豁免判定）
function isPomVersionOnlyCommit(hash) {
  const files = commitFiles(hash)
  if (!files.includes(POM_FILE)) return false
  const nonExempt = files.filter((f) => !isExemptFile(f))
  if (nonExempt.length > 1) return false
  const patch = git(['diff-tree', '--no-commit-id', '-p', '-r', hash, '--', POM_FILE]).out
  const changed = patch
    .split('\n')
    .filter((l) => (l.startsWith('+') || l.startsWith('-')) && !l.startsWith('+++') && !l.startsWith('---'))
  return changed.every((l) => PROP_DIFF_LINE_RE.test(l))
}

function readFileAt(commit, file) {
  const r = git(['show', `${commit}:${file}`], { allowFail: true })
  return r.code === 0 ? r.out : null
}

function listChangesFilesAt(commit) {
  const r = git(['ls-tree', '-r', '--name-only', commit, '--', CHANGES_DIR], { allowFail: true })
  if (r.code !== 0) return []
  return r.out.split('\n').filter((f) => f.endsWith('.json'))
}

function listRcTags(officialVersion) {
  const out = git(['tag', '--list', `v${officialVersion}-rc.*`]).out
  return out
    .split('\n')
    .filter(Boolean)
    .map((t) => {
      const p = parseTag(t)
      return p && p.rc !== null ? { tag: t, n: p.rc, commit: resolveCommit(t) } : null
    })
    .filter(Boolean)
    .sort((a, b) => a.n - b.n)
}

// tracked 源码必须干净；未跟踪文件（含被忽略的构建产物）不阻塞
function assertCleanTree() {
  const out = git(['status', '--porcelain']).out
  const dirty = out.split('\n').filter((l) => l.length > 3 && !l.startsWith('??'))
  if (dirty.length > 0) {
    fail(
      'E-DIRTY-TREE',
      `发布要求 tracked 源码干净（未跟踪文件与构建产物忽略），当前有 ${dirty.length} 处已跟踪改动：\n` +
        dirty.slice(0, 10).map((l) => `  ${l}`).join('\n') +
        (dirty.length > 10 ? `\n  ... 共 ${dirty.length} 处` : '') +
        '\n本地演练可用 --allow-dirty 跳过；正式发布必须在干净提交上执行',
    )
  }
}

// ---------------------------------------------------------------------------
// 数据：状态 / 变更记录 / 覆盖分析 / release notes
// ---------------------------------------------------------------------------
function validateState(state, source) {
  if (!state || typeof state !== 'object' || Array.isArray(state) || !state.baseline) {
    fail('E-STATE', `状态文件 ${source} 缺少 baseline`)
  }
  const b = state.baseline
  const bp = parseVersion(b.version)
  if (!bp || bp.rc !== null) fail('E-STATE', `状态文件 ${source}: baseline.version 必须是正式 semver 版本`)
  if (b.tag !== `v${b.version}`) fail('E-STATE', `状态文件 ${source}: baseline.tag 必须是 v${b.version}`)
  if (b.commit !== undefined && !/^[0-9a-f]{40}$/i.test(String(b.commit))) {
    fail('E-STATE', `状态文件 ${source}: baseline.commit 必须是 40 位提交哈希`)
  }
  if (!Array.isArray(state.releases)) fail('E-STATE', `状态文件 ${source}: releases 必须是数组`)
  for (const [i, e] of state.releases.entries()) {
    const where = `状态文件 ${source}: releases[${i}]`
    const ep = parseVersion(e.version)
    if (!ep || ep.rc !== null) fail('E-STATE', `${where}.version 必须是正式 semver 版本`)
    if (e.tag !== `v${e.version}`) fail('E-STATE', `${where}.tag 必须是 v${e.version}`)
    if (typeof e.base !== 'string' || !e.base) fail('E-STATE', `${where}.base 必须是基线 tag`)
    if (typeof e.notesFile !== 'string' || !e.notesFile) fail('E-STATE', `${where}.notesFile 必须是发布说明路径`)
    if (e.commit !== undefined && !/^[0-9a-f]{40}$/i.test(String(e.commit))) {
      fail('E-STATE', `${where}.commit 必须是 40 位提交哈希`)
    }
  }
  return state
}

function parseState(text, source) {
  let raw
  try {
    raw = JSON.parse(text)
  } catch (e) {
    fail('E-STATE', `状态文件 ${source} 不是合法 JSON: ${e.message}`)
  }
  return validateState(raw, source)
}

// 优先读 tag 树里的状态（冻结语义）；老 tag 树没有该文件时回落工作区
function loadStateAt(commit) {
  const at = readFileAt(commit, STATE_FILE)
  if (at !== null) return { state: parseState(at, `${STATE_FILE}@${commit.slice(0, 12)}`), source: 'tag' }
  const file = path.join(ROOT, STATE_FILE)
  if (fs.existsSync(file)) return { state: parseState(fs.readFileSync(file, 'utf8'), `${STATE_FILE}(工作区回落)`), source: 'worktree' }
  fail('E-STATE-MISSING', `缺少状态文件 ${STATE_FILE}（tag 树与工作区均未找到）`)
}

function loadWorkingState() {
  const file = path.join(ROOT, STATE_FILE)
  if (!fs.existsSync(file)) fail('E-STATE-MISSING', `缺少状态文件 ${STATE_FILE}`)
  return parseState(fs.readFileSync(file, 'utf8'), STATE_FILE)
}

export function validateRecordData(rec, name) {
  const where = `变更记录 ${name}`
  if (!rec || typeof rec !== 'object' || Array.isArray(rec)) fail('E-RECORD', `${where}: 不是 JSON 对象`)
  for (const key of ['id', 'level', 'title']) {
    if (typeof rec[key] !== 'string' || !rec[key].trim()) fail('E-RECORD', `${where}: 缺少或非法字段 ${key}`)
  }
  if (!(rec.level in LEVELS)) fail('E-RECORD', `${where}: level 必须是 major|minor|patch|none`)
  if (!Array.isArray(rec.commits) || rec.commits.length < 1) {
    fail('E-RECORD', `${where}: commits 必须是非空数组（列出该记录覆盖的提交哈希，7-40 位）`)
  }
  for (const c of rec.commits) {
    if (!/^[0-9a-f]{7,40}$/i.test(c)) fail('E-RECORD', `${where}: 提交哈希非法: ${c}`)
  }
  if (!Array.isArray(rec.notes) || rec.notes.length < 1 || rec.notes.some((n) => typeof n !== 'string' || !n.trim())) {
    fail('E-RECORD', `${where}: notes 必须是非空字符串数组（中文发布说明条目）`)
  }
  return rec
}

function loadRecordsFromTree(commit) {
  const names = commit ? listChangesFilesAt(commit) : listWorkingChangesFiles()
  const records = []
  for (const name of names) {
    const text = commit ? readFileAt(commit, name) : fs.readFileSync(path.join(ROOT, name), 'utf8')
    let rec
    try {
      rec = JSON.parse(text)
    } catch (e) {
      fail('E-RECORD', `变更记录 ${name} 不是合法 JSON: ${e.message}`)
    }
    records.push({ name, rec: validateRecordData(rec, name) })
  }
  return records
}

function listWorkingChangesFiles() {
  const dir = path.join(ROOT, CHANGES_DIR)
  if (!fs.existsSync(dir)) return []
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => `${CHANGES_DIR}/${f}`)
}

// 窗口分析：base..head 的已提交变更（绝不读工作区 WIP）
function analyzeWindow(baseCommit, headCommit, records) {
  const commits = listCommits(baseCommit, headCommit).map((c) => ({ ...c, files: commitFiles(c.hash) }))
  const coveredByRecord = new Map(records.map((r) => [r.name, new Set()]))
  const windowHashes = commits.map((c) => c.hash)
  // 记录引用的哈希必须真实存在；窗口外的哈希允许（属于更早发布）
  for (const r of records) {
    for (const h of r.rec.commits) {
      const hit = windowHashes.find((w) => w.startsWith(h.toLowerCase()))
      if (hit) coveredByRecord.get(r.name).add(hit)
      else if (!commitExists(h)) fail('E-RECORD-HASH', `变更记录 ${r.name} 引用了不存在的提交: ${h}`)
    }
  }
  const uncovered = []
  const autoNone = []
  for (const c of commits) {
    const covering = records.filter((r) => coveredByRecord.get(r.name).has(c.hash))
    if (covering.length > 0) {
      c.coveredBy = covering.map((r) => r.name)
      continue
    }
    if (c.files.length === 0) {
      c.auto = 'empty'
      autoNone.push(c)
      // 空提交视为豁免
    } else if (c.files.every(isExemptFile)) {
      c.auto = 'exempt'
      autoNone.push(c)
    } else if (isPomVersionOnlyCommit(c.hash)) {
      c.auto = 'pom-version'
      autoNone.push(c)
    } else {
      uncovered.push(c)
    }
  }
  const used = records
    .filter((r) => coveredByRecord.get(r.name).size > 0)
    .map((r) => ({ name: r.name, rec: r.rec, covered: [...coveredByRecord.get(r.name)] }))
  const requiredNum = used.reduce((max, r) => Math.max(max, LEVELS[r.rec.level]), 0)
  const requiredLevel = Object.keys(LEVELS).find((k) => LEVELS[k] === requiredNum)
  return { commits, uncovered, autoNone, used, requiredLevel }
}

// 有效基线：状态 baseline 与「已存在 tag 的冻结发布」中的最高正式版
function effectiveBaseline(state, { excludeVersion } = {}) {
  const live = resolveCommit(state.baseline.tag)
  if (!live) fail('E-BASELINE-MISSING', `基线 tag 不存在: ${state.baseline.tag}`)
  if (state.baseline.commit && state.baseline.commit.toLowerCase() !== live.toLowerCase()) {
    fail('E-BASELINE-INTEGRITY', `baseline.commit 与 tag 实际指向不一致：${state.baseline.tag} 实际指向 ${live.slice(0, 12)}`)
  }
  let best = { version: state.baseline.version, tag: state.baseline.tag, commit: live, origin: 'baseline' }
  for (const e of state.releases) {
    if (excludeVersion && e.version === excludeVersion) continue
    const c = resolveCommit(e.tag)
    if (!c) continue // 尚未打 tag 的冻结发布不参与基线推进
    if (compareVersions(e.version, best.version) > 0) {
      best = { version: e.version, tag: e.tag, commit: c, origin: 'release' }
    }
  }
  return best
}

// 聚合中文 release notes（确定性：不掺时间等环境量，保证可冻结比对）
export function aggregateNotes(version, usedRecords) {
  const sorted = [...usedRecords].sort((a, b) => {
    const lv = LEVELS[b.rec.level] - LEVELS[a.rec.level]
    if (lv !== 0) return lv
    if (a.rec.id !== b.rec.id) return a.rec.id < b.rec.id ? -1 : 1
    return a.name < b.name ? -1 : 1
  })
  const lines = [`# 版本 ${version}`, '']
  for (const r of sorted) {
    lines.push(`- 【${r.rec.level}】${r.rec.title}`)
    for (const n of r.rec.notes) lines.push(`  - ${n}`)
  }
  return lines.join('\n') + '\n'
}

// ---------------------------------------------------------------------------
// pom 属性
// ---------------------------------------------------------------------------
export function readPomVersion(pomText) {
  const m = new RegExp(`<${VERSION_PROP}>([^<]*)</${VERSION_PROP}>`).exec(pomText)
  return m ? m[1].trim() : null
}

export function setPomVersion(pomText, value) {
  const re = new RegExp(`(<${VERSION_PROP}>)[^<]*(</${VERSION_PROP}>)`)
  if (re.test(pomText)) return pomText.replace(re, `$1${value}$2`)
  if (!/<properties>/.test(pomText)) fail('E-POM', `${POM_FILE} 缺少 <properties>，无法写入 ${VERSION_PROP}`)
  return pomText.replace(/<properties>/, `<properties>\n        <${VERSION_PROP}>${value}</${VERSION_PROP}>`)
}

function pomFilesWithProp() {
  const list = git(['ls-files', POM_FILE, '*/pom.xml']).out.split('\n').filter(Boolean)
  return list.filter((f) => readPomVersion(fs.readFileSync(path.join(ROOT, f), 'utf8')) !== null)
}

// ---------------------------------------------------------------------------
// tag 评估：validate / build-version 共用的完整校验
// ---------------------------------------------------------------------------
function evaluateTag(tag, { allowDirty = false } = {}) {
  const parsed = parseTag(tag)
  if (!parsed) {
    fail('E-TAG-FORMAT', `非法标签格式（严格 semver：vX.Y.Z 或 vX.Y.Z-rc.N，禁止前导零/构建元数据）: ${tag}`)
  }
  const commit = resolveCommit(tag)
  if (!commit) fail('E-TAG-MISSING', `tag 不存在: ${tag}`)

  const { state, source: stateSource } = loadStateAt(commit)
  const officialPartStr = officialPart(parsed)
  const isBaselineTag =
    parsed.rc === null &&
    tag === state.baseline.tag &&
    compareVersions(parsed.version, state.baseline.version) === 0

  // 基线标签校验：工具采纳前的历史 tag，只验完整性与指向，豁免 pom 属性（属性晚于该 tag 引入）
  if (isBaselineTag) {
    if (!allowDirty) assertCleanTree()
    return {
      mode: 'baseline',
      tag, parsed, commit, state, stateSource,
      version: parsed.version,
      warnings: [`基线标签 ${tag} 为工具采纳前发布，豁免 pom ${VERSION_PROP} 一致性检查`],
    }
  }

  const baseline = effectiveBaseline(state, { excludeVersion: parsed.rc === null ? parsed.version : undefined })
  if (!isAncestor(baseline.commit, commit)) {
    fail('E-NOT-INCREMENT', `tag ${tag} 不在基线 ${baseline.tag} 之后（历史被改写或选错基线）`)
  }

  const pomAt = readFileAt(commit, POM_FILE)
  if (pomAt === null) fail('E-POM', `tag 树缺少 ${POM_FILE}`)
  const pomVersion = readPomVersion(pomAt)
  if (!pomVersion) fail('E-TAG-SOURCE', `tag 树 ${POM_FILE} 缺少 <${VERSION_PROP}> 属性`)
  if (pomVersion !== officialPartStr) {
    fail(
      'E-TAG-SOURCE',
      `tag/源码不一致：tag ${tag} 要求 <${VERSION_PROP}>=${officialPartStr}，实际为 ${pomVersion}` +
        `（先 prepare --write 升版并提交，再打 tag）`,
    )
  }
  // 候选版前置检查放在重复版本检查之前：正式版已存在时给出语义准确的 E-RC-AFTER-OFFICIAL
  if (parsed.rc !== null && resolveCommit(`v${officialPartStr}`)) {
    fail('E-RC-AFTER-OFFICIAL', `正式版 v${officialPartStr} 已存在，不能再发同版本候选版 ${tag}`)
  }

  const records = loadRecordsFromTree(commit)
  const window = analyzeWindow(baseline.commit, commit, records)
  const implied = impliedLevel(baseline.version, officialPartStr)
  if (compareVersions(officialPartStr, baseline.version) === 0) {
    fail('E-DUPLICATE-VERSION', `版本与基线相同: ${officialPartStr}（重复发版）`)
  }
  if (LEVELS[implied] < LEVELS[window.requiredLevel]) {
    fail(
      'E-LEVEL-INSUFFICIENT',
      `升版不足：变更定级要求 ${window.requiredLevel}，而 ${baseline.version} -> ${officialPartStr} 仅相当于 ${implied}`,
    )
  }
  if (!allowDirty) assertCleanTree()

  if (parsed.rc === null) {
    if (window.used.length === 0) {
      fail('E-EMPTY-RELEASE', `窗口 ${baseline.tag}..${tag} 没有生效的变更记录，不构成正式发布`)
    }
    const entry = state.releases.find((e) => e.version === officialPartStr)
    if (!entry) {
      fail('E-HISTORY-MISSING', `状态文件缺少 ${officialPartStr} 的正式发布记录（先 prepare --write 并提交，再打 tag）`)
    }
    if (entry.commit && entry.commit.toLowerCase() !== commit.toLowerCase()) {
      fail('E-FROZEN-MISMATCH', `发布记录 pin 的提交与 tag 不一致：${officialPartStr} 记录 ${entry.commit.slice(0, 12)}，tag 实际 ${commit.slice(0, 12)}`)
    }
    const frozenNotes = readFileAt(commit, entry.notesFile)
    if (frozenNotes === null) fail('E-NOTES-MISSING', `tag 树缺少发布说明 ${entry.notesFile}`)
    const recomputed = aggregateNotes(officialPartStr, window.used)
    if (recomputed !== frozenNotes) {
      fail(
        'E-FROZEN-MISMATCH',
        `发布说明与变更记录重算不一致（已打 tag 的输入不可修改）。\n` +
          `--- tag 内冻结的 ${entry.notesFile} ---\n${frozenNotes}` +
          `--- 按记录重算 ---\n${recomputed}`,
      )
    }
    const rcs = listRcTags(officialPartStr)
    if (rcs.length > 0) {
      const latest = rcs[rcs.length - 1]
      if (!isAncestor(latest.commit, commit)) {
        fail('E-RC-NOT-COVERED', `正式转化必须包含最新候选版：${latest.tag} 不是 ${tag} 的祖先`)
      }
    }
  } else {
    // 递增与祖先链检查：被 gate 拒绝的更高序号残留 tag 不阻塞正确序列（其自身从未通过校验）
    const officialTag = `v${officialPartStr}`
    for (let n = 1; n < parsed.rc; n++) {
      if (!resolveCommit(`v${officialPartStr}-rc.${n}`)) {
        fail('E-RC-SKIP', `候选版必须递增：缺少前置 ${officialTag}-rc.${n}`)
      }
    }
    if (parsed.rc > 1) {
      const prev = resolveCommit(`v${officialPartStr}-rc.${parsed.rc - 1}`)
      if (!isAncestor(prev, commit)) {
        fail('E-RC-SKIP', `候选版必须递增：${officialTag}-rc.${parsed.rc - 1} 不是 ${tag} 的祖先`)
      }
    }
  }

  return {
    mode: parsed.rc === null ? 'official' : 'rc',
    tag, parsed, commit, state, stateSource,
    baseline, pomVersion, records, window, implied,
    version: parsed.version,
    officialPart: officialPartStr,
    warnings: [],
  }
}

// ---------------------------------------------------------------------------
// 命令
// ---------------------------------------------------------------------------
function short(hash) {
  return hash.slice(0, 12)
}

function resolveBaseRef(opts, state) {
  if (opts.base) {
    const c = resolveCommit(opts.base)
    if (!c) fail('E-BASE', `--base 无法解析: ${opts.base}`)
    return { ref: opts.base, commit: c }
  }
  const eb = effectiveBaseline(state)
  return { ref: eb.tag, commit: eb.commit, effective: eb }
}

function cmdCheckChanges(opts) {
  const state = loadWorkingState()
  const base = resolveBaseRef(opts, state)
  const head = resolveCommit('HEAD')
  if (!head) fail('E-GIT', '无法解析 HEAD')
  if (!isAncestor(base.commit, head)) {
    fail('E-BASELINE-NOT-ANCESTOR', `基线 ${base.ref} 不是 HEAD 祖先（历史被改写？）`)
  }
  const records = loadRecordsFromTree(null)
  const w = analyzeWindow(base.commit, head, records)
  const eb = base.effective || effectiveBaseline(state)
  const requiredForBump = w.requiredLevel === 'none' ? 'patch' : w.requiredLevel
  const candidate = bumpVersion(eb.version, requiredForBump)

  if (opts.json) {
    console.log(JSON.stringify({
      base: base.ref, baseCommit: base.commit, head, headCommit: head,
      baseline: { version: eb.version, tag: eb.tag, commit: eb.commit },
      commits: w.commits.map((c) => ({ hash: short(c.hash), subject: c.subject, coveredBy: c.coveredBy || null, auto: c.auto || null })),
      uncovered: w.uncovered.map((c) => ({ hash: short(c.hash), subject: c.subject })),
      autoNone: w.autoNone.map((c) => ({ hash: short(c.hash), subject: c.subject, auto: c.auto })),
      records: w.used.map((r) => ({ name: r.name, id: r.rec.id, level: r.rec.level, title: r.rec.title })),
      requiredLevel: w.requiredLevel,
      nextVersion: candidate,
      ok: w.uncovered.length === 0,
    }, null, 2))
  } else {
    const lines = [
      `宿主发版增量检查`,
      `基线: ${eb.tag}（${eb.version} @ ${short(eb.commit)}，来源 ${eb.origin}）`,
      `窗口: ${base.ref}..HEAD，已提交 ${w.commits.length} 笔（不含合并提交，不含 WIP）`,
    ]
    if (w.autoNone.length > 0) {
      lines.push(`自动豁免（仅文档/CI/发版辅助，按 none）: ${w.autoNone.length} 笔`)
      for (const c of w.autoNone) lines.push(`  - ${short(c.hash)} ${c.subject}（${c.auto}）`)
    }
    lines.push(`变更记录: ${w.used.length} 条生效（${CHANGES_DIR}/*.json）`)
    for (const r of w.used) lines.push(`  - [${r.rec.level}] ${r.rec.title}（${r.name}，覆盖 ${r.covered.length} 笔提交）`)
    lines.push(`定级要求: ${w.requiredLevel}`)
    lines.push(`下一正式版本候选: ${candidate}`)
    if (w.uncovered.length > 0) {
      lines.push(`未覆盖提交（缺少变更记录）: ${w.uncovered.length} 笔`)
      for (const c of w.uncovered) lines.push(`  - ${short(c.hash)} ${c.subject}`)
    } else {
      lines.push('未覆盖提交: 无')
    }
    console.log(lines.join('\n'))
  }
  if (w.uncovered.length > 0) process.exit(1)
}

function cmdPrepare(opts) {
  const state = loadWorkingState()
  const eb = effectiveBaseline(state)
  const base = resolveBaseRef(opts, state)
  const head = resolveCommit('HEAD')
  if (!isAncestor(base.commit, head)) {
    fail('E-BASELINE-NOT-ANCESTOR', `基线 ${base.ref} 不是 HEAD 祖先（历史被改写？）`)
  }
  const records = loadRecordsFromTree(null)
  const w = analyzeWindow(base.commit, head, records)
  const requiredForBump = w.requiredLevel === 'none' ? 'patch' : w.requiredLevel
  const candidate = bumpVersion(eb.version, requiredForBump)
  const implied = impliedLevel(eb.version, candidate)

  let version = candidate
  let existingRcs = []
  if (opts.rc) {
    existingRcs = listRcTags(candidate)
    const nextN = existingRcs.length > 0 ? Math.max(...existingRcs.map((r) => r.n)) + 1 : 1
    version = `${candidate}-rc.${nextN}`
  }
  const notes = aggregateNotes(candidate, w.used)

  const plan = {
    baseline: { version: eb.version, tag: eb.tag, commit: eb.commit, origin: eb.origin },
    base: base.ref,
    head,
    requiredLevel: w.requiredLevel,
    impliedLevel: implied,
    candidate,
    version,
    existingRcs: existingRcs.map((r) => r.tag),
    notes,
    uncovered: w.uncovered.map((c) => ({ hash: short(c.hash), subject: c.subject })),
  }

  if (opts.write) {
    if (w.used.length === 0) {
      fail('E-EMPTY-RELEASE', `窗口 ${base.ref}..HEAD 没有生效的变更记录，无法生成发布说明（docs 类提交不构成发版内容）`)
    }
    if (w.uncovered.length > 0) {
      fail('E-RECORDS-MISSING', `存在 ${w.uncovered.length} 笔未覆盖提交，先补 ${CHANGES_DIR}/*.json 再 prepare --write`)
    }
    if (resolveCommit(`v${version}`)) {
      fail('E-RETRY-TAGGED', `v${version} 已存在 tag：已打 tag 的版本输入不可修改；新增变更请升新版本`)
    }
    const written = []
    if (opts.rc) {
      if (resolveCommit(`v${version}`)) {
        fail('E-RETRY-TAGGED', `v${version} 已存在 tag：已打 tag 的版本输入不可修改`)
      }
      // 候选版：只升 pom 正式段；不发说明、不冻结记录
    } else {
      const notesFile = `${NOTES_DIR}/${candidate}.md`
      fs.mkdirSync(path.join(ROOT, NOTES_DIR), { recursive: true })
      fs.writeFileSync(path.join(ROOT, notesFile), notes)
      written.push(notesFile)
      const entry = { version: candidate, tag: `v${candidate}`, base: eb.tag, notesFile }
      const idx = state.releases.findIndex((e) => e.version === candidate)
      if (idx >= 0) state.releases[idx] = entry
      else state.releases.push(entry)
      // 基线自动推进：已存在 tag 的更高正式版
      const advanced = effectiveBaseline(state)
      state.baseline = { version: advanced.version, tag: advanced.tag, commit: advanced.commit }
      fs.mkdirSync(path.dirname(path.join(ROOT, STATE_FILE)), { recursive: true })
      fs.writeFileSync(path.join(ROOT, STATE_FILE), `${JSON.stringify(state, null, 2)}\n`)
      written.push(STATE_FILE)
    }
    for (const f of pomFilesWithProp()) {
      const abs = path.join(ROOT, f)
      const next = setPomVersion(fs.readFileSync(abs, 'utf8'), candidate)
      if (next !== fs.readFileSync(abs, 'utf8')) {
        fs.writeFileSync(abs, next)
        written.push(f)
      }
    }
    plan.written = written
    if (opts.json) console.log(JSON.stringify(plan, null, 2))
    else {
      const lines = [
        `已写入（dry-run 加 --write 才会落盘；本次为显式写入）:`,
        ...written.map((f) => `  - ${f}`),
        ``,
        `版本: ${eb.version} -> ${version}（定级 ${w.requiredLevel}，升版 ${implied}）`,
        `发布说明:\n${notes.split('\n').map((l) => `  ${l}`).join('\n')}`,
        `后续动作：提交 release/** 与 pom.xml 变更（只暂存这些文件），再打 tag v${version}。`,
      ]
      console.log(lines.join('\n'))
    }
  } else {
    plan.written = null
    if (opts.json) console.log(JSON.stringify(plan, null, 2))
    else {
      const lines = [
        `prepare（dry-run，未写盘；显式落盘请加 --write）`,
        `基线: ${eb.tag}（${eb.version} @ ${short(eb.commit)}）`,
        `窗口: ${base.ref}..HEAD，已提交 ${w.commits.length} 笔`,
        `生效变更记录: ${w.used.length} 条，定级要求 ${w.requiredLevel}`,
        `升版: ${eb.version} -> ${version}（${opts.rc ? `候选版 ${implied} 档位` : `正式版 ${implied} 档位`}）`,
      ]
      if (existingRcs.length > 0) lines.push(`同版本已有候选版: ${existingRcs.map((r) => r.tag).join(', ')}`)
      if (w.used.length === 0) lines.push('注意：无生效变更记录，--write 将被拒绝（docs 类提交不构成发版内容）')
      lines.push(`发布说明预览:\n${notes.split('\n').map((l) => `  ${l}`).join('\n')}`)
      console.log(lines.join('\n'))
    }
  }
  if (w.uncovered.length > 0) process.exit(1)
}

function reportEvaluate(r) {
  const lines = [
    `tag: ${r.tag}（${r.mode === 'baseline' ? '基线标签' : r.mode === 'official' ? '正式版' : '候选版'}，${r.version}）`,
    `指向: ${short(r.commit)}；状态来源: ${r.stateSource === 'tag' ? 'tag 树（冻结）' : '工作区回落'}`,
  ]
  if (r.mode !== 'baseline') {
    lines.push(
      `基线: ${r.baseline.tag}（${r.baseline.version} @ ${short(r.baseline.commit)}）`,
      `变更定级: ${r.window.requiredLevel}，升版档位: ${r.implied}，pom ${VERSION_PROP}: ${r.pomVersion}`,
      `生效变更记录: ${r.window.used.length} 条`,
    )
  }
  for (const w of r.warnings) lines.push(`提示: ${w}`)
  return lines.join('\n')
}

function cmdValidate(opts) {
  if (!opts.tag) fail('E-USAGE', 'validate 需要 --tag <tag>')
  const r = evaluateTag(opts.tag, { allowDirty: opts.allowDirty })
  if (opts.json) {
    console.log(JSON.stringify({
      ok: true, tag: r.tag, version: r.version, mode: r.mode, commit: r.commit,
      baseline: r.baseline ? { version: r.baseline.version, tag: r.baseline.tag } : { version: r.state.baseline.version, tag: r.state.baseline.tag },
      requiredLevel: r.window ? r.window.requiredLevel : null,
      impliedLevel: r.implied || null,
      warnings: r.warnings,
    }, null, 2))
  } else {
    console.log(`发布校验通过\n${reportEvaluate(r)}`)
  }
}

function cmdBuildVersion(opts) {
  if (!opts.tag) fail('E-USAGE', 'build-version 需要 --tag <tag>')
  const r = evaluateTag(opts.tag, { allowDirty: opts.allowDirty })
  if (opts.dotenv) {
    const abs = path.resolve(process.cwd(), opts.dotenv)
    fs.mkdirSync(path.dirname(abs), { recursive: true })
    fs.writeFileSync(abs, `HOST_BUILD_VERSION=${r.parsed.version}\n`)
  }
  if (opts.json) {
    console.log(JSON.stringify({
      version: r.parsed.version, tag: r.tag, mode: r.mode, commit: r.commit,
      dotenv: opts.dotenv ? path.resolve(process.cwd(), opts.dotenv) : null,
    }, null, 2))
  } else {
    // stdout 只输出精确版本，供 CI 直接捕获
    console.log(r.parsed.version)
  }
}

// ---------------------------------------------------------------------------
// CLI 解析与入口
// ---------------------------------------------------------------------------
function usage() {
  return [
    '用法: node ci/host-release.mjs <命令> [选项]',
    '',
    '命令:',
    '  check-changes [--base <ref>] [--json]        分支增量检查：基线..HEAD 已提交变更的记录覆盖与定级',
    '  prepare [--base <ref>] [--rc] [--write]      计算下一版本并聚合中文发布说明；默认 dry-run，--write 显式落盘',
    '  validate --tag <tag> [--allow-dirty] [--json] 发布全量校验（格式/一致性/覆盖/定级/冻结/干净树）',
    '  build-version --tag <tag> [--dotenv <path>]  输出经验证的精确版本；--dotenv 写 HOST_BUILD_VERSION=...',
    '',
    '说明: 仓库根取 HOST_RELEASE_REPO_ROOT 或从当前目录向上找 pom.xml；退出码 0 成功 / 1 校验失败 / 2 用法错误',
  ].join('\n')
}

function parseArgs(argv, spec) {
  const opts = {}
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (!(a in spec)) fail('E-USAGE', `未知参数: ${a}\n${usage()}`)
    if (spec[a]) {
      const val = argv[++i]
      if (val === undefined) fail('E-USAGE', `参数 ${a} 缺少值`)
      opts[spec[a]] = val
    } else {
      // 布尔旗标键归一为驼峰：--allow-dirty -> allowDirty，与命令侧读取一致
      const key = a.replace(/^--/, '').replace(/-([a-z])/g, (_, c) => c.toUpperCase())
      opts[key] = true
    }
  }
  return opts
}

export function main(argv = process.argv.slice(2)) {
  const command = argv[0]
  try {
    ROOT = resolveRepoRoot()
    if (!command || command === '--help' || command === '-h') {
      console.log(usage())
      return !command ? 2 : 0
    }
    switch (command) {
      case 'check-changes':
        cmdCheckChanges(parseArgs(argv.slice(1), { '--base': 'base', '--json': null }))
        return 0
      case 'prepare':
        cmdPrepare(parseArgs(argv.slice(1), { '--base': 'base', '--rc': null, '--write': null, '--json': null }))
        return 0
      case 'validate':
        cmdValidate(parseArgs(argv.slice(1), { '--tag': 'tag', '--allow-dirty': null, '--json': null }))
        return 0
      case 'build-version':
        cmdBuildVersion(parseArgs(argv.slice(1), { '--tag': 'tag', '--dotenv': 'dotenv', '--allow-dirty': null, '--json': null }))
        return 0
      default:
        fail('E-USAGE', `未知命令: ${command}\n${usage()}`)
    }
  } catch (e) {
    if (e instanceof ReleaseError) {
      console.error(`host-release: [${e.code}] ${e.message}`)
      return e.code === 'E-USAGE' ? 2 : 1
    }
    console.error(`host-release: 未预期错误: ${e.stack || e.message}`)
    return 1
  }
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
if (isMain) {
  process.exit(main())
}
