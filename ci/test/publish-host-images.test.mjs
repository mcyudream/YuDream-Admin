/**
 * ci/test/publish-host-images.test.mjs —— ci/publish-host-images.sh 离线测试
 * （node --test，零外部依赖；docker 由 ci/test/mock-docker.mjs 模拟）
 *
 * 运行：node --test ci/test/publish-host-images.test.mjs
 * 生产路径不触碰：本测试不访问任何真实镜像仓库。
 */
import { test, describe, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const SCRIPT = path.join(ROOT, 'ci/publish-host-images.sh')
const MOCK = path.join(ROOT, 'ci/test/mock-docker.mjs')

const tmpRoots = []
const BIN = fs.mkdtempSync(path.join(os.tmpdir(), 'publish-img-bin-'))
tmpRoots.push(BIN)
fs.writeFileSync(path.join(BIN, 'docker'), `#!/bin/sh
exec node '${MOCK}' "$@"
`, { mode: 0o755 })
// jq 不做 shim：PATH 中 BIN 仅提供 mock docker，jq 走系统真实 jq
// 构建产物夹具（镜像构建输入检查用）
fs.mkdirSync(path.join(BIN, 'ctx/yudream-bootstrap/target'), { recursive: true })
fs.writeFileSync(path.join(BIN, 'ctx/yudream-bootstrap/target/yudream-bootstrap-1.0.jar'), 'jar\n')
fs.mkdirSync(path.join(BIN, 'ctx/yudream-frontend/apps/core-arco-design-vue/dist'), { recursive: true })
fs.writeFileSync(path.join(BIN, 'ctx/yudream-frontend/apps/core-arco-design-vue/dist/index.html'), '<html></html>')

const HARBOOR = 'registry.yudream.online'
const ALIYUN = 'crpi-test.cn-hangzhou.personal.cr.aliyuncs.com'

function mkwork() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'publish-img-work-'))
  tmpRoots.push(dir)
  return dir
}

function newMockState({ auth = true } = {}) {
  const statePath = path.join(mkwork(), 'state.json')
  fs.writeFileSync(statePath, JSON.stringify({
    hosts: { [HARBOOR]: 'harbor', [ALIYUN]: 'aliyun' },
    auth: auth ? { [HARBOOR]: true, [ALIYUN]: true } : {},
    slots: { harbor: { repos: {} }, aliyun: { repos: {} } },
    local: {},
    counter: 0,
  }, null, 1))
  return statePath
}

function runScript({ env = {}, version, work }) {
  const evidence = path.join(work, 'evidence.json')
  const r = spawnSync(SCRIPT, [], {
    encoding: 'utf8',
    env: {
      ...process.env,
      PATH: `${BIN}:${process.env.PATH}`,
      YD_MOCK_STATE: env.YD_MOCK_STATE,
      HOST_BUILD_VERSION: version,
      HOST_BUILD_COMMIT: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678',
      BUILD_CONTEXT: path.join(BIN, 'ctx'),
      RELEASE_EVIDENCE_FILE: evidence,
      HARBOR_REGISTRY: HARBOOR,
      ALIYUN_REGISTRY: ALIYUN,
      DOCKER_REGISTRY_USER: 'robot$push',
      DOCKER_REGISTRY_PASSWORD: 'pw',
      ALIYUN_REGISTRY_USER: 'user',
      ALIYUN_REGISTRY_PASSWORD: 'pw',
      ...env,
    },
  })
  return { ...r, evidence }
}

function readEvidence(work) {
  return JSON.parse(fs.readFileSync(path.join(work, 'evidence.json'), 'utf8'))
}

function digestOfTag(statePath, slot, repo, tag) {
  const s = JSON.parse(fs.readFileSync(statePath, 'utf8'))
  return s.slots[slot]?.repos?.[repo]?.tags?.[tag]?.digest
}

after(() => {
  for (const dir of tmpRoots) fs.rmSync(dir, { recursive: true, force: true })
})

