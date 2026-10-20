/**
 * ci/host-release.test.mjs —— 宿主发版工具测试（node --test，零外部依赖）
 *
 * 运行：node --test ci/host-release.test.mjs
 * 集成测试在临时 Git 仓库夹具上执行真实 CLI（子进程），不触碰本仓库工作区。
 */
import { test, describe, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  parseTag,
  parseVersion,
  compareVersions,
  bumpVersion,
  impliedLevel,
  aggregateNotes,
  readPomVersion,
  setPomVersion,
} from './host-release.mjs'

const CLI = fileURLToPath(new URL('./host-release.mjs', import.meta.url))
const BASE_VERSION = '1.4.10'

// ---------------------------------------------------------------------------
// 夹具：临时 Git 仓库
// ---------------------------------------------------------------------------
const tmpRoots = []

function sh(repo, args, { allowFail = false } = {}) {
  const r = spawnSync('git', args, { cwd: repo, encoding: 'utf8' })
  if (r.status !== 0 && !allowFail) {
    throw new Error(`git ${args.join(' ')} 失败: ${(r.stderr || r.stdout || '').trim()}`)
  }
  return r
}

function write(repo, file, content) {
  const abs = path.join(repo, file)
  fs.mkdirSync(path.dirname(abs), { recursive: true })
  fs.writeFileSync(abs, content)
}

function commit(repo, message, files = {}) {
  for (const [file, content] of Object.entries(files)) write(repo, file, content)
  sh(repo, ['add', '-A'])
  sh(repo, ['commit', '--no-gpg-sign', '-m', message])
  return sh(repo, ['rev-parse', 'HEAD']).stdout.trim()
}

function tag(repo, name, ref = 'HEAD') {
  sh(repo, ['tag', '-a', name, '-m', name, ref])
}

function baselinePom(version) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>fixture</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <yudream.host.version>${version}</yudream.host.version>
        <yudream.host.build-version>\${yudream.host.version}-dev.local</yudream.host.build-version>
    </properties>
