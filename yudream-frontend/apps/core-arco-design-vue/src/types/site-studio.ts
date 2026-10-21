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
  /** 页面图标（图片资产）：站点导航数据中该页条目的 icon 字段 */
  iconAssetId?: string
  /**
   * 承载的宿主公开页路由前缀（如 /wiki、/market、插件公开页 /timeline）：访问这些路由时，宿主页面内容嵌入本页
   * data-yd-outlet 节点位置渲染（Shadow DOM 插槽），页面中 data-yd-host-hide 的节点在承载时隐藏。
   */
  hostRoutes?: string[]
}

export interface StudioPage {
  id: string
  name: string
  path: string
  root: StudioNode[]
  settings?: StudioPageSettings
}

/**
 * 样式状态 → 编译选择器：hover=:hover、focus=:focus-visible、active=:active、current=[aria-current="page"]、pressed=[aria-pressed="true"]；
 * 父级状态作用于“直接父元素处于该状态时的本元素”，用于二级菜单等无脚本展开：
 * parent-hover=:hover>.名称、parent-focus=:focus-within>.名称、parent-expanded=[aria-expanded="true"]>.名称
 */
export type StudioStyleState = 'hover' | 'focus' | 'active' | 'current' | 'pressed' | 'parent-hover' | 'parent-focus' | 'parent-expanded'
export const STUDIO_STYLE_STATES: StudioStyleState[] = ['hover', 'focus', 'active', 'current', 'pressed', 'parent-hover', 'parent-focus', 'parent-expanded']

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
/** 站点导航条目：url 为站内路径（/site/...、/wiki、插件页等）或 http(s) 外链；最多两级 */
export interface StudioNavItem {
  id: string
  label: string
  url: string
  iconAssetId?: string
  children?: StudioNavItem[]
}

/**
 * 站点导航（随草稿/发布版本化）：数据源 site:navigation 以此为准；
 * appendAuto 为 true 时在其后按 URL 去重追加插件 siteNav 页面与插件市场/知识库入口。
 */
export interface StudioNavigation {
  items: StudioNavItem[]
  appendAuto: boolean
}

/**
 * 插件数据适配器：把插件的匿名公开接口（/api/plugins/{插件}/public/**，GET）映射为站点数据源 adapter:{id}。
 * endpoint 中的 {参数} 取自当前页面地址的查询参数（如详情页 ?id=）；recordsPath 指向结果里的列表
 * （single 为 true 时整个结果即一条记录）；fields 把源字段（点路径）改名为模板使用的字段。
 */
export interface StudioDataSource {
  id: string
  label: string
  endpoint: string
  recordsPath?: string
  single?: boolean
  fields?: Record<string, string>
}

/**
 * 公开页适配：宿主公开页（登录/注册、知识库、插件市场、插件公开页、旧版 CMS 页）按站点外观换肤。
 * 颜色为 #rrggbb，宿主换算为站点变量、组件库变量与 OKLCH 设计令牌；fontFamily 须为主题字体族；
 * chrome 为 true 时这些页面使用站点文档的页头/页脚（如底部物品栏）作为外壳。
 */
export interface StudioPublicSkin {
  chrome: boolean
  dark: boolean
  background: string
  surface: string
  text: string
  muted: string
  border: string
  primary: string
  primaryText: string
  fontFamily?: string
  backgroundImageAssetId?: string
  /** 外壳占用的底部高度（如固定物品栏），宿主页内容留出空间，如 120px */
  bottomOffset?: string
}

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
  navigation?: StudioNavigation
  dataSources?: StudioDataSource[]
  publicSkin?: StudioPublicSkin
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
