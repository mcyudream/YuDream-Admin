import type { SiteDocument, StudioAsset, StudioContentEffect, StudioPage, StudioParticlePreset } from '../types/site-studio'
import { reactive } from 'vue'
import apiCms from '@/api/modules/platform-cms'
import apiPublicTheme from '@/api/modules/public-theme'
import systemClient from '@/api/modules/system-client'
import { useAppAccountStore } from '@/store/modules/app/account'
import { useAppSettingsStore } from '@/store/modules/app/settings'
import { buildNavigationTree, loadSiteCapabilities, loadSiteNavigation, studioRequirementsMet } from '@/views/site/site-navigation'
import { STUDIO_PARTICLE_PRESETS } from '../types/site-studio'
import { toBackendAssetUrl } from './backend-url'
import { resolveStudioHref } from './site-studio-links'
import { STUDIO_ADAPTER_ENDPOINT, validateStudioUrl } from './site-studio-validation'

/**
 * 站点文档运行时：公开站与草稿预览的 iframe 不执行任何脚本，站点功能由宿主在同源文档上实现。
 * 文档只声明意图（features / page.settings / data-yd-* 属性），行为全部是宿主内置、可审计的代码：
 * - 背景音乐与点击音效（音频状态在宿主侧，跨页面保持；按钮 data-yd-action="toggle-music|toggle-sound"）
 * - 当前页链接 aria-current="page"（配合样式类 current 状态做导航高亮）
 * - 站点功能由站点内容中声明 effect 的字段驱动（resolveStudioEffects）：氛围粒子、背景音乐与音效、页面粒子与首次进入提示
 *   （同一会话每页一次）；未声明的效果回落旧版 features.particles / features.audio / 页面 settings
 * - 站点信息：data-yd-site="name|description|logo|copyright|year"（宿主「系统设置」中的站点名称、Logo 等，随设置变化，不写死在文档里）
 * - 账号：data-yd-auth="guest|user" 按登录状态显示；data-yd-account="name|avatar|initial"（头像缺省时显示首字）
 * - 菜单：[data-yd-menu] 内的 [data-yd-menu-trigger] 在触屏/按钮上切换 aria-expanded（二级菜单配合样式类 parent-* 状态展开），
 *   点外部或 Esc 收起；桌面悬停由 parent-hover 状态直接展开，链接点击照常跳转
 * - 插件数据适配器：features.dataSources 声明的插件公开接口作为数据源 adapter:{id}（仅 GET、不带凭据，{参数} 取页面查询参数）；
 *   条目链接可用 data-yd-link="/site/activity?id={id}" 由字段拼出（字段值逐个 URL 编码）
 * - 计数：data-yd-count="数据源" 显示该数据源的真实条数（如首页统计）；条目内 data-yd-show / data-yd-hide="字段" 按字段真假显隐
 * - 复制：按钮 data-yd-action="copy" 复制自身 title（可经 data-yd-title 绑定字段，如服务器地址）
 * - 站点内容（features.content，运营在「站点内容」页填写）：data-yd-content="字段" 写文本（img 写 src），
 *   data-yd-content-href 写链接（按宿主路由解析，站点页写 /site/...），data-yd-content-title 写提示（复制按钮复制该值），data-yd-content-bg 设背景图，
 *   data-yd-content-show="开关字段" 为 false 时隐藏；列表字段作为数据源 content:{字段}，条目字段照常经 data-yd-text/-src/-href 绑定
 * - 数据绑定：data-yd-source 容器内 data-yd-item 为条目模板，data-yd-text/-href/-src/-title 绑定字段（$index 为序号）；
 *   条目内 data-yd-each="children" 为嵌套列表（如二级菜单），无子项时隐藏并给条目写 data-yd-has-children；
 *   示例条目只在草稿预览（samples）中显示：data-yd-sample 元素仅在示例状态显示，data-yd-live 仅在真实数据时显示；
 *   公开站不显示示例——取数前条目隐藏，无数据（含接口不可用）时移除条目并显示 data-yd-empty 元素（空状态）；
 *   容器外的标记用 data-yd-for="数据源" 关联，如列表上方的“示例/实时”状态条或空状态提示）
 */

// ===== 音频（宿主侧单例） =====

type EffectName = 'click' | 'button' | 'toast'
const EFFECTS_KEY = 'yd.site.audio.effects'

export const studioAudio = reactive({ musicOn: false, effectsOn: true, hasMusic: false, hasEffects: false })
let audioUrls: { tracks: string[], effects: Partial<Record<EffectName, string>> } = { tracks: [], effects: {} }
let audioKey = ''
let music: HTMLAudioElement | null = null
let trackIndex = 0

// ===== 站点功能解析：站点内容中声明 effect 的字段优先，未声明的效果回落旧版 features / 页面设置 =====

