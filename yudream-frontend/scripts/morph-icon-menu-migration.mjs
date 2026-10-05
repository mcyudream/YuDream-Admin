// 一次性迁移：8080 实例系统菜单图标 i-ri:* -> morph:*（带回滚日志）
// 用法：node scripts/morph-icon-menu-migration.mjs <token> [--apply]
import fs from 'node:fs'

const BASE = 'http://localhost:9002/api'
const token = process.argv[2]
const APPLY = process.argv.includes('--apply')
if (!token) {
  console.error('用法: node scripts/morph-icon-menu-migration.mjs <token> [--apply]')
  process.exit(1)
}

// 键：菜单里的 i-ri 字面量；值：lucide kebab 名；null = 保留回退
const DB_MAPPING = {
  'i-ri:settings-3-line': 'settings',
  'i-ri:gamepad-line': 'gamepad-2',
  'i-ri:file-list-3-line': 'file-text',
  'i-ri:dashboard-3-line': 'layout-dashboard',
  'i-ri:forum-line': 'messages-square',
  'i-ri:database-2-line': 'database',
  'i-ri:shield-check-line': 'shield-check',
  'i-ri:bar-chart-2-line': 'chart-column',
  'i-ri:history-line': 'history',
  'i-ri:list-check-3': 'list-checks',
  'i-ri:list-check-3-line': 'list-checks',
  'i-ri:trophy-line': 'trophy',
  'i-ri:key-2-line': 'key-round',
  'i-ri:palette-line': 'palette',
  'i-ri:terminal-box-line': 'square-terminal',
  'i-ri:robot-2-line': 'bot',
  'i-ri:store-2-line': 'store',
  'i-ri:t-shirt-2-line': 'shirt',
  'i-ri:calendar-event-line': 'calendar-days',
  'i-ri:archive-stack-line': 'boxes',
  'i-ri:newspaper-line': 'newspaper',
  'i-ri:book-3-line': 'book',
  'i-ri:map-2-line': 'map',
  'i-ri:tools-line': 'wrench',
  'i-ri:team-line': 'users',
  'i-ri:chat-3-line': 'message-circle',
  'i-ri:node-tree': 'list-tree',
  'i-ri:dashboard-horizontal-line': 'layout-dashboard',
  'i-ri:puzzle-2-line': 'puzzle',
  'i-ri:archive-drawer-line': 'archive',
  'i-ri:wallet-3-line': 'wallet',
  'i-ri:bank-card-line': 'credit-card',
  'i-ri:ghost-smile-line': 'ghost',
  'i-ri:group-line': 'users',
  'i-ri:id-card-line': 'id-card',
  'i-ri:file-text-line': 'file-text',
  'i-ri:add-box-line': 'square-plus',
  'i-ri:archive-line': 'archive',
  'i-ri:server-line': 'server',
  'i-ri:bar-chart-box-line': 'chart-column-big',
  'i-ri:coins-line': 'coins',
  'i-ri:map-pin-time-line': 'calendar-check',
  'i-ri:folder-settings-line': 'folder-cog',
  'i-ri:folder-image-line': 'images',
  'i-ri:price-tag-3-line': 'tag',
  'i-ri:questionnaire-line': 'circle-question-mark',
  'i-ri:file-paper-2-line': 'file',
  'i-ri:quill-pen-line': 'feather',
  'i-ri:terminal-window-line': 'square-terminal',
  'i-ri:login-box-line': 'log-in',
  'i-ri:flow-chart': 'workflow',
  'i-ri:file-search-line': 'file-search',
  'i-ri:radar-line': 'radar',
  'i-ri:cpu-line': 'cpu',
  'i-ri:settings-4-line': 'settings-2',
  'i-ri:menu-2-line': 'menu',
  'i-ri:information-line': 'info',
  'i-ri:organization-chart': 'network',
  'i-ri:shield-user-line': 'shield-user',
  'i-ri:user-settings-line': 'user-cog',
  'i-ri:sparkling-2-fill': 'sparkles',
  'i-ri:smartphone-line': 'smartphone',
  'i-ri:links-line': 'link',
  'i-ri:share-circle-line': 'share-2',
  'i-ri:mail-download-line': 'inbox',
  'i-ri:book-open-line': 'book-open',
  'i-ri:survey-line': 'clipboard-list',
  'i-ri:file-word-2-line': 'file-text',
  'i-ri:file-list-2-line': 'file-text',
  'i-ri:upload-2-line': 'upload',
  'i-ri:git-repository-line': 'folder-git-2',
  'i-ri:edit-box-line': 'square-pen',
  'i-ri:checkbox-multiple-line': 'list-checks',
  'i-ri:link-m': 'link',
  'i-ri:flag-line': 'flag',
  'i-ri:verified-badge-line': 'badge-check',
  'i-ri:gift-2-line': 'gift',
  'i-ri:shopping-bag-3-line': 'shopping-bag',
  'i-ri:hand-coin-line': 'hand-coins',
  'i-ri:equalizer-line': 'sliders-horizontal',
  'i-ri:school-line': 'school',
  'i-ri:mail-line': 'mail',
  'i-ri:box-3-line': 'box',
  'i-ri:menu-line': 'menu',
  'i-ri:file-zip-line': 'file-archive',
  'i-ri:macbook-line': 'laptop',
  'i-ri:rocket-2-line': 'rocket',
  'i-ri:file-info-line': 'file-question-mark',
  'i-ri:edit-line': 'pencil',
  'i-ri:graduation-cap-line': 'graduation-cap',
  'i-ri:progress-5-line': 'chart-gantt',
  'i-ri:todo-line': 'list-todo',
  'i-ri:checkbox-circle-line': 'circle-check',
  'i-ri:book-2-line': 'book',
  'i-ri:treasure-map-line': 'map',
  'i-ri:chess-line': 'puzzle',
  'i-ri:brain-line': 'brain',
  'i-ri:user-heart-line': 'user-round-search',
  'i-ri:download-cloud-2-line': 'cloud-download',
  'i-ri:sparkling-2-line': 'sparkles',
  'i-ri:global-line': 'globe',
  'i-ri:layout-4-line': 'layout-dashboard',
  'i-ri:timer-line': 'timer',
  'i-ri:pulse-line': 'activity',
  'i-ri:hammer-line': 'hammer',
  'i-ri:git-branch-line': 'git-branch',
  'i-ri:folder-2-line': 'folder',
  'i-ri:code-s-slash-line': 'code',
  'i-ri:file-copy-2-line': 'copy',
  'i-ri:time-line': 'clock',
  'i-ri:rss-line': 'rss',
  'i-ri:send-plane-line': 'send',
  'i-ri:notification-3-line': 'bell',
  'i-ri:draft-line': 'file-pen',
  'i-ri:bookmark-line': 'bookmark',
  // 明确保留回退：品牌/无等价物
  'i-ri:alipay-line': null,
  'i-ri:layout-masonry-line': null,
  'i-ri:drag-drop-line': null,
  'i-ri:chat-settings-line': null,
}