// ---------------------------------------------------------------------------
describe('版本通道分类', () => {
  test('非法/废弃形态被拒绝', () => {
    for (const bad of ['v01.2.3', 'v1.2.3-rc.0', 'v1.2.3-rc.01', 'v1.4.10-1', 'd1.4.10-1',
      'latest', 'v1.2', 'v1.2.3.4', 'v1.2.3+build']) {
      const work = mkwork()
      const r = runScript({ env: { YD_MOCK_STATE: newMockState() }, version: bad, work })
      assert.equal(r.status, 1, `${bad} 应被拒绝: ${r.stderr}`)
      assert.match(r.stderr, /无法识别的 HOST_BUILD_VERSION/)
    }
  })

  test('合法形态放行：正式/候选/开发', () => {
    for (const v of ['v1.4.11', 'v1.5.0-rc.1', 'd1.4.10']) {
      const work = mkwork()
      const state = newMockState()
      const r = runScript({ env: { YD_MOCK_STATE: state }, version: v, work })
      assert.equal(r.status, 0, `${v} 应放行: ${r.stderr}\n${r.stdout}`)
    }
  })
})

describe('双仓发布与证据', () => {
  test('正式版：双仓推送不可变标签并晋升 latest，证据 complete', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work })
    assert.equal(r.status, 0, r.stderr)
    const ev = readEvidence(work)
    assert.equal(ev.status, 'complete')
    assert.equal(ev.channel, 'stable')
    assert.equal(ev.hostVersion, 'v1.4.11')
    assert.equal(ev.latest.attempted, true)
    assert.equal(ev.latest.complete, true)
    for (const slot of ['harbor', 'aliyun']) {
      assert.equal(ev.images.backend.registries[slot].enabled, true)
      assert.equal(ev.images.frontend.registries[slot].enabled, true)
      assert.ok(ev.images.backend.registries[slot].tags.version.digest.startsWith('sha256:'))
    }
    // 双仓摘要一致
    assert.equal(
      digestOfTag(state, 'harbor', 'yda/backend', '1.4.11'),
      digestOfTag(state, 'aliyun', 'yudream/backend', '1.4.11'),
    )
    // 精确版本与 SHA 标签均已推送
    for (const [slot, repo] of [['harbor', 'yda/backend'], ['aliyun', 'yudream/backend']]) {
      assert.ok(digestOfTag(state, slot, repo, '1.4.11'), `${slot} 版本标签`)
      assert.ok(digestOfTag(state, slot, repo, 'a1b2c3d4'), `${slot} SHA 标签`)
      assert.ok(digestOfTag(state, slot, repo, 'latest'), `${slot} latest`)
    }
  })

  test('候选版与开发版不晋升 latest', () => {
    for (const [version, channel] of [['v1.5.0-rc.1', 'rc'], ['d1.4.10', 'dev']]) {
      const work = mkwork()
      const state = newMockState()
      const r = runScript({ env: { YD_MOCK_STATE: state }, version, work })
      assert.equal(r.status, 0, r.stderr)
      const ev = readEvidence(work)
      assert.equal(ev.status, 'complete')
      assert.equal(ev.channel, channel)
      assert.equal(ev.latest.attempted, false)
      assert.ok(!digestOfTag(state, 'harbor', 'yda/backend', 'latest'),
        `${version} 不应写 latest`)
    }
  })

  test('正式版双仓缺一不可：关闭阿里云时拒绝', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({ env: { YD_MOCK_STATE: state, ALIYUN_PUSH_ENABLED: 'false' }, version: 'v1.4.11', work })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /正式通道要求双仓/)
  })

  test('候选版可关闭阿里云（单仓候选）', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({ env: { YD_MOCK_STATE: state, ALIYUN_PUSH_ENABLED: 'false' }, version: 'v1.5.0-rc.1', work })
    assert.equal(r.status, 0, r.stderr)
    const ev = readEvidence(work)
    assert.equal(ev.status, 'complete')
    assert.equal(ev.images.backend.registries.aliyun.enabled, false)
  })
})

