// 一次性修复：把被 morph-icon-menu-migration.mjs null 目标 bug 写成 "morph:null" 的菜单图标改正
import fs from 'node:fs'
import * as lucideIcons from 'lucide'

const BASE = 'http://localhost:9002/api'
const token = process.argv[2]
const APPLY = process.argv.includes('--apply')
if (!token) {
  console.error('用法: node scripts/morph-icon-menu-repair.mjs <token> [--apply]')
  process.exit(1)
}

// 修复值：lucide 目标（morph:xxx）或 null = 还原回滚日志里的原始 i-ri 值
const FIX = {
  'platform:content-hub': 'layout-template',
  'platform:form:designer': 'square-dashed-mouse-pointer',
  'plugin:yudream-alipay:route:yudreamAlipay:platform-plugin-yudream-alipay-settings': null,
  'plugin:yudream-alipay:route:yudreamAlipay:platform-plugin-yudream-alipay-my-orders': null,
  'plugin:minecraft-activity-proof:route:minecraftActivityProof:platform-plugin-yudream-student-info-activity-square': 'party-popper',
  'plugin:ymcl-adapter:route:ymclAdapter:platform-plugin-ymcl-adapter-admin-chrome-home': 'panels-top-left',
  'plugin:qqbot-automation:module:qqbotAutomation': 'bot-message-square',
  'plugin:web-card:module:webCard': 'gallery-vertical-end',
}

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
for (const [code, target] of Object.entries(FIX)) {
  if (target && !morphIndex.has(target)) {
    console.error(`✗ ${code} -> ${target} 不存在于 morph 索引`)
    process.exit(1)
  }
}

const rollback = JSON.parse(fs.readFileSync(new URL('./menu-icon-rollback.json', import.meta.url), 'utf8'))

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

let ok = 0
let fail = 0
for (const m of flat) {
  if (!(m.icon === 'morph:null' && m.code in FIX)) continue
  const target = FIX[m.code]
  const next = target ? `morph:${target}` : rollback.find(r => r.code === m.code)?.from
  if (!next) {
    console.error(`✗ ${m.code} 找不到修复值`)
    fail++
    continue
  }
  console.log(`[repair] ${m.name}: ${m.icon} -> ${next}`)
  if (!APPLY) continue
  const payload = {
    name: m.name,
    type: m.type,
    parentCode: m.parentCode,
    module: m.module,
    icon: next,
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
    console.error(`✗ ${m.code}: ${e.message}`)
  }
}
console.log(APPLY ? `\n✓ 修复 ${ok} / 失败 ${fail}` : '\n[dry] 未写入；--apply 执行')
