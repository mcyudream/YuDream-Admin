import type { SiteDocument, StudioCommand, StudioContentEffect, StudioContentField, StudioContentSection, StudioPageSettings, StudioParticlePreset } from '../types/site-studio'
import { cloneStudioJson } from './site-studio-validation'

export const STUDIO_PARTICLE_LABELS: Record<StudioParticlePreset, string> = {
  none: '无',
  dust: '金色微光',
  snow: '飘雪',
  petals: '花瓣',
  spores: '幽光孢子',
  embers: '余烬',
  stars: '星屑',
}

export const STUDIO_EFFECT_LABELS: Record<StudioContentEffect, string> = {
  particles: '站点默认粒子',
  music: '背景音乐',
  musicVolume: '音乐音量',
  effectsDefaultOn: '默认开启音效',
  effectVolume: '音效音量',
  clickSound: '链接点击音效',
  buttonSound: '按钮点击音效',
  toastSound: '进入提示音效',
  pageEffects: '页面粒子与进入提示',
}

const EFFECT_KEYS: Record<StudioContentEffect, string> = {
  particles: 'siteParticles',
  music: 'siteMusic',
  musicVolume: 'siteMusicVolume',
  effectsDefaultOn: 'siteEffectsOn',
  effectVolume: 'siteEffectVolume',
  clickSound: 'siteClickSound',
  buttonSound: 'siteButtonSound',
  toastSound: 'siteToastSound',
  pageEffects: 'pageEffects',
}

/** 主题作者可视化添加功能字段时使用；效果逻辑只在平台运行时实现。 */
export function studioEffectField(effect: StudioContentEffect, key = EFFECT_KEYS[effect]): StudioContentField {
  switch (effect) {
    case 'particles': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'particles', effect, defaultValue: 'none' }
    case 'music': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'list', effect, defaultValue: [], itemFields: [
      { key: 'title', label: '曲名', type: 'text' },
      { key: 'src', label: '音频', type: 'audio' },
    ] }
    case 'musicVolume': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'number', effect, defaultValue: 18 }
    case 'effectsDefaultOn': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'switch', effect, defaultValue: true }
    case 'effectVolume': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'number', effect, defaultValue: 30 }
    case 'clickSound':
    case 'buttonSound':
    case 'toastSound': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'audio', effect, defaultValue: '' }
    case 'pageEffects': return { key, label: STUDIO_EFFECT_LABELS[effect], type: 'list', effect, defaultValue: [], itemFields: [
      { key: 'path', label: '页面地址', type: 'text', placeholder: '/timeline' },
      { key: 'particles', label: '粒子', type: 'particles' },
      { key: 'toastTitle', label: '提示标题', type: 'text' },
      { key: 'toastBody', label: '提示内容', type: 'textarea' },
      { key: 'toastIcon', label: '提示图标', type: 'image' },
    ] }
  }
}

export function studioEffectSection(effect: StudioContentEffect): Pick<StudioContentSection, 'code' | 'title' | 'description'> {
  return effect === 'pageEffects'
    ? { code: 'page-effects', title: '页面氛围与进入提示', description: '按页面地址设置粒子与首次进入提示；站点页或宿主公开页均可配置。' }
    : { code: 'site-atmosphere', title: '站点氛围', description: '全站默认粒子、循环背景音乐与界面音效；由平台安全运行。' }
}

export function hasLegacyStudioEffects(doc: SiteDocument) {
  return Boolean(doc.features?.particles || doc.features?.audio || doc.pages.some(page => page.settings?.particles || page.settings?.toast))
}

export function declaredStudioEffects(doc: SiteDocument) {
  return (doc.features?.content?.sections ?? []).flatMap(section => section.fields.filter(field => field.effect).map(field => ({ section: section.title, field })))
}

/**
 * 把旧版站点设置移入内容模型。无法转换的内嵌资源原样留在旧字段，运行时逐项回落读取；
 * 所有操作以一个 batch 执行，既可撤销，也不会自动保存或发布。
 */