interface StudioPageEffect { path: string, particles?: StudioParticlePreset, toast?: { title: string, body: string, icon: string } }
export interface StudioResolvedEffects {
  particles: StudioParticlePreset
  tracks: string[]
  musicVolume: number
  effectVolume: number
  effectsDefaultOn: boolean
  sounds: Partial<Record<EffectName, string>>
  pages: StudioPageEffect[]
}

function preset(value: unknown): StudioParticlePreset | undefined {
  return STUDIO_PARTICLE_PRESETS.includes(value as StudioParticlePreset) ? value as StudioParticlePreset : undefined
}

export function resolveStudioEffects(document: SiteDocument): StudioResolvedEffects {
  const fields = (document.features?.content?.sections ?? []).flatMap(section => section.fields)
  const effect = (name: StudioContentEffect) => {
    const field = fields.find(item => item.effect === name)
    return field ? { value: studioContentValue(document, field.key) } : undefined
  }
  const legacy = document.features?.audio
  const assetUrl = (id: string | undefined, kind: StudioAsset['kind']) => document.assets.find(item => item.id === id && item.kind === kind)?.url ?? ''
  const url = (value: unknown) => typeof value === 'string' && value && !value.startsWith('#') ? toBackendAssetUrl(value) : ''
  const sound = (name: StudioContentEffect, legacyId?: string) => {
    const bound = effect(name)
    return bound ? url(bound.value) : (legacyId ? toBackendAssetUrl(assetUrl(legacyId, 'audio')) : '')
  }
  const number = (name: StudioContentEffect, fallback: number) => {
    const bound = effect(name)
    return bound ? (typeof bound.value === 'number' ? bound.value : fallback) : fallback
  }
  const music = effect('music')
  const switchValue = effect('effectsDefaultOn')
  const pageList = effect('pageEffects')
  const legacyPages: StudioPageEffect[] = document.pages.flatMap(page => page.settings?.particles || page.settings?.toast
    ? [{ path: page.path, particles: page.settings.particles, toast: page.settings.toast ? { title: page.settings.toast.title, body: page.settings.toast.body, icon: assetUrl(page.settings.toast.iconAssetId, 'image') } : undefined }]
    : [])
  const configuredPages: StudioPageEffect[] = pageList
    ? asRecords(pageList.value).flatMap((item) => {
        const path = typeof item.path === 'string' ? item.path.trim() : ''
        if (!path.startsWith('/')) {
          return []
        }
        const title = typeof item.toastTitle === 'string' ? item.toastTitle.trim() : ''
        return [{ path, particles: preset(item.particles), toast: title ? { title, body: typeof item.toastBody === 'string' ? item.toastBody : '', icon: url(item.toastIcon) } : undefined }]
      })
    : []
  const configuredPaths = new Set(configuredPages.map(item => normalizePath(item.path)))
  return {
    particles: preset(effect('particles')?.value) ?? (effect('particles') ? 'none' : document.features?.particles ?? 'none'),
    tracks: music
      ? asRecords(music.value).map(item => url(item.src)).filter(Boolean)
      : (legacy?.tracks ?? []).map(track => toBackendAssetUrl(assetUrl(track.assetId, 'audio'))).filter(Boolean),
    musicVolume: number('musicVolume', legacy?.musicVolume ?? 18),
    effectVolume: number('effectVolume', legacy?.effectVolume ?? 30),
    effectsDefaultOn: switchValue ? switchValue.value !== false : legacy?.effectsDefaultOn !== false,
    sounds: { click: sound('clickSound', legacy?.clickSoundAssetId), button: sound('buttonSound', legacy?.buttonSoundAssetId), toast: sound('toastSound', legacy?.toastSoundAssetId) },
    pages: [...configuredPages, ...legacyPages.filter(item => !configuredPaths.has(normalizePath(item.path)))],
  }
}

/** 当前页面对应的页面效果：按文档页面路径或承载的宿主路由匹配 */
function pageEffectFor(effects: StudioResolvedEffects, candidates: string[]) {
  const wanted = new Set(candidates.map(normalizePath))
  return effects.pages.find(item => wanted.has(normalizePath(item.path)))
}

let audioEffects: StudioResolvedEffects | null = null

function configureAudio(document: SiteDocument) {
  const effects = resolveStudioEffects(document)
  const key = JSON.stringify([document.id, effects.tracks, effects.sounds, effects.musicVolume, effects.effectVolume, effects.effectsDefaultOn])
  if (key === audioKey) {
    return
  }
  audioKey = key
  audioEffects = effects
  audioUrls = { tracks: effects.tracks, effects: effects.sounds }
  studioAudio.hasMusic = audioUrls.tracks.length > 0
  studioAudio.hasEffects = Object.values(audioUrls.effects).some(Boolean)
  const stored = localStorage.getItem(EFFECTS_KEY)
  studioAudio.effectsOn = stored === null ? effects.effectsDefaultOn : stored === '1'
  if (music) {
    music.volume = effects.musicVolume / 100
  }
  if (!studioAudio.hasMusic) {
    stopStudioAudio()
  }
}

