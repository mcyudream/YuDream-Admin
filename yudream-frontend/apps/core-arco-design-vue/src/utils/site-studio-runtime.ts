import type { SiteDocument, StudioFeatures, StudioPage, StudioParticlePreset } from '../types/site-studio'
import { reactive } from 'vue'
import apiCms from '@/api/modules/platform-cms'
import apiPublicTheme from '@/api/modules/public-theme'
import systemClient from '@/api/modules/system-client'
import { useAppSettingsStore } from '@/store/modules/app/settings'
import { loadSiteNavigation } from '@/views/site/site-navigation'
import { toBackendAssetUrl } from './backend-url'
import { validateStudioUrl } from './site-studio-validation'

/**
 * 站点文档运行时：公开站与草稿预览的 iframe 不执行任何脚本，站点功能由宿主在同源文档上实现。
 * 文档只声明意图（features / page.settings / data-yd-* 属性），行为全部是宿主内置、可审计的代码：
 * - 背景音乐与点击音效（音频状态在宿主侧，跨页面保持；按钮 data-yd-action="toggle-music|toggle-sound"）
 * - 当前页链接 aria-current="page"（配合样式类 current 状态做导航高亮）
 * - 氛围粒子（features.particles，页面 settings.particles 覆盖）
 * - 页面首次进入提示（settings.toast，同一会话每页一次）
 * - 站点信息：data-yd-site="name|description|logo|copyright|year"（宿主「系统设置」中的站点名称、Logo 等，随设置变化，不写死在文档里）
 * - 复制：按钮 data-yd-action="copy" 复制自身 title（可经 data-yd-title 绑定字段，如服务器地址）
 * - 数据绑定：data-yd-source 容器内 data-yd-item 为条目模板，data-yd-text/-href/-src/-title 绑定字段；
 *   无数据时保留作者写的示例条目（data-yd-sample 元素仅在示例状态显示，data-yd-live 仅在真实数据时显示；
 *   容器外的标记用 data-yd-for="数据源" 关联，如列表上方的“示例/实时”状态条）
 */

// ===== 音频（宿主侧单例） =====

type EffectName = 'click' | 'button' | 'toast'
const EFFECTS_KEY = 'yd.site.audio.effects'

export const studioAudio = reactive({ musicOn: false, effectsOn: true, hasMusic: false, hasEffects: false })
let audioConfig: NonNullable<StudioFeatures['audio']> | null = null
let audioUrls: { tracks: string[], effects: Partial<Record<EffectName, string>> } = { tracks: [], effects: {} }
let audioKey = ''
let music: HTMLAudioElement | null = null
let trackIndex = 0

function configureAudio(document: SiteDocument) {
  const config = document.features?.audio ?? null
  const key = JSON.stringify([document.id, config, document.assets])
  if (key === audioKey) {
    return
  }
  audioKey = key
  const url = (id?: string) => {
    const asset = id ? document.assets.find(item => item.id === id && item.kind === 'audio') : undefined
    return asset ? toBackendAssetUrl(asset.url) : ''
  }
  audioConfig = config
  audioUrls = {
    tracks: (config?.tracks ?? []).map(track => url(track.assetId)).filter(Boolean),
    effects: { click: url(config?.clickSoundAssetId), button: url(config?.buttonSoundAssetId), toast: url(config?.toastSoundAssetId) },
  }
  studioAudio.hasMusic = audioUrls.tracks.length > 0
  studioAudio.hasEffects = Object.values(audioUrls.effects).some(Boolean)
  const stored = localStorage.getItem(EFFECTS_KEY)
  studioAudio.effectsOn = stored === null ? config?.effectsDefaultOn !== false : stored === '1'
  if (music) {
    music.volume = (config?.musicVolume ?? 0) / 100
  }
  if (!studioAudio.hasMusic) {
    stopStudioAudio()
  }
}

function playEffect(name: EffectName) {
  const src = audioUrls.effects[name] || (name === 'button' ? audioUrls.effects.click : '')
  if (!studioAudio.effectsOn || !src || !audioConfig) {
    return
  }
  const sound = new Audio(src)
  sound.volume = audioConfig.effectVolume / 100
  void sound.play().catch(() => undefined)
}