</project>
`
}

function recordJson({ id, level, title, commits, notes }) {
  return `${JSON.stringify({ id, level, title, commits, notes }, null, 2)}\n`
}

// 建仓：pom(基线版本) 提交 -> 基线 tag -> 状态文件提交
function makeRepo({ baseline = BASE_VERSION } = {}) {
  const repo = fs.mkdtempSync(path.join(os.tmpdir(), 'host-release-test-'))
  tmpRoots.push(repo)
  sh(repo, ['init', '-b', 'master'])
  sh(repo, ['config', 'user.email', 'test@example.com'])
  sh(repo, ['config', 'user.name', '测试'])
  commit(repo, 'chore: 初始化', { 'pom.xml': baselinePom(baseline) })
  tag(repo, `v${baseline}`)
  const baselineCommit = sh(repo, ['rev-parse', `v${baseline}^{commit}`]).stdout.trim()
  write(repo, 'release/host-release.json', `${JSON.stringify({
    baseline: { version: baseline, tag: `v${baseline}`, commit: baselineCommit },
    releases: [],
  }, null, 2)}\n`)
  sh(repo, ['add', '-A'])
  sh(repo, ['commit', '--no-gpg-sign', '-m', 'chore: 发版状态基线'])
  return repo
}

function run(repo, args) {
  return spawnSync(process.execPath, [CLI, ...args], {
    cwd: repo,
    encoding: 'utf8',
    env: { ...process.env, HOST_RELEASE_REPO_ROOT: repo },
  })
}

after(() => {
  for (const dir of tmpRoots) fs.rmSync(dir, { recursive: true, force: true })
})

// 标准窗口：一笔代码提交 + 对应记录 + 记录提交
function seedCoveredChange(repo, { level = 'patch', file = 'src/A.java', id = 'r1' } = {}) {
  const code = commit(repo, `feat: 代码变更 ${id}`, { [file]: `内容 ${id}\n` })
  const short = code.slice(0, 12)
  commit(repo, `docs(release): 记录 ${id}`, {
    [`release/changes/2026-01-01-${id}.json`]: recordJson({
      id, level, title: `变更 ${id}`,
      commits: [short],
      notes: [`${id} 的中文说明`],
    }),
  })
  return code
}

// ---------------------------------------------------------------------------
// 纯函数单元测试
// ---------------------------------------------------------------------------
describe('semver 解析（严格）', () => {
  test('正式版与候选版', () => {
    assert.deepEqual({ ...parseTag('v1.4.10'), tag: 'v1.4.10' }, {
      tag: 'v1.4.10', version: '1.4.10', major: 1, minor: 4, patch: 10, rc: null,
    })
    const rc = parseTag('v1.5.0-rc.2')
    assert.equal(rc.version, '1.5.0-rc.2')
    assert.equal(rc.rc, 2)
    assert.equal(parseTag('v1.5.0').rc, null)
  })
  test('拒绝非法形态', () => {
    for (const bad of ['v01.2.3', 'v1.2', 'v1.2.3.4', 'v1.2.3-rc.0', 'v1.2.3-rc.01',
      'v1.2.3+build', '1.4.11', 'v1.2.3-beta', 'v1.2.3-rc', 'latest', '']) {
      assert.equal(parseTag(bad), null, `应拒绝: ${bad}`)
    }
  })
  test('parseVersion 接受不带 v 的版本串', () => {
    assert.equal(parseVersion('1.5.0-rc.1').rc, 1)
    assert.equal(parseVersion('1.5.0-dev.local'), null)
  })
})

describe('版本比较与升版', () => {
  test('compareVersions', () => {
    assert.equal(compareVersions('1.4.10', '1.4.9'), 1)
    assert.equal(compareVersions('1.4.10', '1.5.0'), -1)
    assert.equal(compareVersions('1.5.0', '1.5.0-rc.2'), 1)
    assert.equal(compareVersions('1.5.0-rc.2', '1.5.0-rc.10'), -1)
    assert.equal(compareVersions('1.5.0', '1.5.0'), 0)
  })
  test('bumpVersion', () => {
    assert.equal(bumpVersion('1.4.10', 'major'), '2.0.0')
    assert.equal(bumpVersion('1.4.10', 'minor'), '1.5.0')
    assert.equal(bumpVersion('1.4.10', 'patch'), '1.4.11')
  })
  test('impliedLevel', () => {
    assert.equal(impliedLevel('1.4.10', '2.0.0'), 'major')
    assert.equal(impliedLevel('1.4.10', '1.5.0'), 'minor')
    assert.equal(impliedLevel('1.4.10', '1.4.11'), 'patch')
    assert.equal(impliedLevel('1.4.10', '1.4.10'), 'none')
  })
})

describe('release notes 聚合（确定性）', () => {
  test('按档位降序、内容稳定', () => {
    const records = [
      { name: 'b.json', rec: { id: 'b', level: 'patch', title: '修复 B', notes: ['B1'] } },
      { name: 'a.json', rec: { id: 'a', level: 'minor', title: '功能 A', notes: ['A1', 'A2'] } },
    ]
    const text = aggregateNotes('1.5.0', records)
    assert.equal(text, '# 版本 1.5.0\n\n- 【minor】功能 A\n  - A1\n  - A2\n- 【patch】修复 B\n  - B1\n')
    assert.equal(aggregateNotes('1.5.0', [...records].reverse()), text)
  })
})

describe('pom 属性读写', () => {
  test('读取与替换', () => {
    const pom = baselinePom('1.4.10')
    assert.equal(readPomVersion(pom), '1.4.10')
    const next = setPomVersion(pom, '1.4.11')
    assert.equal(readPomVersion(next), '1.4.11')
    assert.ok(next.includes('<yudream.host.build-version>'))
  })
  test('缺属性时插入', () => {
    const next = setPomVersion('<project>\n  <properties>\n  </properties>\n</project>\n', '1.5.0')
    assert.equal(readPomVersion(next), '1.5.0')
  })
})

// ---------------------------------------------------------------------------
// 集成测试（临时仓库 + 真实 CLI）
// ---------------------------------------------------------------------------
describe('check-changes', () => {
  test('已覆盖变更通过并给出候选版本', (t) => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    const r = run(repo, ['check-changes', '--json'])
    assert.equal(r.status, 0, r.stderr)
    const out = JSON.parse(r.stdout)
    assert.equal(out.ok, true)
    assert.equal(out.requiredLevel, 'patch')
    assert.equal(out.nextVersion, '1.4.11')
    assert.equal(out.baseline.version, '1.4.10')
  })

  test('未覆盖的代码提交导致失败并列出哈希', () => {
    const repo = makeRepo()
    commit(repo, 'feat: 无记录的代码', { 'src/X.java': 'x\n' })
    const r = run(repo, ['check-changes'])
    assert.equal(r.status, 1)
    assert.match(r.stdout, /未覆盖提交/)
    assert.match(r.stdout, /无记录的代码/)
  })

  test('仅文档提交自动按 none，无需记录', () => {
    const repo = makeRepo()
    commit(repo, 'docs: 文档', { 'docs/a.md': 'a\n' })
    const r = run(repo, ['check-changes', '--json'])
    assert.equal(r.status, 0, r.stderr)
    const out = JSON.parse(r.stdout)
    assert.equal(out.ok, true)
    assert.equal(out.requiredLevel, 'none')
    // 夹具里基线 tag 后还有一笔状态文件提交，同样按豁免处理；关键是不要求变更记录
    assert.ok(out.autoNone.some((e) => e.subject === 'docs: 文档'), '文档提交应自动豁免')
    assert.ok(!out.commits.some((c) => c.coveredBy === null && c.auto !== 'exempt'))
  })

  test('显式 --base 生效', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    const r = run(repo, ['check-changes', '--base', 'v1.4.10', '--json'])
    assert.equal(r.status, 0, r.stderr)
    assert.equal(JSON.parse(r.stdout).base, 'v1.4.10')
  })

  test('基线记录与 tag 指向不一致时报完整性错误', () => {
    const repo = makeRepo()
    write(repo, 'release/host-release.json', `${JSON.stringify({
      baseline: { version: '1.4.10', tag: 'v1.4.10', commit: '0'.repeat(40) },
      releases: [],
    }, null, 2)}\n`)
    commit(repo, 'chore: 篡改状态', {})
    const r = run(repo, ['check-changes'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-BASELINE-INTEGRITY/)
  })
})

describe('prepare', () => {
  test('默认 dry-run 不落盘', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'minor', id: 'f1' })
    const r = run(repo, ['prepare'])
    assert.equal(r.status, 0, r.stderr)
    assert.match(r.stdout, /dry-run/)
    assert.match(r.stdout, /1\.5\.0/)
    assert.equal(fs.existsSync(path.join(repo, 'release/notes')), false)
    assert.equal(JSON.parse(fs.readFileSync(path.join(repo, 'release/host-release.json'), 'utf8')).releases.length, 0)
    assert.equal(readPomVersion(fs.readFileSync(path.join(repo, 'pom.xml'), 'utf8')), '1.4.10')
  })

  test('--write 落盘 notes/状态/pom，提交后打 tag 可通过 validate 与 build-version', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    const w = run(repo, ['prepare', '--write'])
    assert.equal(w.status, 0, w.stderr)
    assert.equal(readPomVersion(fs.readFileSync(path.join(repo, 'pom.xml'), 'utf8')), '1.4.11')
    const notes = fs.readFileSync(path.join(repo, 'release/notes/1.4.11.md'), 'utf8')
    assert.match(notes, /# 版本 1\.4\.11/)
    assert.match(notes, /【patch】变更 r1/)
    const state = JSON.parse(fs.readFileSync(path.join(repo, 'release/host-release.json'), 'utf8'))
    assert.equal(state.releases[0].version, '1.4.11')
    assert.equal(state.releases[0].base, 'v1.4.10')

    commit(repo, 'chore(release): 1.4.11 发布准备')
    tag(repo, 'v1.4.11')
    const v = run(repo, ['validate', '--tag', 'v1.4.11'])
    assert.equal(v.status, 0, v.stderr)
    const b = run(repo, ['build-version', '--tag', 'v1.4.11'])
    assert.equal(b.status, 0, b.stderr)
    assert.equal(b.stdout, '1.4.11\n')
  })

  test('--dotenv 写出 HOST_BUILD_VERSION', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    run(repo, ['prepare', '--write'])
    commit(repo, 'chore(release): 1.4.11 发布准备')
    tag(repo, 'v1.4.11')
    const dotenvPath = path.join(repo, 'host-version.env')
    const b = run(repo, ['build-version', '--tag', 'v1.4.11', '--dotenv', 'host-version.env'])
    assert.equal(b.status, 0, b.stderr)
    assert.equal(fs.readFileSync(dotenvPath, 'utf8'), 'HOST_BUILD_VERSION=1.4.11\n')
  })

  test('无生效记录时 --write 被拒绝', () => {
    const repo = makeRepo()
    commit(repo, 'docs: 只有文档', { 'docs/a.md': 'a\n' })
    const r = run(repo, ['prepare', '--write'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-EMPTY-RELEASE/)
  })

  test('目标版本已打 tag 时拒绝重算（重试不可改已 tag 输入）', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    run(repo, ['prepare', '--write'])
    commit(repo, 'chore(release): 1.4.11 发布准备')
    tag(repo, 'v1.4.11')
    // 状态里基线仍是 1.4.10（模拟外部直接打 tag、未经下一轮 prepare 推进）
    write(repo, 'release/host-release.json', `${JSON.stringify({
      baseline: { version: '1.4.10', tag: 'v1.4.10', commit: sh(repo, ['rev-parse', 'v1.4.10^{commit}']).stdout.trim() },
      releases: [],
    }, null, 2)}\n`)
    commit(repo, 'chore: 还原状态', {})
    const r = run(repo, ['prepare', '--write'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-RETRY-TAGGED/)
  })
})

describe('validate 拦截', () => {
  test('tag/源码版本不一致（升版提交遗漏）', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    run(repo, ['prepare', '--write'])
    commit(repo, 'chore(release): 1.4.11 发布准备')
    tag(repo, 'v1.4.12')
    const r = run(repo, ['validate', '--tag', 'v1.4.12'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-TAG-SOURCE/)
  })

  test('升版不足：minor 记录配 patch 版本', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'minor', id: 'f1' })
    // 绕过 prepare，手工把 pom 升到 patch 档位并伪造历史记录缺失前的状态
    commit(repo, 'feat: 手工升版', { 'pom.xml': baselinePom('1.4.11') })
    tag(repo, 'v1.4.11')
    const r = run(repo, ['validate', '--tag', 'v1.4.11'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-LEVEL-INSUFFICIENT/)
  })

  test('缺少发布记录/说明（未 prepare 直接打 tag）', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    commit(repo, 'chore: 手工升版', { 'pom.xml': baselinePom('1.4.11') })
    tag(repo, 'v1.4.11')
    const r = run(repo, ['validate', '--tag', 'v1.4.11', '--allow-dirty'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-HISTORY-MISSING/)
  })

  test('移动已打 tag 重新注入变更（E-FROZEN-MISMATCH）', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    run(repo, ['prepare', '--write'])
    commit(repo, 'chore(release): 1.4.11 发布准备')
    tag(repo, 'v1.4.11')
    assert.equal(run(repo, ['validate', '--tag', 'v1.4.11']).status, 0)
    // 事后修改记录并移动 tag：冻结说明与重算不一致，必须失败
    const rec = JSON.parse(fs.readFileSync(path.join(repo, 'release/changes/2026-01-01-r1.json'), 'utf8'))
    rec.notes.push('夹带私货')
    commit(repo, 'docs(release): 改记录并移 tag', {
      'release/changes/2026-01-01-r1.json': recordJson(rec),
    })
    sh(repo, ['tag', '-f', '-a', 'v1.4.11', '-m', 'v1.4.11'])
    const r = run(repo, ['validate', '--tag', 'v1.4.11'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-FROZEN-MISMATCH/)
  })

  test('tracked 源码不干净阻塞发布；未跟踪构建产物忽略；--allow-dirty 逃生', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    run(repo, ['prepare', '--write'])
    commit(repo, 'chore(release): 1.4.11 发布准备')
    tag(repo, 'v1.4.11')
    // 未跟踪文件（模拟构建产物）不阻塞
    write(repo, 'target/a.jar', 'bin')
    assert.equal(run(repo, ['validate', '--tag', 'v1.4.11']).status, 0)
    // 已跟踪改动阻塞
    write(repo, 'src/A.java', '改动未提交\n')
    const dirty = run(repo, ['validate', '--tag', 'v1.4.11'])
    assert.equal(dirty.status, 1)
    assert.match(dirty.stderr, /E-DIRTY-TREE/)
    assert.equal(run(repo, ['validate', '--tag', 'v1.4.11', '--allow-dirty']).status, 0)
  })

  test('非法标签格式与缺失 tag', () => {
    const repo = makeRepo()
    const bad = run(repo, ['validate', '--tag', 'v01.2.3'])
    assert.equal(bad.status, 1)
    assert.match(bad.stderr, /E-TAG-FORMAT/)
    const missing = run(repo, ['build-version', '--tag', 'v9.9.9'])
    assert.equal(missing.status, 1)
    assert.match(missing.stderr, /E-TAG-MISSING/)
  })

  test('基线标签走精简校验并通过', () => {
    const repo = makeRepo()
    const r = run(repo, ['validate', '--tag', 'v1.4.10'])
    assert.equal(r.status, 0, r.stderr)
    const j = JSON.parse(run(repo, ['validate', '--tag', 'v1.4.10', '--json']).stdout)
    assert.equal(j.mode, 'baseline')
  })
})

describe('候选版（rc）规则', () => {
  test('rc 递增、正式转化、越序拦截', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'minor', id: 'f1' })
    const p = run(repo, ['prepare', '--rc', '--write'])
    assert.equal(p.status, 0, p.stderr)
    assert.equal(readPomVersion(fs.readFileSync(path.join(repo, 'pom.xml'), 'utf8')), '1.5.0')
    assert.equal(fs.existsSync(path.join(repo, 'release/notes')), false, 'rc 不发正式说明')
    const state = JSON.parse(fs.readFileSync(path.join(repo, 'release/host-release.json'), 'utf8'))
    assert.equal(state.releases.length, 0, 'rc 不冻结发布记录')
    commit(repo, 'chore(release): 1.5.0 pom 升版')
    tag(repo, 'v1.5.0-rc.1')
    const b1 = run(repo, ['build-version', '--tag', 'v1.5.0-rc.1'])
    assert.equal(b1.status, 0, b1.stderr)
    assert.equal(b1.stdout, '1.5.0-rc.1\n')

    // 二次 prepare 递增 rc.2；直接打 rc.3 跳号被拦截
    const p2 = run(repo, ['prepare', '--rc'])
    assert.match(p2.stdout, /1\.5\.0-rc\.2/)
    tag(repo, 'v1.5.0-rc.3')
    const skip = run(repo, ['validate', '--tag', 'v1.5.0-rc.3'])
    assert.equal(skip.status, 1)
    assert.match(skip.stderr, /E-RC-SKIP/)
    tag(repo, 'v1.5.0-rc.2')
    assert.equal(run(repo, ['validate', '--tag', 'v1.5.0-rc.2']).status, 0)

    // 正式转化：prepare --write -> tag v1.5.0 -> 校验通过（最新 rc 是祖先）
    run(repo, ['prepare', '--write'])
    commit(repo, 'chore(release): 1.5.0 发布准备')
    tag(repo, 'v1.5.0')
    const off = run(repo, ['validate', '--tag', 'v1.5.0'])
    assert.equal(off.status, 0, off.stderr)
    assert.equal(run(repo, ['build-version', '--tag', 'v1.5.0']).stdout, '1.5.0\n')

    // 正式版存在后不得再发同版本 rc
    tag(repo, 'v1.5.0-rc.4')
    const late = run(repo, ['validate', '--tag', 'v1.5.0-rc.4'])
    assert.equal(late.status, 1)
    assert.match(late.stderr, /E-RC-AFTER-OFFICIAL/)
  })

  test('正式转化未包含最新 rc（E-RC-NOT-COVERED）', () => {
    const repo = makeRepo()
    // 代码 + 记录先落，随后手工准备（冻结说明与记录重算一致），正式 tag 打在 rc tag 之前
    const code = commit(repo, 'feat: 功能', { 'src/A.java': 'a\n' })
    commit(repo, 'docs(release): 记录', {
      'release/changes/2026-01-02-r1.json': recordJson({
        id: 'r1', level: 'patch', title: '修复', commits: [code.slice(0, 12)], notes: ['说明'],
      }),
    })
    const prep = commit(repo, 'chore(release): 1.5.0 手工准备', {
      'pom.xml': baselinePom('1.5.0'),
      'release/notes/1.5.0.md': '# 版本 1.5.0\n\n- 【patch】修复\n  - 说明\n',
      'release/host-release.json': `${JSON.stringify({
        baseline: { version: '1.4.10', tag: 'v1.4.10', commit: sh(repo, ['rev-parse', 'v1.4.10^{commit}']).stdout.trim() },
        releases: [{ version: '1.5.0', tag: 'v1.5.0', base: 'v1.4.10', notesFile: 'release/notes/1.5.0.md' }],
      }, null, 2)}\n`,
    })
    tag(repo, 'v1.5.0', prep)
    // rc tag 打在正式 tag 之后的提交上：正式转化未包含最新 rc
    commit(repo, 'docs: 后续提交', { 'docs/q.md': 'q\n' })
    tag(repo, 'v1.5.0-rc.1')
    const r = run(repo, ['validate', '--tag', 'v1.5.0'])
    assert.equal(r.status, 1)
    assert.match(r.stderr, /E-RC-NOT-COVERED/)
  })
})

describe('记录 schema 与引用校验', () => {
  test('非法 level 被拦截', () => {
    const repo = makeRepo()
    commit(repo, 'docs(release): 坏记录', {
      'release/changes/bad.json': recordJson({ id: 'bad', level: 'huge', title: 'x', commits: ['abcdef1'], notes: ['n'] }),
    })
    assert.match(run(repo, ['check-changes']).stderr, /E-RECORD/)
  })

  test('引用不存在的提交哈希被拦截', () => {
    const repo = makeRepo()
    commit(repo, 'docs(release): 幽灵哈希', {
      'release/changes/ghost.json': recordJson({ id: 'g', level: 'patch', title: 'x', commits: ['deadbeef'], notes: ['n'] }),
    })
    assert.match(run(repo, ['check-changes']).stderr, /E-RECORD-HASH/)
  })

  test('发版准备提交（仅版本属性）自动豁免', () => {
    const repo = makeRepo()
    seedCoveredChange(repo, { level: 'patch', id: 'r1' })
    commit(repo, 'chore(release): 1.4.11 发布准备', { 'pom.xml': baselinePom('1.4.11') })
    const r = run(repo, ['check-changes', '--json'])
    assert.equal(r.status, 0, r.stderr)
    const out = JSON.parse(r.stdout)
    assert.ok(out.autoNone.some((c) => c.auto === 'pom-version'))
  })
})