function playEffect(name: EffectName) {
  const src = audioUrls.effects[name] || (name === 'button' ? audioUrls.effects.click : '')
  if (!studioAudio.effectsOn || !src || !audioEffects) {
    return
  }
  const sound = new Audio(src)
  sound.volume = audioEffects.effectVolume / 100
  void sound.play().catch(() => undefined)
}

function playTrack() {
  if (!audioUrls.tracks.length) {
    return
  }
  music ??= new Audio()
  music.src = audioUrls.tracks[trackIndex % audioUrls.tracks.length]!
  music.volume = (audioEffects?.musicVolume ?? 0) / 100
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

function showPageToast(document: SiteDocument, effect: StudioPageEffect | undefined, host: Element) {
  const toast = effect?.toast
  if (!toast) {
    return () => undefined
  }
  const seenKey = `${document.id}:${normalizePath(effect.path)}`
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
  const doc = host.ownerDocument
  const layer = host.querySelector(':scope > .yd-site-toast-layer') ?? host.appendChild(Object.assign(doc.createElement('div'), { className: 'yd-site-toast-layer' }))
  layer.setAttribute('role', 'status')
  layer.setAttribute('aria-live', 'polite')
  const item = doc.createElement('div')
  item.className = 'yd-site-toast'
  if (toast.icon) {
    const img = doc.createElement('img')
    img.className = 'yd-site-toast-icon'
    img.alt = ''
    img.src = toast.icon
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
    let current: string[] = []
    try {
      current = JSON.parse(sessionStorage.getItem(TOAST_KEY) || '[]') as string[]
    }
    catch {
      current = []
    }
    if (current.includes(seenKey)) {
      return
    }
    sessionStorage.setItem(TOAST_KEY, JSON.stringify([...current, seenKey].slice(-200)))
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
  const assetUrl = (id?: string) => document.assets.find(item => item.id === id && item.kind === 'image')?.url ?? ''
  const own = document.features?.navigation
  // 依赖未满足（插件未启用、能力关闭）的条目与指向已关闭页面的条目一律隐藏
  const available = await loadSiteCapabilities()
  // 承载外壳页自身地址不注册，同样视为关闭
  const closedPages = new Set(document.pages.filter(page => page.settings?.shell || !studioRequirementsMet(page.settings?.requires, available)).map(page => page.path === '/' ? '/site' : `/site${page.path}`))
  const open = (item: { url: string, requires?: string[] }) => studioRequirementsMet(item.requires, available) && !closedPages.has(item.url.replace(/\/+$/, '') || '/site')
  let tree: { label: string, url: string, icon?: string, children?: unknown[] }[]
  if (own) {
    // 站点文档自带导航（工作台「站点导航」）：为准；可选按 URL 去重追加插件页面与市场/知识库入口
    const flat = own.items.filter(open).flatMap((item, index) => [
      { id: item.id, label: item.label, url: item.url, icon: assetUrl(item.iconAssetId), visible: true, sort: index },
      ...(item.children ?? []).filter(open).map((child, childIndex) => ({ id: child.id, parentId: item.id, label: child.label, url: child.url, icon: assetUrl(child.iconAssetId), visible: true, sort: childIndex })),
    ])
    tree = own.appendAuto ? await loadSiteNavigation(JSON.stringify(flat)) : buildNavigationTree(flat)
    // 合并后保留条目自带图标
    const iconByUrl = new Map(flat.filter(item => item.icon).map(item => [item.url, item.icon]))
    const withIcons = (items: typeof tree): typeof tree => items.map(item => ({ ...item, icon: iconByUrl.get(item.url) ?? '', children: withIcons((item.children ?? []) as typeof tree) }))
    tree = withIcons(tree)
  }
  else {
    const navigationJson = await apiCms.publicHome({ skipGlobalToast: true }).then(res => res.data?.settings?.navigationJson as string | undefined, () => undefined)
    const pages = document.pages.filter(page => page.path !== '/' && !page.path.includes(':') && !page.settings?.shell && studioRequirementsMet(page.settings?.requires, available)).map((page, index) => ({ id: `studio-${page.id}`, label: page.name, url: `/site${page.path}`, visible: true, sort: 1000 + index }))
    tree = await loadSiteNavigation(navigationJson, pages)
  }
  // 指向站点文档页面的条目带上该页图标（页面设置 iconAssetId）
  const icons = new Map(document.pages.flatMap((page) => {
    const asset = document.assets.find(item => item.id === page.settings?.iconAssetId && item.kind === 'image')
    // 外壳页的图标同时用于它承载的宿主页面条目（如「活动平台」指向插件页 /activities）
    return asset ? [page.path === '/' ? '/site' : `/site${page.path}`, ...(page.settings?.hostRoutes ?? [])].map(url => [url, asset.url] as const) : []
  }))
  const toRecord = (item: { label: string, url: string, icon?: string, children?: unknown[] }): DataRecord => ({
    label: item.label,
    url: item.url,
    icon: item.icon || (icons.get(item.url.replace(/\/+$/, '') || '/site') ?? ''),
    external: /^https?:/i.test(item.url),
    children: (item.children ?? []).map(child => toRecord(child as typeof item)),
  })
  return tree.map(item => toRecord(item))
}

/** 读取匿名公开接口（不带凭据）；兼容 {code,data} 包装与裸 JSON，失败抛错由调用方降级为无数据 */
async function fetchPublicJson(path: string): Promise<unknown> {
  const response = await fetch(toBackendAssetUrl(path), { credentials: 'omit', headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  const body: unknown = await response.json()
  if (body && typeof body === 'object' && !Array.isArray(body) && 'code' in body) {
    const envelope = body as { code: unknown, data?: unknown }
    if (Number(envelope.code) !== 200) {
      throw new Error('接口返回错误')
    }
    return envelope.data
  }
  return body
}

/** 插件数据适配器：读取插件匿名公开接口并按声明映射为记录；缺参数、非 2xx 或业务错误时无数据 */
async function loadAdapter(document: SiteDocument, id: string, query: Record<string, string>): Promise<DataRecord[]> {
  const adapter = document.features?.dataSources?.find(item => item.id === id)
  if (!adapter || !STUDIO_ADAPTER_ENDPOINT.test(adapter.endpoint)) {
    return []
  }
  let missing = false
  const path = adapter.endpoint.replace(/\{(\w+)\}/g, (_whole, name: string) => {
    const value = query[name]
    if (!value) {
      missing = true
    }
    return encodeURIComponent(value ?? '')
  })
  if (missing) {
    return []
  }
  const response = await fetch(toBackendAssetUrl(path), { credentials: 'omit', headers: { Accept: 'application/json' } })
  if (!response.ok) {
    return []
  }
  let body: unknown = await response.json()
  if (body && typeof body === 'object' && 'code' in body) {
    const envelope = body as { code: unknown, data?: unknown }
    if (Number(envelope.code) !== 200) {
      return []
    }
    body = envelope.data
  }
  const raw = adapter.single ? (body && typeof body === 'object' ? [body as DataRecord] : []) : asRecords(adapter.recordsPath ? field(body as DataRecord, adapter.recordsPath) : body)
  return raw.map(record => ({ ...record, ...Object.fromEntries(Object.entries(adapter.fields ?? {}).map(([target, source]) => [target, field(record, source)])) }))
}

async function loadSource(source: string, limit: number, document: SiteDocument, query: Record<string, string> = {}): Promise<DataRecord[]> {
  const quiet = { skipGlobalToast: true }
  if (source.startsWith('adapter:')) {
    return (await loadAdapter(document, source.slice('adapter:'.length), query)).slice(0, limit)
  }
  if (source === 'site:navigation') {
    return loadNavigation(document)
  }
  if (source.startsWith('content:')) {
    return asRecords(studioContentValue(document, source.slice('content:'.length))).slice(0, limit)
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
    case 'market:categories': {
      // 市场 v2 协议返回裸 JSON（无 {code,data} 包装），systemClient 会按业务错误拒绝，改用原生 fetch
      const res = await fetchPublicJson('/api/public/plugin-market/api/v2/categories')
      return asRecords(res).map(item => ({ ...item, url: `/market?category=${encodeURIComponent(String(item.code ?? item.name ?? ''))}` }))
    }
    case 'market:plugins': {
      const res = await fetchPublicJson(`/api/public/plugin-market/api/v2/plugins?page=1&size=${limit}`)
      return asRecords(res).map(item => ({ ...item, name: item.displayName || item.name || item.code, url: item.code ? `/market/${encodeURIComponent(String(item.code))}` : '' }))
    }
    default:
      return []
  }
}

export { STUDIO_DATA_SOURCES } from './site-studio-sources'

function fetchSource(source: string, limit: number, document: SiteDocument, query: Record<string, string>) {
  // 站点内容随文档变化（预览即时生效），不走接口缓存
  if (source.startsWith('content:')) {
    return loadSource(source, limit, document, query).catch(() => [] as DataRecord[])
  }
  const key = `${document.id}|${source}|${limit}|${source.startsWith('adapter:') ? JSON.stringify(query) : ''}`
  const cached = dataCache.get(key)
  if (cached && Date.now() - cached.at < DATA_TTL) {
    return cached.promise
  }
  const promise = loadSource(source, limit, document, query).catch(() => [] as DataRecord[])
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

/** 元素归属的最近条目（含自身）：嵌套列表的条目由各自的 fillItem 处理 */
function ownerItem(element: Element) {
  return element.closest('[data-yd-item]')
}

/**
 * 用记录展开模板：容器内（不含更深层列表）的首个 data-yd-item 为模板，其余同标记项为示例，一并替换。
 * 返回是否展开（无模板时为 false）。
 */
function expandItems(container: Element, records: DataRecord[], limit: number) {
  const owner = container.hasAttribute('data-yd-each') ? '[data-yd-each],[data-yd-source]' : '[data-yd-source]'
  const items = [...container.querySelectorAll('[data-yd-item]')].filter(item => item.parentElement?.closest(owner) === container)
  const template = items[0]
  if (!template) {
    return false
  }
  const parent = template.parentElement!
  const anchor = template.nextSibling
  const fragment = container.ownerDocument.createDocumentFragment()
  records.slice(0, limit).forEach((record, index) => {
    const clone = template.cloneNode(true) as Element
    clone.removeAttribute('data-studio-id')
    clone.querySelectorAll('[data-studio-id]').forEach(element => element.removeAttribute('data-studio-id'))
    fillItem(clone, record, index)
    // 绑定数据的站内链接指向宿主路由（插件页、/market 等），不再映射到站点前缀
    for (const link of [clone, ...clone.querySelectorAll('a[data-yd-href]')]) {
      if (link.tagName === 'A' && link.hasAttribute('data-yd-href')) {
        link.setAttribute('data-yd-host-link', '')
      }
    }
    clone.removeAttribute('hidden')
    fragment.appendChild(clone)
  })
  parent.insertBefore(fragment, anchor)
  items.forEach(item => item.remove())
  return true
}

function fillItem(item: Element, record: DataRecord, index: number) {
  // 先展开本条目直属的嵌套列表（如二级菜单），无子项时隐藏容器
  const nested = [...item.querySelectorAll('[data-yd-each]')].filter(element => element.parentElement && ownerItem(element.parentElement) === item)
  for (const container of nested) {
    const children = asRecords(field(record, container.getAttribute('data-yd-each') || ''))
    const expanded = children.length > 0 && expandItems(container, children, 50)
    container.toggleAttribute('hidden', !expanded)
    item.setAttribute('data-yd-has-children', String(expanded))
  }
  const targets = [item, ...item.querySelectorAll('[data-yd-text],[data-yd-href],[data-yd-src],[data-yd-title],[data-yd-link],[data-yd-show],[data-yd-hide]')].filter(element => ownerItem(element) === item)
  const value = (path: string) => path === '$index' ? String(index + 1) : text(field(record, path))
  for (const element of targets) {
    const textPath = element.getAttribute('data-yd-text')
    if (textPath) {
      element.textContent = formatValue(value(textPath), element.getAttribute('data-yd-format'))
    }
    const hrefPath = element.getAttribute('data-yd-href')
    if (hrefPath) {
      const href = safeUrl(value(hrefPath), false)
      if (href) {
        element.setAttribute('href', href)
      }
      else {
        element.removeAttribute('href')
      }
    }
    for (const [attribute, invert] of [['data-yd-show', false], ['data-yd-hide', true]] as const) {
      const path = element.getAttribute(attribute)
      if (path) {
        const raw = field(record, path)
        const truthy = Boolean(raw) && raw !== 'false' && raw !== '0'
        element.toggleAttribute('hidden', invert ? truthy : !truthy)
      }
    }
    const linkTemplate = element.getAttribute('data-yd-link')
    if (linkTemplate) {
      const href = safeUrl(linkTemplate.replace(/\{([\w.]+)\}/g, (_whole, path: string) => encodeURIComponent(value(path))), false)
      if (href) {
        element.setAttribute('href', href)
      }
    }
    const titlePath = element.getAttribute('data-yd-title')
    if (titlePath) {
      element.setAttribute('title', value(titlePath))
    }
    const srcPath = element.getAttribute('data-yd-src')
    if (srcPath) {
      const src = safeUrl(value(srcPath), false)
      if (src) {
        element.setAttribute('src', src)
        element.removeAttribute('hidden')
      }
      else if (!element.getAttribute('src')) {
        // 无数据且作者未提供默认图时隐藏；有默认图（如导航默认图标）则保留
        element.setAttribute('hidden', '')
      }
    }
  }
}

/** 文本格式化：data-yd-format="datetime|date|time" 把时间戳（秒/毫秒）或 ISO 时间转为本地时间 */
function formatValue(raw: string, format: string | null) {
  if (!format || !raw) {
    return raw
  }
  const numeric = /^\d{9,13}$/.test(raw) ? Number(raw) * (raw.length <= 10 ? 1000 : 1) : Number.NaN
  const date = new Date(Number.isNaN(numeric) ? raw : numeric)
  if (Number.isNaN(date.getTime())) {
    return raw
  }
  if (format === 'date') {
    return date.toLocaleDateString('zh-CN')
  }
  if (format === 'time') {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

/** 账号绑定：登录状态显隐、账号名/头像/首字 */
function bindAccount(host: Element) {
  const account = useAppAccountStore()
  const loggedIn = account.isLogin
  host.querySelectorAll('[data-yd-auth]').forEach((element) => {
    const want = element.getAttribute('data-yd-auth')
    element.toggleAttribute('hidden', want === 'user' ? !loggedIn : want === 'guest' ? loggedIn : false)
  })
  const avatar = loggedIn ? safeUrl(account.avatar || '', false) : ''
  host.querySelectorAll('[data-yd-account]').forEach((element) => {
    const kind = element.getAttribute('data-yd-account')
    if (kind === 'name') {
      element.textContent = loggedIn ? account.account : element.textContent
    }
    else if (kind === 'avatar') {
      if (avatar) {
        element.setAttribute('src', avatar)
      }
      element.toggleAttribute('hidden', !avatar)
    }
    else if (kind === 'initial') {
      element.textContent = (account.account || '?').slice(0, 1).toUpperCase()
      element.toggleAttribute('hidden', Boolean(avatar))
    }
  })
}

/** 站点信息绑定：宿主公开设置（站点名称/简介/Logo/版权），无值时保留作者内容 */
function bindSiteInfo(host: Element, extra: Record<string, string> = {}) {
  const settings = useAppSettingsStore()
  const copyright = settings.settings.app.copyright
  const values: Record<string, string> = {
    name: settings.siteName,
    description: settings.siteDescription,
    logo: settings.logo,
    copyright: [copyright.dates ? `© ${copyright.dates}` : '', copyright.company].filter(Boolean).join(' '),
    year: String(new Date().getFullYear()),
    date: new Date().toLocaleDateString('zh-CN'),
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    ...extra,
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

/** 站点内容字段值：已填写的值优先，其次字段默认值；都没有时返回 undefined（保留作者内容） */
export function studioContentValue(document: SiteDocument, key: string): unknown {
  const content = document.features?.content
  if (!content) {
    return undefined
  }
  const value = content.values[key]
  if (value !== undefined) {
    return value
  }
  for (const section of content.sections) {
    const field = section.fields.find(item => item.key === key)
    if (field) {
      return field.defaultValue === '' ? undefined : field.defaultValue
    }
  }
  return undefined
}

/** 站点内容单值绑定：文本/图片、链接、背景图与开关显隐 */
function bindContent(host: Element, document: SiteDocument) {
  const scalar = (key: string) => {
    const value = studioContentValue(document, key)
    return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
  }
  for (const element of host.querySelectorAll('[data-yd-content]')) {
    const value = scalar(element.getAttribute('data-yd-content') || '')
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
      if (value.includes('\n')) {
        (element as HTMLElement).style.whiteSpace = 'pre-line'
      }
    }
  }
  for (const element of host.querySelectorAll('[data-yd-content-href]')) {
    const href = safeUrl(scalar(element.getAttribute('data-yd-content-href') || ''), true)
    if (href && element.tagName === 'A') {
      element.setAttribute('href', href)
      element.setAttribute('data-yd-host-link', '')
    }
  }
  for (const element of host.querySelectorAll('[data-yd-content-title]')) {
    const value = scalar(element.getAttribute('data-yd-content-title') || '')
    if (value) {
      element.setAttribute('title', value)
    }
  }
  for (const element of host.querySelectorAll('[data-yd-content-bg]')) {
    const src = safeUrl(scalar(element.getAttribute('data-yd-content-bg') || ''), false)
    if (src && !src.startsWith('#')) {
      (element as HTMLElement).style.backgroundImage = `url("${src}")`
    }
  }
  for (const element of host.querySelectorAll('[data-yd-content-show]')) {
    if (studioContentValue(document, element.getAttribute('data-yd-content-show') || '') === false) {
      element.setAttribute('hidden', '')
    }
  }
}

/** 计数绑定：data-yd-count 元素显示数据源真实条数（接口不可用时保留作者内容） */
function bindCounts(host: Element, isAlive: () => boolean, document: SiteDocument, query: Record<string, string>) {
  for (const element of host.querySelectorAll('[data-yd-count]')) {
    const source = element.getAttribute('data-yd-count') || ''
    void fetchSource(source, 50, document, query).then((records) => {
      if (isAlive()) {
        element.textContent = String(records.length)
      }
    })
  }
}

/** 容器直属的条目模板/示例条目（不含嵌套列表的条目） */
function ownItems(container: Element) {
  return [...container.querySelectorAll('[data-yd-item]')].filter(item => item.parentElement?.closest('[data-yd-each],[data-yd-source]') === container)
}

function bindSources(host: Element, isAlive: () => boolean, document: SiteDocument, onFilled: () => void, query: Record<string, string>, samples: boolean) {
  host.querySelectorAll('[data-yd-live]').forEach(element => element.setAttribute('hidden', ''))
  host.querySelectorAll('[data-yd-empty]').forEach(element => element.setAttribute('hidden', ''))
  const containers = [...host.querySelectorAll('[data-yd-source]')]
  for (const container of containers) {
    const source = container.getAttribute('data-yd-source') || ''
    const limit = Math.min(Math.max(Number.parseInt(container.getAttribute('data-yd-limit') || '12', 10) || 12, 1), 50)
    const linked = () => [...host.querySelectorAll('[data-yd-for]')].filter(element => element.getAttribute('data-yd-for') === source)
    // 公开站：取数前隐藏示例，避免假数据闪现
    if (!samples) {
      container.setAttribute('data-yd-state', 'loading')
      for (const element of [...ownItems(container), ...container.querySelectorAll('[data-yd-sample]'), ...linked().filter(element => element.hasAttribute('data-yd-sample'))]) {
        element.setAttribute('hidden', '')
      }
    }
    void fetchSource(source, limit, document, query).then((records) => {
      if (!isAlive()) {
        return
      }
      if (!records.length) {
        if (!samples) {
          ownItems(container).forEach(item => item.remove())
          for (const element of [...container.querySelectorAll('[data-yd-sample],[data-yd-empty]'), ...linked()]) {
            if (element.hasAttribute('data-yd-sample')) {
              element.remove()
            }
            else if (element.hasAttribute('data-yd-empty')) {
              element.removeAttribute('hidden')
            }
          }
          container.setAttribute('data-yd-state', 'empty')
        }
        return
      }
      if (!expandItems(container, records, limit)) {
        return
      }
      for (const element of [...container.querySelectorAll('[data-yd-sample],[data-yd-live]'), ...linked()]) {
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
export function attachStudioRuntime(options: {
  document: SiteDocument
  /** 站点文档页面；为空表示只挂载页头页脚外壳（宿主公开页），不启用粒子与进入提示 */
  page?: StudioPage
  frameWindow: Window
  basePath: string
  query?: Record<string, string>
  /** 挂载根：默认 frameWindow.document；宿主公开页外壳传 ShadowRoot */
  root?: Document | ShadowRoot
  /** 宿主当前路由路径：外壳模式下按它标记当前链接 */
  currentPath?: string
  /** 承载宿主公开页：隐藏 data-yd-host-hide 节点；否则隐藏 data-yd-outlet（普通站点页面不显示空插槽） */
  hostMode?: boolean
  /** 当前页面标题（宿主页面路由标题），供 data-yd-site="pageTitle" */
  pageTitle?: string
  /** 草稿预览：无数据时保留作者示例条目；公开站不传，示例不对访客显示 */
  samples?: boolean
}) {
  const { document, page, frameWindow, basePath } = options
  const doc = options.root ?? frameWindow.document
  const host = doc.querySelector('[data-studio-document]')
  if (!host) {
    return () => undefined
  }
  let alive = true
  configureAudio(document)

  // 当前页链接：所有链接按点击时同一规则解析为宿主路由后与当前路由比较（站点页、宿主公开页、外壳导航一致）
  const toRoute = (href: string, hostLink: boolean) => normalizePath(resolveStudioHref(href, basePath, hostLink))
  let current = normalizePath(options.currentPath ?? resolveStudioHref(page?.path ?? '/', basePath))
  const markCurrent = () => {
    for (const anchor of host.querySelectorAll('a[href]')) {
      const href = anchor.getAttribute('href') || ''
      const isCurrent = href.startsWith('/') && !href.startsWith('//') && toRoute(href, anchor.hasAttribute('data-yd-host-link')) === current
      anchor.toggleAttribute('aria-current', isCurrent)
      if (isCurrent) {
        anchor.setAttribute('aria-current', 'page')
      }
    }
    // 菜单触发项：当前页在其（可见）子菜单里，或当前路由位于其路径之下（如 /site/activities/:id 高亮「活动平台」，首页根路径除外）
    for (const menu of host.querySelectorAll('[data-yd-menu]')) {
      const trigger = menu.querySelector('[data-yd-menu-trigger]')
      if (trigger?.tagName !== 'A' || trigger.getAttribute('aria-current') === 'page') {
        continue
      }
      const href = trigger.getAttribute('href') || ''
      const base = href.startsWith('/') ? toRoute(href, trigger.hasAttribute('data-yd-host-link')) : ''
      const below = Boolean(base) && base !== '/site' && base !== '/' && current.startsWith(`${base}/`)
      if (below || menu.querySelector('[data-yd-submenu]:not([hidden]) a[aria-current="page"],[data-yd-each]:not([hidden]) a[aria-current="page"]')) {
        trigger.setAttribute('aria-current', 'page')
      }
    }
  }
  markCurrent()

  const closeMenus = (except?: Element) => {
    host.querySelectorAll('[data-yd-menu][aria-expanded="true"]').forEach((element) => {
      if (element !== except) {
        element.setAttribute('aria-expanded', 'false')
      }
    })
  }

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
    // 二级菜单：触屏或按钮触发时先展开，再次点击（链接）才跳转；桌面悬停已由 parent-hover 展开
    const trigger = target?.closest?.('[data-yd-menu-trigger]')
    const menu = trigger?.closest('[data-yd-menu]')
    const submenu = menu ? [...menu.querySelectorAll('[data-yd-submenu],[data-yd-each]')].find(element => !element.hasAttribute('hidden')) : undefined
    if (trigger && menu && submenu) {
      const expanded = menu.getAttribute('aria-expanded') === 'true'
      const coarse = frameWindow.matchMedia('(hover: none)').matches
      if (!expanded && (trigger.tagName === 'BUTTON' || coarse)) {
        event.preventDefault()
        closeMenus(menu)
        menu.setAttribute('aria-expanded', 'true')
        playEffect('button')
        return
      }
      if (expanded && trigger.tagName === 'BUTTON') {
        event.preventDefault()
        menu.setAttribute('aria-expanded', 'false')
        return
      }
    }
    if (!target?.closest?.('[data-yd-menu]')) {
      closeMenus()
    }
    const control = target?.closest?.('a,button')
    if (control) {
      playEffect(control.tagName === 'BUTTON' ? 'button' : 'click')
    }
  }
  doc.addEventListener('click', onClick as EventListener, true)
  const onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      closeMenus()
    }
  }
  doc.addEventListener('keydown', onKeydown as EventListener)

  // 站点功能（粒子、进入提示）：页面效果按文档页面路径或当前宿主路由匹配
  const effects = resolveStudioEffects(document)
  const routePath = options.currentPath ?? ''
  const routeCandidates = routePath ? [routePath, routePath.replace(/^\/site(?=\/|$)/, '') || '/'] : []
  const pageEffect = page ? pageEffectFor(effects, [...routeCandidates, page.path]) : undefined
  const stopParticles = page ? startParticles(frameWindow, host, pageEffect?.particles ?? effects.particles) : () => undefined
  const stopToast = page ? showPageToast(document, pageEffect, host) : () => undefined
  host.querySelectorAll(options.hostMode ? '[data-yd-host-hide]' : '[data-yd-outlet]').forEach(element => element.setAttribute('hidden', ''))
  bindSiteInfo(host, { pageTitle: options.pageTitle || page?.name || '' })
  bindAccount(host)
  bindSources(host, () => alive, document, markCurrent, options.query ?? {}, options.samples ?? false)
  bindContent(host, document)
  bindCounts(host, () => alive, document, options.query ?? {})

  // 经验条：data-yd-progress="scroll" 宽度跟随页面滚动；="visited" 显示本次访问去过的页面数
  const VISITED_KEY = 'yd.site.visited'
  const recordVisit = () => {
    let visited: string[] = []
    try {
      visited = JSON.parse(sessionStorage.getItem(VISITED_KEY) || '[]') as string[]
    }
    catch {
      visited = []
    }
    if (!visited.includes(current)) {
      visited = [...visited, current].slice(-100)
      sessionStorage.setItem(VISITED_KEY, JSON.stringify(visited))
    }
    return visited.length
  }
  const updateProgress = () => {
    const root = frameWindow.document.documentElement
    const max = root.scrollHeight - frameWindow.innerHeight
    const percent = max > 0 ? Math.min(100, Math.max(0, (frameWindow.scrollY / max) * 100)) : 0
    host.querySelectorAll<HTMLElement>('[data-yd-progress="scroll"]').forEach((element) => {
      element.style.width = `${percent.toFixed(1)}%`
    })
  }
  const updateVisited = () => {
    const count = String(recordVisit())
    host.querySelectorAll('[data-yd-progress="visited"]').forEach((element) => {
      element.textContent = count
    })
  }
  updateVisited()
  updateProgress()
  frameWindow.addEventListener('scroll', updateProgress, { passive: true })
  frameWindow.addEventListener('resize', updateProgress, { passive: true })

  const detach = () => {
    alive = false
    doc.removeEventListener('click', onClick as EventListener, true)
    doc.removeEventListener('keydown', onKeydown as EventListener)
    frameWindow.removeEventListener('scroll', updateProgress)
    frameWindow.removeEventListener('resize', updateProgress)
    stopParticles()
    stopToast()
  }
  /** 持久外壳在路由切换时只更新当前链接、访问数与经验条，不重建 DOM */
  return Object.assign(detach, {
    setCurrentPath(path: string) {
      current = normalizePath(path)
      closeMenus()
      // 登录/退出后跳转时刷新账号显示（外壳不重建）
      bindAccount(host)
      markCurrent()
      updateVisited()
      frameWindow.requestAnimationFrame(updateProgress)
    },
  })
}