function playTrack() {
  if (!audioUrls.tracks.length) {
    return
  }
  music ??= new Audio()
  music.src = audioUrls.tracks[trackIndex % audioUrls.tracks.length]!
  music.volume = (audioConfig?.musicVolume ?? 0) / 100
  music.onended = () => {
    trackIndex = (trackIndex + 1) % audioUrls.tracks.length
    playTrack()
  }
  void music.play().catch(() => {
    studioAudio.musicOn = false
  })
}

function toggleMusic() {
  if (!studioAudio.hasMusic) {
    return
  }
  studioAudio.musicOn = !studioAudio.musicOn
  if (studioAudio.musicOn) {
    playTrack()
  }
  else {
    music?.pause()
  }
}

function toggleEffects() {
  studioAudio.effectsOn = !studioAudio.effectsOn
  localStorage.setItem(EFFECTS_KEY, studioAudio.effectsOn ? '1' : '0')
}

/** 离开站点（公开站/预览容器卸载）时停止音乐。 */
export function stopStudioAudio() {
  music?.pause()
  if (music) {
    music.onended = null
    music.removeAttribute('src')
    music.load()
  }
  music = null
  studioAudio.musicOn = false
}

// ===== 粒子 =====

interface ParticlePreset { colors: string[], count: number, vy: [number, number], vx: [number, number], size: [number, number], glow: boolean, spin?: boolean }
const PARTICLES: Record<Exclude<StudioParticlePreset, 'none'>, ParticlePreset> = {
  dust: { colors: ['#ffd98a', '#ffb366', '#fff2c9'], count: 46, vy: [-0.18, -0.05], vx: [-0.08, 0.08], size: [2, 4], glow: true },
  snow: { colors: ['#ffffff', '#e6f2ff', '#cfe3ff'], count: 110, vy: [0.35, 0.9], vx: [-0.25, 0.1], size: [2, 4], glow: false },
  petals: { colors: ['#ffb7d5', '#f48fb9', '#ffe3ef'], count: 54, vy: [0.3, 0.7], vx: [0.15, 0.5], size: [4, 6], glow: false, spin: true },
  spores: { colors: ['#4fe3ef', '#1fa7b6', '#9ff6ff'], count: 50, vy: [-0.45, -0.15], vx: [-0.05, 0.05], size: [2, 4], glow: true },
  embers: { colors: ['#ff8a2a', '#ffb347', '#ff5a1f', '#ffd86b'], count: 70, vy: [-0.9, -0.35], vx: [-0.15, 0.15], size: [2, 3], glow: true },
  stars: { colors: ['#d6b0ff', '#a96bff', '#f0e0ff'], count: 60, vy: [-0.25, 0.25], vx: [-0.25, 0.25], size: [2, 4], glow: true },
}
const between = ([min, max]: [number, number]) => min + Math.random() * (max - min)

