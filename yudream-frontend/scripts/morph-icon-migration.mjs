// 一次性迁移脚本：把存量图标字符串人工映射到 lucide（morph:* / i-lucide:*）
// 用法：node scripts/morph-icon-migration.mjs [--apply]（默认只校验+预览）
import fs from 'node:fs'
import path from 'node:path'
import * as lucideIcons from 'lucide'

const FRONTEND_ROOT = path.resolve(import.meta.dirname, '..')

// ============ 人工映射表（逐条判断；brand/无可靠等价物者不列入 ============ )
// 键：源图标字面量（不含 i- 前缀）；值：lucide kebab 名称
const MAPPING = {
  // ---- ri: 动作/导航 ----
  'ri:search-line': 'search',
  'ri:add-line': 'plus',
  'ri:delete-bin-line': 'trash-2',
  'ri:delete-bin-6-line': 'trash-2',
  'ri:refresh-line': 'refresh-cw',
  'ri:close-line': 'x',
  'ri:close-fill': 'x',
  'ri:close-circle-fill': 'circle-x',
  'ri:close-circle-line': 'circle-x',
  'ri:save-3-line': 'save',
  'ri:save-line': 'save',
  'ri:arrow-down-s-line': 'chevron-down',
  'ri:arrow-up-s-line': 'chevron-up',
  'ri:arrow-right-s-line': 'chevron-right',
  'ri:arrow-left-s-line': 'chevron-left',
  'ri:arrow-right-line': 'arrow-right',
  'ri:arrow-left-line': 'arrow-left',
  'ri:arrow-up-line': 'arrow-up',
  'ri:arrow-down-line': 'arrow-down',
  'ri:arrow-right-up-line': 'arrow-up-right',
  'ri:arrow-up-circle-line': 'circle-arrow-up',
  'ri:arrow-go-back-line': 'undo-2',
  'ri:arrow-go-forward-line': 'redo-2',
  'ri:arrow-down-double-line': 'chevrons-down',
  'ri:corner-down-right-line': 'corner-down-right',
  'ri:expand-left-right-line': 'unfold-horizontal',
  'ri:contract-left-right-line': 'fold-horizontal',
  'ri:fullscreen-line': 'maximize',
  'ri:fullscreen-exit-line': 'minimize',
  'ri:external-link-line': 'external-link',
  'ri:external-link-fill': 'external-link',
  'ri:login-circle-line': 'log-in',
  'ri:login-box-line': 'log-in',
  'ri:logout-circle-r-line': 'log-out',
  'ri:upload-2-line': 'upload',
  'ri:upload-cloud-2-line': 'cloud-upload',
  'ri:download-2-line': 'download',
  'ri:download-line': 'download',
  'ri:download-cloud-2-line': 'cloud-download',
  'ri:download-cloud-line': 'cloud-download',
  'ri:send-plane-line': 'send',
  'ri:send-plane-2-line': 'send',
  'ri:send-plane-2-fill': 'send',
  'ri:mail-send-line': 'send',
  'ri:drag-move-2-line': 'move',
  'ri:play-line': 'play',
  'ri:pause-line': 'pause',
  'ri:pause-mini-line': 'pause',
  'ri:play-circle-line': 'circle-play',
  'ri:pause-circle-line': 'circle-pause',
  'ri:stop-circle-line': 'circle-stop',
  'ri:stop-line': 'square',
  'ri:stop-fill': 'square',
  'ri:skip-forward-line': 'skip-forward',
  'ri:restart-line': 'rotate-cw',
  'ri:eject-line': null, // lucide 无 eject
  'ri:history-line': 'history',
  'ri:timer-line': 'timer',
  'ri:time-line': 'clock',
  'ri:speed-up-line': 'fast-forward',
  'ri:sort-desc': 'arrow-down-wide-narrow',
  'ri:draggable': 'move',
  'ri:cursor-line': 'mouse-pointer',
  'ri:crosshair-2-line': 'crosshair',
  'ri:aim-line': 'crosshair',
  'ri:focus-3-line': 'crosshair',
  'ri:rocket-line': 'rocket',
  'ri:flashlight-line': 'flashlight',
  'ri:eraser-line': 'eraser',
  'ri:equalizer-line': 'sliders-horizontal',
  'ri:magic-line': 'wand-sparkles',
  'ri:quill-pen-line': 'feather',
  'ri:edit-line': 'pencil',
  'ri:edit-2-line': 'pencil',
  'ri:edit-box-line': 'square-pen',
  'ri:image-edit-line': null, // 无直接等价，保留回退
  'ri:record-circle-line': 'circle-dot',
  'ri:double-quotes-l': 'quote',
  // ---- ri: 状态/反馈 ----
  'ri:error-warning-line': 'triangle-alert',
  'ri:alert-line': 'triangle-alert',
  'ri:information-line': 'info',
  'ri:question-line': 'circle-question-mark',
  'ri:checkbox-circle-line': 'circle-check',
  'ri:check-line': 'check',
  'ri:subtract-line': 'minus',
  'ri:thumb-up-line': 'thumbs-up',
  'ri:thumb-up-fill': 'thumbs-up',
  'ri:thumb-down-line': 'thumbs-down',
  'ri:thumb-down-fill': 'thumbs-down',
  'ri:sparkling-line': 'sparkles',
  'ri:sparkling-2-line': 'sparkles',
  'ri:sparkling-2-fill': 'sparkles',
  'ri:lightbulb-line': 'lightbulb',
  'ri:ghost-line': 'ghost',
  'ri:copyright-line': 'copyright',
  'ri:at-line': 'at-sign',
  'ri:fingerprint-line': 'fingerprint',
  'ri:eye-line': 'eye',
  'ri:eye-off-line': 'eye-off',
  'ri:wifi-line': 'wifi',
  'ri:cloud-line': 'cloud',
  'ri:cloud-off-line': 'cloud-off',
  'ri:pulse-line': 'activity',
  'ri:bug-line': 'bug',
  'ri:flask-line': 'flask-conical',
  'ri:brain-line': 'brain',
  'ri:camera-line': 'camera',
  'ri:qr-code-line': 'qr-code',
  'ri:compass-3-line': 'compass',
  // ---- ri: 文件/文档 ----
  'ri:file-text-line': 'file-text',
  'ri:file-list-3-line': 'file-text',
  'ri:file-list-2-line': 'file-text',
  'ri:file-2-line': 'file',
  'ri:file-add-line': 'file-plus',
  'ri:file-search-line': 'file-search',
  'ri:file-code-line': 'file-code',
  'ri:file-excel-2-line': 'file-spreadsheet',
  'ri:file-word-2-line': 'file-text',
  'ri:file-lock-line': 'file-lock',
  'ri:attachment-2': 'paperclip',
  'ri:folder-line': 'folder',
  'ri:folder-3-line': 'folder',
  'ri:folder-open-line': 'folder-open',
  'ri:folder-add-line': 'folder-plus',
  'ri:file-copy-line': 'copy',
  'ri:book-open-line': 'book-open',
  'ri:book-2-line': 'book',
  'ri:book-shelf-line': 'library',
  'ri:book-marked-line': 'book-marked',
  'ri:article-line': 'newspaper',
  'ri:markdown-line': null, // lucide 无 markdown，保留回退
  'ri:task-line': 'list-todo',
  'ri:list-check-2': 'list-checks',
  'ri:checkbox-multiple-line': 'list-checks',
  'ri:play-list-add-line': 'list-plus',
  'ri:inbox-line': 'inbox',
  'ri:inbox-archive-line': 'archive',
  'ri:inbox-unarchive-line': 'archive-restore',
  'ri:archive-drawer-line': 'archive',
  // ---- ri: 对象/形状 ----
  'ri:image-line': 'image',
  'ri:image-2-line': 'image',
  'ri:image-add-line': 'image-plus',
  'ri:image-circle-line': null, // 保留回退
  'ri:image-ai-line': null, // 保留回退
  'ri:database-2-line': 'database',
  'ri:hard-drive-2-line': 'hard-drive',
  'ri:hard-drive-3-line': 'hard-drive',
  'ri:server-line': 'server',
  'ri:cpu-line': 'cpu',
  'ri:stack-line': 'layers',
  'ri:layout-grid-line': 'layout-grid',
  'ri:apps-2-line': 'layout-grid',
  'ri:dashboard-line': 'gauge',
  'ri:dashboard-3-line': 'layout-dashboard',
  'ri:layout-2-line': 'layout-dashboard',
  'ri:layout-line': 'layout-dashboard',
  'ri:layout-4-line': 'layout-dashboard',
  'ri:layout-top-line': 'panel-top',
  'ri:sidebar-fold-line': 'panel-left-close',
  'ri:sidebar-unfold-line': 'panel-left-open',
  'ri:window-line': 'app-window',
  'ri:computer-line': 'monitor',
  'ri:smartphone-line': 'smartphone',
  'ri:tablet-line': 'tablet',
  'ri:t-shirt-2-line': 'shirt',
  'ri:wallet-3-line': 'wallet',
  'ri:store-2-line': 'store',
  'ri:building-2-line': 'building-2',
  'ri:building-4-line': 'building',
  'ri:home-4-line': 'house',
  'ri:key-2-line': 'key-round',
  'ri:key-line': 'key',
  'ri:lock-password-line': 'lock-keyhole',
  'ri:id-card-line': 'id-card',
  'ri:graduation-cap-line': 'graduation-cap',
  'ri:price-tag-3-line': 'tag',
  'ri:pushpin-line': 'pin',
  'ri:pushpin-2-fill': 'pin',
  'ri:bookmark-3-line': 'bookmark',
  'ri:map-2-line': 'map',
  'ri:palette-line': 'palette',
  'ri:brush-line': 'brush',
  'ri:menu-line': 'menu',
  'ri:menu-2-line': 'menu',
  'ri:more-2-fill': 'ellipsis',
  'ri:node-tree': 'list-tree',
  'ri:mind-map': 'waypoints',
  'ri:flow-chart': 'workflow',
  'ri:organization-chart': 'network',
  'ri:git-branch-line': 'git-branch',
  'ri:git-merge-line': 'git-merge',
  'ri:git-repository-line': 'folder-git-2',
  'ri:function-ai-line': 'function-square',
  'ri:code-line': 'code',
  'ri:code-s-slash-line': 'code',
  'ri:braces-line': 'braces',
  'ri:terminal-box-line': 'square-terminal',
  'ri:terminal-window-line': 'square-terminal',
  'ri:radar-line': 'radar',
  'ri:crosshair-2-fill': 'crosshair',
  // ---- 补漏（第二轮残留） ----
  'ri:loader-4-line': 'loader-circle',
  'ri:links-line': 'link',
  'ri:settings-3-line': 'settings',
  'ri:settings-4-line': 'settings-2',
  'ri:tools-line': 'wrench',
  'ri:link': 'link',
  'ri:link-m': 'link',
  'ri:exchange-line': 'arrow-left-right',
  'ri:translate-2': 'languages',
  'ri:rss-line': 'rss',
  'ri:notification-2-line': 'bell',
  'ri:mail-line': 'mail',
  'ri:line-chart-line': 'chart-line',
  'ri:badge-check-line': 'badge-check',
  'ri:alarm-warning-line': 'alarm-clock',
  // ---- ri: 用户/社交 ----
  'ri:user-3-line': 'user',
  'ri:user-add-line': 'user-plus',
  'ri:user-settings-line': 'user-cog',
  'ri:user-smile-line': 'circle-user-round',
  'ri:spy-line': 'venetian-mask',
  'ri:team-line': 'users',
  'ri:group-line': 'users',
  'ri:shield-check-line': 'shield-check',
  'ri:shield-keyhole-line': 'shield',
  'ri:shield-user-line': 'shield-user',
  'ri:chat-3-line': 'message-circle',
  'ri:chat-smile-3-line': 'message-circle-more',
  'ri:message-3-line': 'message-square',
  'ri:question-answer-line': 'messages-square',
  'ri:chat-settings-line': null, // 保留回退
  'ri:notification-3-line': 'bell',
  'ri:robot-2-line': 'bot',
  'ri:puzzle-2-line': 'puzzle',
  'ri:puzzle-line': 'puzzle',
  'ri:plugin-line': 'puzzle',
  'ri:plug-line': 'plug',
  'ri:global-line': 'globe',
  'ri:sun-line': 'sun',
  'ri:moon-line': 'moon',
  'ri:star-line': 'star',
  'ri:smile-line': 'smile',
  'ri:bubble-chart-line': null, // 保留回退
  // ---- ri: 通用（选择器内置/回退值） ----
  'ri:apps-2-fill': 'layout-grid',
  'ri:search-eye-line': 'zoom-in',
  'ri:menu-search-line': 'text-search',
  'ri:token-swap-line': 'arrow-left-right',
  'ri:share-forward-line': 'share',
  'ri:share-circle-line': 'share-2',
  'ri:align-left': 'align-left',
  'ri:circle-fill': 'circle',
  // ---- 其他集合 ----
  'ep:caret-bottom': 'chevron-down',
  'ep:caret-top': 'chevron-up',
  'ep:caret-right': 'chevron-right',
  'ep:caret-left': 'chevron-left',
  'ep:search': 'search',
  'ep:refresh': 'refresh-cw',
  'ep:edit': 'pencil',
  'ep:arrow-right': 'arrow-right',
  'ep:arrow-left': 'arrow-left',
  'ep:arrow-down': 'arrow-down',
  'mdi:pencil': 'pencil',
  'mdi:eye': 'eye',
  'mdi:delete': 'trash-2',
  'mdi:swap-horizontal': 'arrow-left-right',
  'mdi:logout': 'log-out',
  'mdi:keyboard': 'keyboard',
  'mdi:home': 'house',
  'mdi:close': 'x',
  'mdi:arrow-expand-right': 'chevrons-right',
  'mdi:arrow-expand-left': 'chevrons-left',
  'mdi:account': 'circle-user-round',
  'carbon:close': 'x',
  'carbon:zoom-in': 'zoom-in',
  'carbon:zoom-out': 'zoom-out',
  'carbon:user-avatar-filled': 'circle-user-round',
  // carbon:rotate-180 实际命中的是 CSS 旋转类场景，图标保留回退
  'carbon:rotate': 'rotate-cw',
  'carbon:checkmark': 'check',
  'ant-design:link-outlined': 'link',
  'ant-design:info-circle-filled': 'info',
  'ant-design:home-twotone': 'house',
  'ant-design:exclamation-circle-filled': 'triangle-alert',
  'ant-design:close-circle-filled': 'circle-x',
  'ant-design:check-circle-filled': 'circle-check',
  'ant-design:caret-up-filled': 'chevron-up',
  'ant-design:caret-down-filled': 'chevron-down',
  'heroicons-solid:menu-alt-3': 'menu',
  'tabler:mood-smile': 'smile',
  'tabler:mood-empty': 'meh',
  'tabler:chart-area-line': 'chart-area',
  'icon-park-outline:upload': 'upload',
  'icon-park-outline:delete': 'trash-2',
  'icon-park-outline:to-top-one': 'arrow-up-to-line',
  'icon-park-outline:square': 'square',
  'icon-park-outline:right-bar': 'panel-right',
  'icon-park-outline:left-bar': 'panel-left',
  'icon-park-outline:preview-open': 'eye',
  'icon-park-outline:arrow-circle-right': 'circle-chevron-right',
  'icon-park-outline:arrow-circle-left': 'circle-chevron-left',
  'icon-park-twotone:planet': 'earth',
  'ic:twotone-double-arrow': 'chevrons-right',
  'hugeicons:mouse-right-click-06': 'mouse-pointer-click',
  'ph:image-broken-duotone': 'image-off',
  'line-md:loading-loop': 'loader-circle+animate-spin',
  'line-md:loading-twotone-loop': 'loader-circle+animate-spin',
  'material-symbols:expand-all-rounded': 'unfold-vertical',
  'codicon:color-mode': 'sun-moon',
  'ci:calendar-days': 'calendar-days',
  'ix:upload-success': 'circle-check-big',
  'ix:upload-fail': 'circle-x',
  // tdesign:mobile-blocked-filled 无等价物，保留回退
  'noto:identification-card': 'id-card',
}

