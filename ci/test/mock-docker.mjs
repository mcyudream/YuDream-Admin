#!/usr/bin/env node
// 测试专用 docker CLI 模拟器 —— 仅供 ci/publish-host-images.sh 的离线测试使用，
// 生产路径不依赖本文件。模拟真实 docker CLI 的输出/退出码约定：
//   login <host> -u <user> --password-stdin
//   build -f <file> --label <k=v> ... -t <ref> <context>
//   tag <src> <dst>
//   push <ref>            末行输出 `<tag>: digest: sha256:... size: N`
//   pull <ref>            输出 `Digest: sha256:...`；缺失标签返回
//                         `... manifest for <ref> not found: manifest unknown ...`
//   image inspect --format '{{.Id}}'|'{{json .Config.Labels}}' <ref>
//
// 状态（YD_MOCK_STATE 指向的 JSON）：
//   hosts:  { "<registry host>": "<slot 名（harbor/aliyun）>" }
//   auth:   { "<registry host>": true }
//   slots:  { harbor: { repos: { "<project>/<name>": { tags: { "<tag>": {digest,imageId,labels} } } } }, aliyun: {...} }
//   local:  { "<ref>": { imageId, labels } }
//
// 故障注入（env）：
//   YD_MOCK_FAIL=login:harbor,pull:aliyun:*:latest:network,push:harbor:backend:1.4.10,build
//     pull/push 指令格式 <op>:<slot>:<image>:<tag>[:<class>]，class: auth|network；其余为通用失败
//   YD_MOCK_DIGEST_SKEW=aliyun:backend:1.4.10   推送时篡改摘要（模拟注册表侧内容漂移）
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const statePath = process.env.YD_MOCK_STATE || '';
if (!statePath) {
  console.error('mock-docker: 未设置 YD_MOCK_STATE');
  process.exit(2);
}

const FAILS = (process.env.YD_MOCK_FAIL || '').split(',').map((s) => s.trim()).filter(Boolean);
const SKEWS = (process.env.YD_MOCK_DIGEST_SKEW || '').split(',').map((s) => s.trim()).filter(Boolean);
const argv = process.argv.slice(2);
const op = argv[0];

function loadState() {
  try {
    return JSON.parse(fs.readFileSync(statePath, 'utf8'));
  } catch {
    return { auth: {}, hosts: {}, slots: {}, local: {}, counter: 0 };
  }
}
function saveState(s) {
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  fs.writeFileSync(statePath, JSON.stringify(s, null, 1));
}
function recordCall(line) {
  const callsPath = process.env.YD_MOCK_CALLS;
  if (callsPath) fs.appendFileSync(callsPath, line + '\n');
}
recordCall(Array.from(argv).join(' '));

const sha256hex = (s) => crypto.createHash('sha256').update(s).digest('hex');
// 同一 image（同 imageId）无论推到哪个仓库摘要一致（真实行为：manifest 内容相同）
const digestOf = (imageId) => 'sha256:' + sha256hex('manifest\0' + imageId);
const skewDigest = (d) => {
  const hex = d.slice('sha256:'.length);
  const last = hex[hex.length - 1];
  return 'sha256:' + hex.slice(0, -1) + (last === '0' ? '1' : '0');
};

function refParse(ref) {
  const slash = ref.indexOf('/');
  if (slash <= 0) return null;
  const host = ref.slice(0, slash);
  const rest = ref.slice(slash + 1);
  const colon = rest.lastIndexOf(':');
  if (colon <= 0) return { host, repo: rest, tag: 'latest', name: rest.split('/').pop() };
  const repo = rest.slice(0, colon);
  return { host, repo, tag: rest.slice(colon + 1), name: repo.split('/').pop() };
}

function hostSlot(state, host) {
  return state.hosts[host] || host;
}

function failClass(kind, slot, image, tag) {
  for (const f of FAILS) {
    const p = f.split(':');
    if (p[0] !== kind) continue;
    if (kind === 'build' || kind === 'login') return p[1] || 'generic';
    const fSlot = p[1] ?? '*';
    const fImage = p[2] ?? '*';
    const fTag = p[3] ?? '*';
    const fClass = p[4] || 'generic';
    const slotOk = fSlot === '*' || fSlot === slot;
    const imageOk = fImage === '*' || fImage === image;
    const tagOk = fTag === '*' || fTag === tag;
    if (slotOk && imageOk && tagOk) return fClass;
  }
  return '';
}
function skewFor(slot, repo, tag) {
  return SKEWS.some((s) => {
    const [fSlot, fRepo, fTag] = s.split(':');
    return (fSlot === '*' || fSlot === slot) && (fRepo === '*' || fRepo === repo) && (fTag === '*' || fTag === tag);
  });
}

const state = loadState();

// ---------- login ----------
if (op === 'login') {
  const host = argv[1];
  const slot = hostSlot(state, host);
  if (failClass('login', slot, '*', '*')) {
    console.error('Error response from daemon: unauthorized: incorrect username or password');
    process.exit(1);
  }
  state.auth[host] = true;
  saveState(state);
  console.log(`WARNING! Using --password via the CLI is insecure. Use --password-stdin. (mock ${host})`);
  console.log('Login Succeeded');
  process.exit(0);
}

