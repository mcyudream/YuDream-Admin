import type { SiteDocument, StudioPublicSkin, StudioSkinRole, StudioStyleClass } from '../types/site-studio'
import { compileStudioNodes, compileStudioScopedNodeCss, compileStudioThemeCss, STUDIO_BASE_CSS } from './site-studio-html'

/**
 * 公开页适配（宿主侧生成，值均已过文档校验）：
 * - 宿主公开页根（站点外壳、登录页、知识库、插件市场、插件公开页）覆写三套变量：
 *   站点变量 --yb-site-*（十六进制）、组件库中性变量 --color-*、设计令牌（OKLCH 三元组）；
 *   组件库编号主色 --primary-N 受宿主品牌令牌门禁约束，不在此覆写（主按钮走设计令牌 --primary）；
 * - 主题字体以 @font-face 注册到文档（Shadow DOM 内的 @font-face 不生效，外壳字体也依赖这里）；
 * - 页头/页脚区域单独编译，供 Shadow DOM 外壳渲染。
 */

export const STUDIO_PUBLIC_ROOTS = ['.site-chrome', '.site-page', '.login-page', '.wiki-public', '.wiki-home', '.wiki-search-page', '.discover', '.plugin', '.public-form-page', '.tl-page', '.ev-page', '.world-map-viewer', '.qb-screen', '.qb-shared', '.mc-wiki-public']

function rgb(hex: string): [number, number, number] {
  const value = Number.parseInt(hex.slice(1), 16)
  return [(value >> 16) & 255, (value >> 8) & 255, value & 255]
}