// 品牌与无等价物：明确保留回退（仅用于报告）
const KEEP = [
  'ri:github-line', 'ri:qq-line', 'ri:wechat-line', 'ri:google-line', 'ri:alipay-line',
  'whh:jqueryui',
]

// ============ morph 索引重建（与 morph-icons.ts 同规则） ============
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

// ============ 校验 ============
const invalid = []
for (const [src, target] of Object.entries(MAPPING)) {
  if (!target) continue
  const lucideName = target.split('+')[0]
  if (!morphIndex.has(lucideName)) invalid.push(`${src} -> ${target}`)
}
if (invalid.length) {
  console.error('✗ 以下映射目标不存在于 morph 索引：')
  for (const item of invalid) console.error('  ' + item)
  process.exit(1)
}
console.log(`✓ 映射 ${Object.keys(MAPPING).filter(k => MAPPING[k]).length} 条全部通过 morph 索引校验`)

// ============ 扫描与替换 ============
const SCAN_DIRS = [
  'apps/core-arco-design-vue/src',
  'apps/component-showcase/src',
  'packages/components/src',
  'packages/composables/src',
  'packages/settings/src',
  'packages/themes/src',
  'packages/types/src',
]
const EXCLUDE_FILES = new Set([
  // Remix 回退目录数据本身不动
  'packages/components/src/basic/icon-picker/icons.ts',
  // morph 数据模块自身
  'packages/components/src/basic/icon/morph-icons.ts',
])
const EXCLUDE_DIR_PARTS = ['node_modules', 'basic/icon/_examples/_morph.vue']