function startParticles(frameWindow: Window, host: Element, name: StudioParticlePreset) {
  const reduce = frameWindow.matchMedia('(prefers-reduced-motion: reduce)')
  if (name === 'none' || reduce.matches) {
    return () => undefined
  }
  const preset = PARTICLES[name]
  const canvas = host.ownerDocument.createElement('canvas')
  canvas.className = 'yd-site-particles'
  canvas.setAttribute('aria-hidden', 'true')
  host.prepend(canvas)
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    canvas.remove()
    return () => undefined
  }
  let width = 0
  let height = 0
  let dpr = 1
  const resize = () => {
    dpr = Math.min(frameWindow.devicePixelRatio || 1, 2)
    width = frameWindow.innerWidth
    height = frameWindow.innerHeight
    canvas.width = Math.round(width * dpr)
    canvas.height = Math.round(height * dpr)
  }
  resize()
  const spawn = (initial: boolean) => ({
    x: Math.random() * width,
    y: initial ? Math.random() * height : (preset.vy[0] > 0 ? -10 : height + 10),
    vx: between(preset.vx),
    vy: between(preset.vy),
    size: Math.round(between(preset.size)),
    color: preset.colors[Math.floor(Math.random() * preset.colors.length)]!,
    phase: Math.random() * Math.PI * 2,
    life: 0.4 + Math.random() * 0.6,
  })
  const particles = Array.from({ length: width < 720 ? Math.round(preset.count * 0.55) : preset.count }, () => spawn(true))
  let frame = 0
  const tick = (time: number) => {
    frame = 0
    if (host.ownerDocument.hidden || reduce.matches) {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      return
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, width, height)
    for (const pt of particles) {
      pt.x += pt.vx + Math.sin(time / 1300 + pt.phase) * 0.25
      pt.y += pt.vy
      if (pt.y > height + 12 || pt.y < -12 || pt.x < -12 || pt.x > width + 12) {
        Object.assign(pt, spawn(false))
      }
      ctx.globalAlpha = pt.life * (preset.glow ? 0.55 + Math.sin(time / 420 + pt.phase) * 0.45 : 1)
      ctx.fillStyle = pt.color
      ctx.shadowColor = pt.color
      ctx.shadowBlur = preset.glow ? 8 : 0
      if (preset.spin) {
        ctx.fillRect(Math.round(pt.x), Math.round(pt.y), pt.size, pt.size / 2)
        ctx.fillRect(Math.round(pt.x + pt.size / 4), Math.round(pt.y - pt.size / 4), pt.size / 2, pt.size)
      }
      else {
        ctx.fillRect(Math.round(pt.x), Math.round(pt.y), pt.size, pt.size)
      }
    }
    frame = frameWindow.requestAnimationFrame(tick)
  }
  const wake = () => {
    if (!frame && !host.ownerDocument.hidden && !reduce.matches) {
      frame = frameWindow.requestAnimationFrame(tick)
    }
  }
  wake()
  frameWindow.addEventListener('resize', resize, { passive: true })
  host.ownerDocument.addEventListener('visibilitychange', wake)
  reduce.addEventListener?.('change', wake)
  return () => {
    frameWindow.cancelAnimationFrame(frame)
    frameWindow.removeEventListener('resize', resize)
    host.ownerDocument.removeEventListener('visibilitychange', wake)
    reduce.removeEventListener?.('change', wake)
    canvas.remove()
  }
}

// ===== 页面进入提示 =====

const TOAST_KEY = 'yd.site.toasts'

function showPageToast(document: SiteDocument, page: StudioPage, host: Element) {
  const toast = page.settings?.toast
  if (!toast) {
    return () => undefined
  }
  const seenKey = `${document.id}:${page.id}`
  let seen: string[] = []
  try {
    seen = JSON.parse(sessionStorage.getItem(TOAST_KEY) || '[]') as string[]
  }
  catch {
    seen = []
  }
  if (seen.includes(seenKey)) {
    return () => undefined
  }
  sessionStorage.setItem(TOAST_KEY, JSON.stringify([...seen, seenKey].slice(-200)))
  const doc = host.ownerDocument
  const layer = host.querySelector(':scope > .yd-site-toast-layer') ?? host.appendChild(Object.assign(doc.createElement('div'), { className: 'yd-site-toast-layer' }))
  layer.setAttribute('role', 'status')
  layer.setAttribute('aria-live', 'polite')
  const item = doc.createElement('div')
  item.className = 'yd-site-toast'
  const icon = document.assets.find(asset => asset.id === toast.iconAssetId && asset.kind === 'image')
  if (icon) {
    const img = doc.createElement('img')
    img.className = 'yd-site-toast-icon'
    img.alt = ''
    img.src = icon.url
    item.appendChild(img)
  }
  const copy = doc.createElement('div')
  copy.className = 'yd-site-toast-copy'
  const title = doc.createElement('div')
  title.className = 'yd-site-toast-title'
  title.textContent = toast.title
  copy.appendChild(title)
  if (toast.body) {
    const body = doc.createElement('div')
    body.className = 'yd-site-toast-body'
    body.textContent = toast.body
    copy.appendChild(body)
  }
  item.appendChild(copy)
  const timers: number[] = []
  timers.push(window.setTimeout(() => {
    layer.appendChild(item)
    playEffect('toast')
  }, 440))
  timers.push(window.setTimeout(() => item.classList.add('yd-site-toast--out'), 4000))
  timers.push(window.setTimeout(() => item.remove(), 4300))
  return () => {
    timers.forEach(timer => window.clearTimeout(timer))
    item.remove()
  }
}

// ===== 数据绑定 =====

type DataRecord = Record<string, unknown>
const DATA_TTL = 60_000
const dataCache = new Map<string, { at: number, promise: Promise<DataRecord[]> }>()