describe('不可变标签与防倒退', () => {
  test('版本标签已被不同 commit 占用时拒绝', () => {
    const work = mkwork()
    const state = newMockState()
    // 预置旧 commit 占用 1.4.11
    fs.writeFileSync(path.join(work, 'seed.mjs'), '')
    const pre = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work: mkwork() })
    // 直接在当前 state 上模拟另一 commit 占用：改 state 里 harbor 1.4.11 的 revision
    void pre
    const s = JSON.parse(fs.readFileSync(state, 'utf8'))
    s.slots.harbor.repos['yda/backend'] = { tags: { '1.4.11': {
      digest: 'sha256:' + '9'.repeat(64), imageId: 'sha256:other',
      labels: { 'org.opencontainers.image.revision': 'ffffffffffffffffffffffffffffffffffffffff',
        'org.opencontainers.image.version': 'v1.4.11' },
    } } }
    fs.writeFileSync(state, JSON.stringify(s, null, 1))
    const r = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /禁止覆盖|已被不同 commit 占用/)
  })

  test('latest 防倒退：已有更高正式版时拒绝晋升', () => {
    const work = mkwork()
    const state = newMockState()
    // 用一次真实发布把 latest 推到 v1.5.0
    const hi = runScript({ env: { YD_MOCK_STATE: state, HOST_BUILD_VERSION: 'v1.5.0' }, version: 'v1.5.0', work })
    assert.equal(hi.status, 0, hi.stderr)
    // 再发低版本 v1.4.11
    const r = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /拒绝 latest 倒退/)
    const ev = readEvidence(work)
    assert.equal(ev.status, 'partial')
  })

  test('latest 无 version 标签（历史遗留）fail closed，需 ALLOW_LATEST_MIGRATION=1', () => {
    const work = mkwork()
    const state = newMockState()
    // 预置一个无 OCI 标签的 latest
    const s = JSON.parse(fs.readFileSync(state, 'utf8'))
    s.slots.harbor.repos['yda/backend'] = { tags: { latest: {
      digest: 'sha256:' + '8'.repeat(64), imageId: 'sha256:legacy', labels: {},
    } } }
    fs.writeFileSync(state, JSON.stringify(s, null, 1))
    const r = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /无 OCI version 标签|latest 无版本/)
    const r2 = runScript({ env: { YD_MOCK_STATE: state, ALLOW_LATEST_MIGRATION: '1' }, version: 'v1.4.11', work })
    assert.equal(r2.status, 0, r2.stderr)
  })

  test('latest 读取遇到网络故障 fail closed', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({
      env: { YD_MOCK_STATE: state, YD_MOCK_FAIL: 'pull:aliyun:*:latest:network' },
      version: 'v1.4.11', work,
    })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /latest 版本读取失败/)
  })

  test('同 commit 重试幂等：已存在同内容标签复用不重建', () => {
    const work = mkwork()
    const state = newMockState()
    const first = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work })
    assert.equal(first.status, 0, first.stderr)
    const digestBefore = digestOfTag(state, 'harbor', 'yda/backend', '1.4.11')
    const r = runScript({ env: { YD_MOCK_STATE: state }, version: 'v1.4.11', work })
    assert.equal(r.status, 0, r.stderr)
    assert.equal(digestOfTag(state, 'harbor', 'yda/backend', '1.4.11'), digestBefore)
    const ev = readEvidence(work)
    assert.equal(ev.images.backend.registries.harbor.tags.version.action, 'reused')
  })

  test('推送部分失败时直接失败退出，不写证据（不宣告完成）', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({
      env: { YD_MOCK_STATE: state, YD_MOCK_FAIL: 'push:aliyun:backend:1.4.11' },
      version: 'v1.4.11', work,
    })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /推送失败/)
    assert.ok(!fs.existsSync(path.join(work, 'evidence.json')), '失败退出不得生成完整发行证据')
  })
})

describe('登录与依赖', () => {
  test('缺少凭据时报错而非静默跳过', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({
      env: { YD_MOCK_STATE: state, DOCKER_REGISTRY_PASSWORD: '' },
      version: 'v1.4.11', work,
    })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /缺少凭据/)
  })

  test('凭据错误（登录失败）fail closed', () => {
    const work = mkwork()
    const state = newMockState()
    const r = runScript({
      env: { YD_MOCK_STATE: state, YD_MOCK_FAIL: 'login:harbor' },
      version: 'v1.4.11', work,
    })
    assert.equal(r.status, 1)
    assert.match(r.stderr, /docker login.*失败/)
  })
})