// ---------- build ----------
if (op === 'build') {
  let file = '';
  let target = '';
  let ctx = '';
  const labels = [];
  for (let i = 1; i < argv.length; i++) {
    const a = argv[i];
    if (a === '-f' || a === '--file') file = argv[++i];
    else if (a === '-t' || a === '--tag') target = argv[++i];
    else if (a === '--label') labels.push(argv[++i]);
    else if (a === '--build-arg') i++;
    else if (a === '--pull' || a === '--quiet') { /* 忽略 */ }
    else ctx = a;
  }
  if (failClass('build')) {
    console.error(`ERROR: failed to solve: failed to read dockerfile (${file}): mock build failure`);
    process.exit(1);
  }
  if (!target) {
    console.error('mock-docker: build 缺少 -t');
    process.exit(2);
  }
  const labelObj = {};
  for (const l of labels) {
    const eq = l.indexOf('=');
    labelObj[l.slice(0, eq)] = l.slice(eq + 1);
  }
  state.counter = (state.counter || 0) + 1;
  const imageId = 'sha256:' + sha256hex(JSON.stringify({ file, ctx, target, labels: labelObj, n: state.counter }));
  state.local[target] = { imageId, labels: labelObj };
  saveState(state);
  console.log(`Successfully built ${imageId}`);
  console.log(`Successfully tagged ${target}`);
  process.exit(0);
}

// ---------- tag ----------
if (op === 'tag') {
  const src = argv[1];
  const dst = argv[2];
  const rec = state.local[src];
  if (!rec) {
    console.error(`Error: No such image: ${src}`);
    process.exit(1);
  }
  state.local[dst] = { imageId: rec.imageId, labels: rec.labels };
  saveState(state);
  process.exit(0);
}

// ---------- push ----------
if (op === 'push') {
  const ref = argv[1];
  const parsed = refParse(ref);
  const slot = hostSlot(state, parsed.host);
  if (!state.auth[parsed.host]) {
    console.error('Error response from daemon: unauthorized: authentication required');
    process.exit(1);
  }
  const rec = state.local[ref];
  if (!rec) {
    console.error(`An image does not exist locally with the tag: ${ref}`);
    process.exit(1);
  }
  if (failClass('push', slot, parsed.name, parsed.tag)) {
    console.error(`Error response from daemon: Get "https://${parsed.host}/v2/": dial tcp 1.2.3.4:443: connect: connection refused`);
    process.exit(1);
  }
  let digest = digestOf(rec.imageId);
  if (skewFor(slot, parsed.repo, parsed.tag)) digest = skewDigest(digest);
  const slotState = (state.slots[slot] = state.slots[slot] || { repos: {} });
  const repoState = (slotState.repos[parsed.repo] = slotState.repos[parsed.repo] || { tags: {} });
  repoState.tags[parsed.tag] = { digest, imageId: rec.imageId, labels: rec.labels };
  saveState(state);
  console.log(`The push refers to repository [${parsed.host}/${parsed.repo}]`);
  console.log('a1b2c3: Prepared');
  console.log('a1b2c3: Pushed');
  console.log(`${parsed.tag}: digest: ${digest} size: 1571`);
  process.exit(0);
}

// ---------- pull ----------
if (op === 'pull') {
  const ref = argv[1];
  const parsed = refParse(ref);
  const slot = hostSlot(state, parsed.host);
  if (!state.auth[parsed.host]) {
    console.error('Error response from daemon: unauthorized: authentication required');
    process.exit(1);
  }
  const cls = failClass('pull', slot, parsed.name, parsed.tag);
  if (cls === 'auth') {
    console.error('Error response from daemon: unauthorized: authentication required');
    process.exit(1);
  }
  if (cls === 'network') {
    console.error(`Error response from daemon: Get "https://${parsed.host}/v2/": dial tcp 1.2.3.4:443: connect: connection refused`);
    process.exit(1);
  }
  if (cls) {
    console.error(`Error response from daemon: pull access denied for ${parsed.repo}, repository does not exist or may require 'docker login'`);
    process.exit(1);
  }
  const rec = state.slots?.[slot]?.repos?.[parsed.repo]?.tags?.[parsed.tag];
  if (!rec) {
    console.error(`Error response from daemon: manifest for ${ref} not found: manifest unknown: manifest unknown`);
    process.exit(1);
  }
  state.local[ref] = { imageId: rec.imageId, labels: rec.labels };
  saveState(state);
  console.log(`${rec.imageId}: Pulling from ${parsed.repo}`);
  console.log('a1b2c3: Pulling fs layer');
  console.log(`Digest: ${rec.digest}`);
  console.log(`Status: Downloaded newer image for ${ref}`);
  process.exit(0);
}

// ---------- image inspect ----------
if (op === 'image' && argv[1] === 'inspect') {
  let tmpl = '';
  let ref = '';
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--format' || argv[i] === '-f') tmpl = argv[++i];
    else ref = argv[i];
  }
  const rec = state.local[ref];
  if (!rec) {
    console.error(`Error: No such image: ${ref}`);
    process.exit(1);
  }
  if (tmpl === '{{.Id}}') {
    console.log(rec.imageId);
    process.exit(0);
  }
  if (tmpl === '{{json .Config.Labels}}') {
    const labels = rec.labels || {};
    console.log(Object.keys(labels).length ? JSON.stringify(labels) : 'null');
    process.exit(0);
  }
  console.error(`mock-docker: 不支持的模板: ${tmpl}`);
  process.exit(2);
}

console.error(`mock-docker: 不支持的命令: ${argv.join(' ')}`);
process.exit(2);