/** sRGB 十六进制 → OKLCH 三元组字符串（L C H），供 oklch(var(--x)) 形式的设计令牌 */
export function hexToOklchTriplet(hex: string) {
  const linear = rgb(hex).map((channel) => {
    const c = channel / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  const [r, g, b] = linear as [number, number, number]
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  const C = Math.sqrt(A * A + B * B)
  const H = C < 0.0001 ? 0 : ((Math.atan2(B, A) * 180) / Math.PI + 360) % 360
  return `${L.toFixed(4)} ${C.toFixed(4)} ${H.toFixed(2)}`
}

function skinVariables(skin: StudioPublicSkin): Record<string, string> {
  const ok = hexToOklchTriplet
  return {
    // 站点外壳与旧版 CMS 页
    '--yb-site-bg': skin.background,
    '--yb-site-text': skin.text,
    '--yb-site-heading': skin.text,
    '--yb-site-muted': skin.muted,
    '--yb-site-caption': skin.muted,
    '--yb-site-nav-text': skin.text,
    '--yb-site-text-2': skin.text,
    '--yb-site-text-3': skin.muted,
    '--yb-site-border': skin.border,
    '--yb-site-border-2': skin.border,
    '--yb-site-surface': skin.surface,
    '--yb-site-hover': skin.surface,
    '--yb-site-primary': skin.primary,
    '--yb-site-primary-text': skin.primaryText,
    '--yb-site-primary-btn-bg': skin.primary,
    '--yb-site-primary-btn-text': skin.primaryText,
    '--yb-site-hero-bg': `linear-gradient(135deg, ${skin.primary}, ${skin.background})`,
    '--yb-site-hero-text': skin.text,
    // 组件库（知识库、插件市场、插件公开页）
    '--color-bg-1': skin.surface,
    '--color-bg-2': skin.background,
    '--color-bg-3': skin.surface,
    '--color-bg-4': skin.surface,
    '--color-bg-5': skin.surface,
    '--color-bg-white': skin.surface,
    '--color-text-1': skin.text,
    '--color-text-2': skin.text,
    '--color-text-3': skin.muted,
    '--color-text-4': skin.muted,
    '--color-border-1': skin.border,
    '--color-border-2': skin.border,
    '--color-border-3': skin.border,
    '--color-fill-1': skin.surface,
    '--color-fill-2': skin.surface,
    '--color-fill-3': skin.surface,
    // YD 设计令牌（OKLCH）
    '--background': ok(skin.background),
    '--foreground': ok(skin.text),
    '--card': ok(skin.surface),
    '--card-foreground': ok(skin.text),
    '--popover': ok(skin.surface),
    '--popover-foreground': ok(skin.text),
    '--primary': ok(skin.primary),
    '--primary-foreground': ok(skin.primaryText),
    '--secondary': ok(skin.surface),
    '--secondary-foreground': ok(skin.text),
    '--muted': ok(skin.surface),
    '--muted-foreground': ok(skin.muted),
    '--accent': ok(skin.surface),
    '--accent-foreground': ok(skin.text),
    '--border': ok(skin.border),
    '--input': ok(skin.border),
    '--ring': ok(skin.primary),
  }
}

/** 公开页换肤 CSS；文档未启用公开页适配时返回空串 */
export function compileStudioPublicSkin(document: SiteDocument): string {
  const skin = document.features?.publicSkin
  const assets = new Map(document.assets.map(asset => [asset.id, asset]))
  const fonts = (document.theme.fonts ?? []).flatMap((font) => {
    const asset = assets.get(font.assetId)
    return asset ? [`@font-face{font-family:"${font.family}";src:url("${asset.url}");font-weight:${font.weight ?? 'normal'};font-style:${font.style ?? 'normal'};font-display:swap}`] : []
  })
  if (!skin) {
    return fonts.join('\n')
  }
  const roots = `html :is(${STUDIO_PUBLIC_ROOTS.join(',')})`
  const variables = Object.entries(skinVariables(skin)).map(([key, value]) => `${key}:${value} !important`).join(';')
  // 弹层（对话框、弹出框、下拉、选择器）挂在 body 上，不在页面根内：在嵌入主题页时同样换肤并自适应视口
  const overlays = 'html:has(.studio-shadow-layout) :is([role="dialog"],[data-slot="dialog-content"],[data-slot="popover-content"],[data-slot="select-content"],[data-slot="dropdown-menu-content"])'
  const image = skin.backgroundImageAssetId ? assets.get(skin.backgroundImageAssetId)?.url : ''
  const background = image
    ? `background:linear-gradient(rgba(0,0,0,.55),rgba(0,0,0,.55)),url("${image}") center/cover fixed,${skin.background} !important`
    : `background:${skin.background} !important`
  const font = skin.fontFamily ? `font-family:"${skin.fontFamily}",system-ui,"PingFang SC","Microsoft YaHei",sans-serif !important;` : ''
  return [
    ...fonts,
    `${roots}{${variables}}`,
    `${overlays}{${variables};color:${skin.text}}`,
    `html:has(.studio-shadow-layout) :is([role="dialog"],[data-slot="dialog-content"]){max-width:min(880px,calc(100vw - 24px)) !important;max-height:calc(100svh - 48px) !important;overflow:auto !important}`,
    skin.pixel ? `${overlays}{border-radius:0 !important;border:2px solid #000 !important;box-shadow:inset 0 0 0 2px rgba(255,255,255,.08),6px 6px 0 rgba(0,0,0,.5) !important}` : '',
    // 页面根：背景、文字与字体；登录页内表单控件同样继承字体
    `html :is(.site-chrome,.login-page,.wiki-public,.wiki-home,.wiki-search-page,.discover,.plugin,.public-form-page,.tl-page,.ev-page){${background};color:${skin.text} !important;${font}}`,
    skin.fontFamily ? `html .login-page :is(input,textarea,select,button,label,h1,h2,h3,h4,p,span,a){font-family:inherit !important}` : '',
    // 嵌入站点主题页（Shadow DOM 插槽）时宿主页面根透明，露出主题页背景
    `html .studio-shadow-layout .login-page{min-height:auto !important;background:transparent !important}`,
    `html .studio-shadow-layout :is(.site-chrome,.wiki-public,.wiki-home,.wiki-search-page,.discover,.plugin,.public-form-page,.tl-page,.ev-page){background:transparent !important}`,
    ...(skin.pixel ? pixelRules(skin) : []),
    ...themeClassRules(document, skin),
    skin.dark ? `html:has(:is(${STUDIO_PUBLIC_ROOTS.join(',')})){color-scheme:dark}` : '',
  ].filter(Boolean).join('\n')
}

/** 像素风：只作用于嵌入主题页（.studio-shadow-layout 插槽内）的宿主页面，用通用选择器而非插件专属类名 */
const SKIN_SCOPE = 'html .studio-shadow-layout'
// 只给最外层卡片加框：卡片内部的「-card-body / -card-title」等子元素不再各自成框
const SKIN_CARD = ':is([data-slot="card"],[class*="card"])'
const SKIN_SELECTORS: Record<StudioSkinRole, string> = {
  panel: `${SKIN_SCOPE} :is(${SKIN_CARD}:not(${SKIN_CARD} *),aside,.login-box)`,
  control: `${SKIN_SCOPE} :is([data-slot="input-group"],[data-slot="select-trigger"],[data-slot="popover-trigger"],[data-slot="form-control"],textarea,input:not([type="checkbox"]):not([type="radio"]))`,
  // 只匹配真正的按钮；YdAction 也常作整张卡片的点击区域，不套按钮外观
  button: `${SKIN_SCOPE} :is([data-slot="button"],[data-slot="tabs-trigger"])`,
  heading: `${SKIN_SCOPE} :is(h1,h2)`,
}

function pixelRules(skin: StudioPublicSkin) {
  const panels = SKIN_SELECTORS.panel
  const controls = SKIN_SELECTORS.control
  const buttons = SKIN_SELECTORS.button
  const scope = SKIN_SCOPE
  return [
    `${scope} *{border-radius:0 !important}`,
    `${panels}{background:${skin.surface}e6 !important;border:2px solid #000 !important;box-shadow:inset 0 0 0 2px rgba(255,255,255,.08),4px 4px 0 rgba(0,0,0,.45) !important}`,
    `${controls}{background:rgba(0,0,0,.45) !important;border:2px solid #000 !important;box-shadow:inset 2px 2px 0 rgba(0,0,0,.5) !important}`,
    `${buttons}{border:2px solid #000 !important;box-shadow:inset 2px 2px 0 rgba(255,255,255,.28),inset -2px -2px 0 rgba(0,0,0,.35) !important}`,
    `${buttons}:active{box-shadow:inset -2px -2px 0 rgba(255,255,255,.2),inset 2px 2px 0 rgba(0,0,0,.4) !important}`,
  ]
}

/** 沿用主题样式类时只取视觉属性：不改宿主/插件页面的尺寸与布局 */
const VISUAL_PROPERTY = /^(?:background(?:-[a-z]+)*|border(?:-[a-z]+)*|box-shadow|color|text-shadow|image-rendering|letter-spacing|filter|outline(?:-[a-z]+)*|font-family|font-weight)$/
const TOKEN_REF = /var\(--([\w-]+)/g

function visualDeclarations(styles: Record<string, string> | undefined) {
  return Object.entries(styles ?? {}).filter(([property]) => VISUAL_PROPERTY.test(property)).map(([property, value]) => `${property}:${value} !important`).join(';')
}

/**
 * 公开页沿用主题样式类（publicSkin.classes）：嵌入主题页的宿主/插件页面中卡片、按钮、输入框、大标题套用对应主题样式类的
 * 视觉属性与 hover/active 状态；样式类引用的主题变量（如 --gs、--shadow）一并写到嵌入区域，不外泄到后台。
 */
function themeClassRules(document: SiteDocument, skin: StudioPublicSkin) {
  const mapping = skin.classes ?? {}
  const classes = new Map((document.theme.classes ?? []).map(item => [item.name, item] as const))
  const rules: string[] = []
  const tokens = new Set<string>()
  for (const [role, name] of Object.entries(mapping) as [StudioSkinRole, string][]) {
    const item: StudioStyleClass | undefined = classes.get(name)
    if (!item) {
      continue
    }
    const selector = SKIN_SELECTORS[role]
    const parts: [string, Record<string, string> | undefined][] = [['', item.styles], [':hover', item.states?.hover], [':active', item.states?.active], [':focus-visible', item.states?.focus]]
    for (const [state, styles] of parts) {
      const declarations = visualDeclarations(styles)
      if (declarations) {
        rules.push(`${state ? selector.replace(/\)$/, `)${state}`) : selector}{${declarations}}`)
        for (const match of declarations.matchAll(TOKEN_REF)) {
          tokens.add(match[1]!)
        }
      }
    }
  }
  const values = Object.entries(document.theme.tokens).filter(([key]) => tokens.has(key.replace(/^--/, ''))).map(([key, value]) => `${key.startsWith('--') ? key : `--${key}`}:${value}`)
  return values.length ? [`${SKIN_SCOPE}{${values.join(';')}}`, ...rules] : rules
}

/**
 * 编译单个主题区域（页头或页脚）供 Shadow DOM 外壳：文档作用域外层透明，节点样式与主题样式类照常作用。
 * 返回 html/css 与区域是否有内容。
 */
export function compileStudioRegion(document: SiteDocument, region: 'header' | 'footer') {
  const nodes = document.theme[region]
  const output = compileStudioNodes(nodes)
  const scope = `[data-studio-document="${document.id}"]`
  const tokens = Object.entries(document.theme.tokens).map(([key, value]) => `${key.startsWith('--') ? key : `--${key}`}:${value}`).join(';')
  const tag = region === 'header' ? 'header' : 'footer'
  return {
    empty: nodes.length === 0,
    html: `<div data-studio-document="${document.id}" data-studio-chrome-region><${tag} data-studio-region="${region}">${output.html}</${tag}></div>`,
    css: [
      STUDIO_BASE_CSS,
      `${scope}{${tokens};background:transparent !important}`,
      compileStudioThemeCss(document),
      compileStudioScopedNodeCss(document, nodes),
    ].join('\n'),
  }
}