function asRecords(value: unknown): DataRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is DataRecord => !!item && typeof item === 'object')
  }
  if (value && typeof value === 'object') {
    for (const key of ['items', 'records', 'list', 'servers', 'activities', 'events']) {
      const nested = (value as DataRecord)[key]
      if (Array.isArray(nested)) {
        return asRecords(nested)
      }
    }
    // 块数据为对象且只有一个数组字段时，取该数组
    const arrays = Object.values(value as DataRecord).filter(Array.isArray)
    if (arrays.length === 1) {
      return asRecords(arrays[0])
    }
  }
  return []
}

/** 站点导航：首页 + CMS 导航 + 站点文档页面 + 插件 siteNav 页面 + 插件市场/知识库（与宿主公开站同一合并规则） */
async function loadNavigation(document: SiteDocument): Promise<DataRecord[]> {
  const navigationJson = await apiCms.publicHome({ skipGlobalToast: true }).then(res => res.data?.settings?.navigationJson as string | undefined, () => undefined)
  const pages = document.pages.filter(page => page.path !== '/').map((page, index) => ({ id: `studio-${page.id}`, label: page.name, url: `/site${page.path}`, visible: true, sort: 1000 + index }))
  const tree = await loadSiteNavigation(navigationJson, pages)
  return tree.map(item => ({ label: item.label, url: item.url, external: /^https?:/i.test(item.url) }))
}

async function loadSource(source: string, limit: number, document: SiteDocument): Promise<DataRecord[]> {
  const quiet = { skipGlobalToast: true }
  if (source === 'site:navigation') {
    return loadNavigation(document)
  }
  if (source.startsWith('block:')) {
    const code = source.slice('block:'.length)
    const res = await apiPublicTheme.context({ blocks: [code], limit })
    return asRecords(res.data.blocks?.[code])
  }
  switch (source) {
    case 'cms:latest': {
      const res = await apiPublicTheme.context({ cmsLatest: limit })
      return asRecords(res.data.cmsPagesLatest).map(item => ({ ...item, url: item.url || (item.slug ? `/site/${encodeURIComponent(String(item.slug))}` : '') }))
    }
    case 'wiki:spaces': {
      const res = await systemClient.get<unknown, { data: unknown }>('api/public/wiki/spaces', quiet)
      return asRecords(res.data).map(item => ({ ...item, url: item.slug ? `/wiki/${encodeURIComponent(String(item.slug))}` : '' }))
    }
    case 'market:plugins': {
      const res = await systemClient.get<unknown, { data: unknown }>('api/public/plugin-market/api/v2/plugins', { ...quiet, params: { page: 1, size: limit } })
      return asRecords(res.data).map(item => ({ ...item, name: item.displayName || item.name || item.code, url: item.code ? `/market/${encodeURIComponent(String(item.code))}` : '' }))
    }
    default:
      return []
  }
}

/** 已知数据源，供工作台检查器提示可选值与字段 */
export const STUDIO_DATA_SOURCES = [
  { value: 'site:navigation', label: '站点导航（CMS 导航 + 站点页面 + 插件页面）', fields: ['label', 'url'] },
  { value: 'block:server-list', label: '服务器列表（Minecraft 服务器插件）', fields: ['name', 'address', 'motd', 'statusText', 'playersText', 'latencyText', 'icon'] },
  { value: 'block:activity-square', label: '活动广场（活动证明插件）', fields: ['title', 'summary', 'statusText', 'meta', 'cover', 'url'] },
  { value: 'block:timeline', label: '大事记（大事记插件）', fields: ['title', 'time', 'summary', 'url'] },
  { value: 'cms:latest', label: '最新文章（CMS）', fields: ['title', 'summary', 'excerpt', 'publishedAt', 'url'] },
  { value: 'wiki:spaces', label: '知识库空间', fields: ['name', 'description', 'slug', 'url'] },
  { value: 'market:plugins', label: '插件市场', fields: ['name', 'description', 'category', 'latestVersion', 'url'] },
] as const

function fetchSource(source: string, limit: number, document: SiteDocument) {
  const key = `${document.id}|${source}|${limit}`
  const cached = dataCache.get(key)
  if (cached && Date.now() - cached.at < DATA_TTL) {
    return cached.promise
  }
  const promise = loadSource(source, limit, document).catch(() => [] as DataRecord[])
  dataCache.set(key, { at: Date.now(), promise })
  return promise
}