const exts = new Set(['.vue', '.ts', '.tsx'])
function walk(dir) {
  const out = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules') continue
      out.push(...walk(full))
    }
    else if (exts.has(path.extname(entry.name))) {
      out.push(full)
    }
  }
  return out
}

const rel = p => path.relative(FRONTEND_ROOT, p).replaceAll('\\', '/')
// 键按字面量长度降序，避免前缀碰撞（i-ri:link vs i-ri:link-m）
const literalKeys = Object.keys(MAPPING)
  .map(k => `i-${k}`)
  .sort((a, b) => b.length - a.length)

let totalMorph = 0
let totalClass = 0
const changedFiles = []

for (const dir of SCAN_DIRS) {
  const absDir = path.join(FRONTEND_ROOT, dir)
  if (!fs.existsSync(absDir)) continue
  for (const file of walk(absDir)) {
    const relFile = rel(file)
    if (EXCLUDE_FILES.has(relFile) || EXCLUDE_DIR_PARTS.some(p => relFile.includes(p))) continue
    const before = fs.readFileSync(file, 'utf8')
    let after = before
    let morphCount = 0
    let classCount = 0

    // Pass A：整串引用（'i-ri:xxx' / "i-ri:xxx"）→ morph:target
    after = after.replace(/(['"])(i-[a-z0-9-]+:[a-z0-9-]+)\1/g, (m, quote, icon) => {
      const target = MAPPING[icon.slice(2)]
      if (!target) return m
      morphCount++
      return `${quote}morph:${target}${quote}`
    })

    // Pass B：剩余出现（class 属性等内联上下文）→ i-lucide:target
    for (const key of literalKeys) {
      const target = MAPPING[key.slice(2)]
      if (!target) continue
      const lucideName = target.split('+')[0]
      const extra = target.includes('+') ? ` ${target.split('+')[1]}` : ''
      const pattern = new RegExp(`(?<![\\w-])${key}(?![\\w-])`, 'g')
      after = after.replace(pattern, () => {
        classCount++
        return `i-lucide:${lucideName}${extra}`
      })
    }

    if (after !== before) {
      fs.writeFileSync(file, after)
      changedFiles.push(`${relFile} (morph:${morphCount}, class:${classCount})`)
      totalMorph += morphCount
      totalClass += classCount
    }
  }
}

console.log(`\n✓ 替换完成：morph 通道 ${totalMorph} 处，类通道 ${totalClass} 处，涉及 ${changedFiles.length} 个文件`)
for (const f of changedFiles) console.log('  ' + f)