export function migrateStudioEffects(doc: SiteDocument): { commands: StudioCommand[], skipped: string[] } {
  const skipped: string[] = []
  const content = doc.features?.content
  const sections = cloneStudioJson(content?.sections ?? [])
  const used = new Set(sections.flatMap(section => section.fields.map(field => field.key)))
  const codes = new Set(sections.map(section => section.code))
  const declared = new Set(sections.flatMap(section => section.fields.map(field => field.effect).filter(Boolean)))
  const unique = (base: string, occupied: Set<string>) => {
    let candidate = base
    for (let index = 2; occupied.has(candidate); index++) {
      candidate = `${base}${index}`
    }
    occupied.add(candidate)
    return candidate
  }
  let changed = false
  const add = (effect: StudioContentEffect, value: unknown) => {
    if (declared.has(effect)) {
      return
    }
    const group = studioEffectSection(effect)
    let section = sections.find(item => item.code === group.code && item.title === group.title)
    if (!section) {
      section = { ...group, code: unique(group.code, codes), fields: [] }
      sections.push(section)
    }
    const field = studioEffectField(effect, unique(EFFECT_KEYS[effect], used))
    field.defaultValue = value
    section.fields.push(field)
    declared.add(effect)
    changed = true
  }
  const asset = (id: string | undefined, kind: 'audio' | 'image', what: string): string | null => {
    if (!id) {
      return ''
    }
    const found = doc.assets.find(item => item.id === id && item.kind === kind)
    if (!found) {
      return null
    }
    if (found.url.startsWith('data:')) {
      skipped.push(`${what}「${found.name}」为内嵌资源，请上传文件后再迁移`)
      return null
    }
    return found.url
  }
  const features = { ...doc.features }
  if (features.particles) {
    add('particles', features.particles)
    delete features.particles
    changed = true
  }
  const audio = features.audio
  if (audio) {
    const tracks = audio.tracks.map(track => ({ title: track.title, src: asset(track.assetId, 'audio', '背景音乐') }))
    if (tracks.every(track => track.src !== null)) {
      add('music', tracks)
    }
    add('musicVolume', audio.musicVolume)
    add('effectsDefaultOn', audio.effectsDefaultOn)
    add('effectVolume', audio.effectVolume)
    for (const [effect, id] of [
      ['clickSound', audio.clickSoundAssetId],
      ['buttonSound', audio.buttonSoundAssetId],
      ['toastSound', audio.toastSoundAssetId],
    ] as const) {
      const src = asset(id, 'audio', STUDIO_EFFECT_LABELS[effect])
      if (src !== null) {
        add(effect, src)
      }
    }
    if (['music', 'musicVolume', 'effectsDefaultOn', 'effectVolume', 'clickSound', 'buttonSound', 'toastSound'].every(effect => declared.has(effect as StudioContentEffect))) {
      delete features.audio
      changed = true
    }
  }

  const pageCommands: StudioCommand[] = []
  if (!declared.has('pageEffects')) {
    const rows: Record<string, string>[] = []
    for (const page of doc.pages) {
      const settings = page.settings
      if (!settings?.particles && !settings?.toast) {
        continue
      }
      const icon = asset(settings.toast?.iconAssetId, 'image', '进入提示图标')
      if (icon === null) {
        continue
      }
      const paths = settings.shell && settings.hostRoutes?.length ? settings.hostRoutes : [page.path]
      for (const path of paths) {
        rows.push({
          path,
          ...(settings.particles ? { particles: settings.particles } : {}),
          ...(settings.toast ? { toastTitle: settings.toast.title, toastBody: settings.toast.body, toastIcon: icon } : {}),
        })
      }
      const next: StudioPageSettings = { ...settings }
      delete next.particles
      delete next.toast
      pageCommands.push({ type: 'update-page', pageId: page.id, patch: { settings: next } })
    }
    if (rows.length) {
      add('pageEffects', rows)
    }
  }
  if (changed) {
    features.content = { sections, values: { ...content?.values } }
  }
  const commands: StudioCommand[] = []
  if (changed) {
    commands.push({ type: 'set-features', features })
  }
  commands.push(...pageCommands)
  return { commands, skipped }
}