function field(record: DataRecord, path: string): unknown {
  return path.split('.').reduce<unknown>((value, key) => value && typeof value === 'object' ? (value as DataRecord)[key] : undefined, record)
}
function text(value: unknown) {
  if (value === null || value === undefined) {
    return ''
  }
  return typeof value === 'object' ? '' : String(value)
}
function safeUrl(value: string, allowAnchor: boolean) {
  try {
    validateStudioUrl(value)
    return allowAnchor || !value.startsWith('#') ? value : ''
  }
  catch {
    return ''
  }
}

function fillItem(item: Element, record: DataRecord) {
  const targets = [item, ...item.querySelectorAll('[data-yd-text],[data-yd-href],[data-yd-src],[data-yd-title]')]
  for (const element of targets) {
    const textPath = element.getAttribute('data-yd-text')
    if (textPath) {
      element.textContent = text(field(record, textPath))
    }
    const hrefPath = element.getAttribute('data-yd-href')
    if (hrefPath) {
      const href = safeUrl(text(field(record, hrefPath)), false)
      if (href) {
        element.setAttribute('href', href)
      }
      else {
        element.removeAttribute('href')
      }
    }
    const titlePath = element.getAttribute('data-yd-title')
    if (titlePath) {
      element.setAttribute('title', text(field(record, titlePath)))
    }
    const srcPath = element.getAttribute('data-yd-src')
    if (srcPath) {
      const src = safeUrl(text(field(record, srcPath)), false)
      if (src) {
        element.setAttribute('src', src)
        element.removeAttribute('hidden')
      }
      else {
        element.setAttribute('hidden', '')
      }
    }
  }
}

/** 站点信息绑定：宿主公开设置（站点名称/简介/Logo/版权），无值时保留作者内容 */
function bindSiteInfo(host: Element) {
  const settings = useAppSettingsStore()
  const copyright = settings.settings.app.copyright
  const values: Record<string, string> = {
    name: settings.siteName,
    description: settings.siteDescription,
    logo: settings.logo,
    copyright: [copyright.dates ? `© ${copyright.dates}` : '', copyright.company].filter(Boolean).join(' '),
    year: String(new Date().getFullYear()),
  }
  for (const element of host.querySelectorAll('[data-yd-site]')) {
    const value = values[element.getAttribute('data-yd-site') || ''] || ''
    if (!value) {
      continue
    }
    if (element.tagName === 'IMG') {
      const src = safeUrl(value, false)
      if (src) {
        element.setAttribute('src', src)
      }
    }
    else {
      element.textContent = value
    }
  }
}

function bindSources(host: Element, isAlive: () => boolean, document: SiteDocument, onFilled: () => void) {
  host.querySelectorAll('[data-yd-live]').forEach(element => element.setAttribute('hidden', ''))
  const containers = [...host.querySelectorAll('[data-yd-source]')]
  for (const container of containers) {
    const source = container.getAttribute('data-yd-source') || ''
    const limit = Math.min(Math.max(Number.parseInt(container.getAttribute('data-yd-limit') || '12', 10) || 12, 1), 50)
    void fetchSource(source, limit, document).then((records) => {
      if (!isAlive() || !records.length) {
        return
      }
      const items = [...container.querySelectorAll('[data-yd-item]')].filter(item => item.closest('[data-yd-source]') === container)
      const template = items[0]
      if (!template) {
        return
      }
      const parent = template.parentElement!
      const anchor = template.nextSibling
      const fragment = host.ownerDocument.createDocumentFragment()
      for (const record of records.slice(0, limit)) {
        const clone = template.cloneNode(true) as Element
        clone.removeAttribute('data-studio-id')
        clone.querySelectorAll('[data-studio-id]').forEach(element => element.removeAttribute('data-studio-id'))
        fillItem(clone, record)
        // 绑定数据的站内链接指向宿主路由（插件页、/market 等），不再映射到站点前缀
        for (const anchor of [clone, ...clone.querySelectorAll('a[data-yd-href]')]) {
          if (anchor.tagName === 'A' && anchor.hasAttribute('data-yd-href')) {
            anchor.setAttribute('data-yd-host-link', '')
          }
        }
        fragment.appendChild(clone)
      }
      parent.insertBefore(fragment, anchor)
      items.forEach(item => item.remove())
      const linked = [...host.querySelectorAll('[data-yd-for]')].filter(element => element.getAttribute('data-yd-for') === source)
      for (const element of [...container.querySelectorAll('[data-yd-sample],[data-yd-live]'), ...linked]) {
        if (element.hasAttribute('data-yd-sample')) {
          element.remove()
        }
        else if (element.hasAttribute('data-yd-live')) {
          element.removeAttribute('hidden')
        }
      }
      container.setAttribute('data-yd-state', 'live')
      onFilled()
    })
  }
}