// ---- 校验目标存在性（与 morph-icons.ts 同规则重建索引） ----
import * as lucideIcons from 'lucide'
function toKebabCase(exportName) {
  return exportName
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([a-zA-Z])(\d)/g, '$1-$2')
    .replace(/(\d)(?=\d)/g, '$1-')
    .toLowerCase()
}
const morphIndex = new Set()
for (const [exportName, value] of Object.entries(lucideIcons)) {
  if (Array.isArray(value)) morphIndex.add(toKebabCase(exportName))
}
const targets = new Map()
const invalid = []
for (const [src, target] of Object.entries(DB_MAPPING)) {
  if (target === null) {
    targets.set(src, null)
    continue
  }
  if (morphIndex.has(target)) {
    targets.set(src, target)
  }
  else {
    invalid.push(`${src} -> ${target}`)
  }
}
if (invalid.length) {
  console.error('✗ 以下映射目标不存在于 morph 索引：')
  for (const item of invalid) console.error('  ' + item)
  process.exit(1)
}

// ---- 拉取菜单树 ----
async function api(method, url, body) {
  const res = await fetch(`${BASE}${url}`, {
    method,
    headers: { Authorization: token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json()
  if (json.code !== 200 && json.code !== 1) {
    throw new Error(`${method} ${url} -> ${res.status} ${JSON.stringify(json).slice(0, 200)}`)
  }
  return json.data
}

const tree = await api('GET', '/system/menus')
const flat = []
function walk(list) {
  for (const m of list) {
    flat.push(m)
    if (m.children) walk(m.children)
  }
}
walk(tree)

// 注意：null 目标（保留回退）必须排除，否则会把 icon 改写成字面量 "morph:null"
const pending = flat.filter(m => m.icon && targets.get(m.icon))
console.log(`菜单节点 ${flat.length}，待更新 ${pending.length}，保留回退 ${flat.filter(m => m.icon && targets.get(m.icon) === null).length}`)

const rollback = []
let ok = 0
let fail = 0
for (const m of pending) {
  const next = targets.get(m.icon)
  rollback.push({ code: m.code, name: m.name, from: m.icon, to: next })
  if (!APPLY) {
    console.log(`[dry] ${m.code} ${m.name}: ${m.icon} -> morph:${next}`)
    continue
  }
  const payload = {
    name: m.name,
    type: m.type,
    parentCode: m.parentCode,
    module: m.module,
    icon: `morph:${next}`,
    path: m.path,
    component: m.component,
    link: m.link,
    sort: m.sort,
    visible: m.visible,
    permission: m.permission,
    status: m.status,
  }
  try {
    await api('PUT', `/system/menus?code=${encodeURIComponent(m.code)}`, payload)
    ok++
  }
  catch (e) {
    fail++
    console.error(`✗ ${m.code} ${m.name}: ${e.message}`)
  }
}

fs.writeFileSync(new URL('./menu-icon-rollback.json', import.meta.url), JSON.stringify(rollback, null, 2))
console.log(APPLY ? `\n✓ 更新 ${ok} 成功 / ${fail} 失败；回滚日志 scripts/menu-icon-rollback.json` : `\n[dry] 共 ${rollback.length} 条待更新，未写入；--apply 执行`)
