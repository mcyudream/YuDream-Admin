export interface StudioNode {
  id: string
  type: string
  tag: string
  text?: string
  attributes: Record<string, string>
  styles: Record<string, string>
  responsive?: { tablet?: Record<string, string>, mobile?: Record<string, string> }
  children: StudioNode[]
}

export type StudioParticlePreset = 'none' | 'snow' | 'petals' | 'embers' | 'spores' | 'stars' | 'dust'
export const STUDIO_PARTICLE_PRESETS: StudioParticlePreset[] = ['none', 'snow', 'petals', 'embers', 'spores', 'stars', 'dust']

/** 页面级站点功能：覆盖站点默认粒子；首次进入该页时弹出一次提示（同一访问内只弹一次）。 */
export interface StudioPageSettings {
  particles?: StudioParticlePreset
  toast?: { title: string, body: string, iconAssetId?: string }
}

export interface StudioPage {
  id: string
  name: string
  path: string
  root: StudioNode[]
  settings?: StudioPageSettings
}

/** 样式状态 → 编译选择器：hover=:hover、focus=:focus-visible、active=:active、current=[aria-current="page"]、pressed=[aria-pressed="true"] */
export type StudioStyleState = 'hover' | 'focus' | 'active' | 'current' | 'pressed'
export const STUDIO_STYLE_STATES: StudioStyleState[] = ['hover', 'focus', 'active', 'current', 'pressed']

/** 共享样式类：节点经 class 属性引用，编译为文档作用域内的 .name 规则（先于节点样式输出，节点样式可覆盖）。 */
export interface StudioStyleClass {
  name: string
  styles: Record<string, string>
  responsive?: { tablet?: Record<string, string>, mobile?: Record<string, string> }
  states?: Partial<Record<StudioStyleState, Record<string, string>>>
}

/** 字体：引用 kind=font 的资产，编译为 @font-face。 */
export interface StudioFontFace {
  family: string
  assetId: string
  weight?: string
  style?: 'normal' | 'italic'
}

/** 关键帧动画：帧键为 from / to / 0%–100%，帧内为受校验的 CSS 声明。 */
export interface StudioKeyframes {
  name: string
  frames: Record<string, Record<string, string>>
}

/**
 * 站点功能：由宿主运行时实现的受控行为（文档本身不含脚本）。
 * audio：背景音乐曲目与点击音效，访客经 data-yd-action="toggle-music|toggle-sound" 按钮开关；
 * particles：站点默认氛围粒子，页面可覆盖。
 */
export interface StudioFeatures {
  audio?: {
    tracks: { title: string, assetId: string }[]
    musicVolume: number
    effectVolume: number
    effectsDefaultOn: boolean
    clickSoundAssetId?: string
    buttonSoundAssetId?: string
    toastSoundAssetId?: string
  }
  particles?: StudioParticlePreset
}

export interface StudioAsset {
  id: string
  name: string
  url: string
  mimeType: string
  kind: 'image' | 'video' | 'audio' | 'font' | 'file'
}

export interface SiteDocument {
  schemaVersion: 1
  id: string
  name: string
  theme: {
    tokens: Record<string, string>
    header: StudioNode[]
    footer: StudioNode[]
    classes?: StudioStyleClass[]
    fonts?: StudioFontFace[]
    keyframes?: StudioKeyframes[]
  }
  pages: StudioPage[]
  assets: StudioAsset[]
  features?: StudioFeatures
}

/** A collection is a virtual root: it cannot itself be removed or moved. */
export type StudioContainer = { pageId: string } | { region: 'header' | 'footer' } | { parentId: string }
export type StudioCommand
  = | { type: 'batch', commands: StudioCommand[] }
    | { type: 'set-text', nodeId: string, text: string }
    | { type: 'update-node', nodeId: string, patch: Partial<Pick<StudioNode, 'type' | 'tag' | 'text' | 'attributes' | 'styles' | 'responsive' | 'children'>> }
    | { type: 'set-style', nodeId: string, styles: Record<string, string | null>, breakpoint?: 'desktop' | 'tablet' | 'mobile' }
    | { type: 'insert', target: StudioContainer, node: StudioNode, index?: number }
    | { type: 'remove', nodeId: string }
    | { type: 'move', nodeId: string, target: StudioContainer, index?: number }
    | { type: 'duplicate', nodeId: string }
    | { type: 'set-theme', tokens?: Record<string, string>, header?: StudioNode[], footer?: StudioNode[], classes?: StudioStyleClass[], fonts?: StudioFontFace[], keyframes?: StudioKeyframes[] }
    | { type: 'set-features', features: StudioFeatures }
    | { type: 'add-page', page: StudioPage }
    | { type: 'update-page', pageId: string, patch: Partial<Pick<StudioPage, 'name' | 'path' | 'root' | 'settings'>> }
    | { type: 'remove-page', pageId: string }
    | { type: 'set-assets', assets: StudioAsset[] }

export interface StudioCompileOptions {
  includeChrome?: boolean
  /** 是否输出主题级 CSS（字体/关键帧/样式类），默认输出；编辑画布另行注入 */
  themeCss?: boolean
}

export interface StudioPackage {
  type: 'yudream-site-package'
  version: 1
  kind: 'theme' | 'site'
  document: SiteDocument
  assets: { id: string, mode: 'embedded' | 'reference', url: string, mimeType: string }[]
}

/** Structural model only. The editor lifecycle belongs exclusively to YdPageBuilder. */
export interface StudioGrapesModel {
  type?: string
  tagName?: string
  content?: string
  attributes?: Record<string, string>
  style?: Record<string, string>
  components?: StudioGrapesModel[]
}