// ===== 挂载 =====

function normalizePath(value: string) {
  const path = value.split(/[?#]/)[0]!.replace(/\/+$/, '')
  return (path || '/').toLowerCase()
}

/**
 * 在一次页面加载上挂载运行时；返回卸载函数（页面切换或容器卸载时调用）。
 * pagePath 为文档内页面路径（/、/servers）；链接 href 按文档路径或带前缀的站点路径比较。
 */
export function attachStudioRuntime(options: { document: SiteDocument, page: StudioPage, frameWindow: Window, basePath: string }) {
  const { document, page, frameWindow, basePath } = options
  const doc = frameWindow.document
  const host = doc.querySelector('[data-studio-document]')
  if (!host) {
    return () => undefined
  }
  let alive = true
  configureAudio(document)
  const current = normalizePath(page.path)

  // 当前页链接：文档内链接按文档路径比较；站点前缀（/site 或预览前缀）先剥离。数据绑定插入的链接在填充后再标记一次
  const markCurrent = () => {
    for (const anchor of host.querySelectorAll('a[href]')) {
      const href = anchor.getAttribute('href') || ''
      if (!href.startsWith('/')) {
        continue
      }
      let path = normalizePath(href)
      for (const prefix of [basePath.toLowerCase(), '/site']) {
        if (path === prefix) {
          path = '/'
        }
        else if (path.startsWith(`${prefix}/`)) {
          path = path.slice(prefix.length)
        }
      }
      anchor.toggleAttribute('aria-current', path === current)
      if (path === current) {
        anchor.setAttribute('aria-current', 'page')
      }
    }
  }
  markCurrent()

  // 音频按钮状态
  const actionButtons = () => [...host.querySelectorAll('[data-yd-action]')]
  const syncActions = () => {
    for (const button of actionButtons()) {
      const action = button.getAttribute('data-yd-action')
      if (action === 'toggle-music') {
        button.setAttribute('aria-pressed', String(studioAudio.musicOn))
        button.toggleAttribute('hidden', !studioAudio.hasMusic)
      }
      else if (action === 'toggle-sound') {
        button.setAttribute('aria-pressed', String(studioAudio.effectsOn))
        button.toggleAttribute('hidden', !studioAudio.hasEffects)
      }
    }
  }
  syncActions()
  const onClick = (event: MouseEvent) => {
    const target = event.target as Element | null
    const action = target?.closest?.('[data-yd-action]')
    if (action) {
      event.preventDefault()
      const name = action.getAttribute('data-yd-action')
      if (name === 'toggle-music') {
        toggleMusic()
      }
      else if (name === 'toggle-sound') {
        toggleEffects()
      }
      else if (name === 'copy') {
        const value = action.getAttribute('title') || ''
        if (value) {
          void navigator.clipboard?.writeText(value).then(() => {
            action.setAttribute('data-yd-copied', 'true')
            window.setTimeout(() => action.removeAttribute('data-yd-copied'), 1500)
          }, () => undefined)
        }
      }
      playEffect('button')
      syncActions()
      return
    }
    const control = target?.closest?.('a,button')
    if (control) {
      playEffect(control.tagName === 'BUTTON' ? 'button' : 'click')
    }
  }
  doc.addEventListener('click', onClick, true)

  const stopParticles = startParticles(frameWindow, host, page.settings?.particles ?? document.features?.particles ?? 'none')
  const stopToast = showPageToast(document, page, host)
  bindSiteInfo(host)
  bindSources(host, () => alive, document, markCurrent)

  return () => {
    alive = false
    doc.removeEventListener('click', onClick, true)
    stopParticles()
    stopToast()
  }
}
