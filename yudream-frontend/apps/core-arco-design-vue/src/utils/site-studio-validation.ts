import type { SiteDocument, StudioAsset, StudioContentField, StudioNode } from '../types/site-studio'
import { parse } from 'postcss'
import { STUDIO_CONTENT_EFFECTS, STUDIO_CONTENT_FIELD_TYPES, STUDIO_PARTICLE_PRESETS, STUDIO_SKIN_ROLES, STUDIO_STYLE_STATES } from '../types/site-studio'

export const STUDIO_LIMITS = { bytes: 2 * 1024 * 1024, nodes: 6000, depth: 32, classes: 500, fonts: 32, keyframes: 64, tracks: 20 } as const
/** 样式类与关键帧名：小写字母开头，可含数字与连字符 */
export const STUDIO_CLASS_NAME = /^[a-z][a-z0-9-]{0,63}$/
const FONT_FAMILY = /^[a-z0-9][\w -]{0,63}$/i
const KEYFRAME_KEY = /^(?:from|to|(?:100|[1-9]?\d)(?:\.\d{1,2})?%)$/
export const STUDIO_BREAKPOINTS = { tablet: 992, mobile: 768 } as const
const safeTags = new Set('a abbr address article aside audio b bdi bdo blockquote br button caption cite code col colgroup dd del details dfn div dl dt em figcaption figure footer h1 h2 h3 h4 h5 h6 header hgroup hr i img ins kbd li main mark nav ol p picture pre q rp rt ruby s samp section small source span strong sub summary sup table tbody td th thead time tr track u ul var video wbr'.split(' '))
export const STUDIO_VOID_TAGS = new Set('br col hr img source track wbr'.split(' '))
const attributes = new Set('id class title lang dir role href target rel download src poster alt width height controls autoplay loop muted preload loading decoding crossorigin type media sizes datetime cite open colspan rowspan scope headers span start reversed value kind srclang label'.split(' '))
const dataMimes = new Set('image/png image/jpeg image/gif image/webp image/avif audio/mpeg audio/ogg audio/wav audio/webm video/mp4 video/webm video/ogg font/woff font/woff2 font/ttf font/otf'.split(' '))
const cssFunctions = new Set('url var calc min max clamp rgb rgba hsl hsla hwb lab lch oklab oklch color color-mix linear-gradient radial-gradient conic-gradient repeating-linear-gradient repeating-radial-gradient repeating-conic-gradient cubic-bezier steps translate translatex translatey translatez translate3d scale scalex scaley scalez scale3d rotate rotatex rotatey rotatez rotate3d skew skewx skewy matrix matrix3d perspective blur brightness contrast drop-shadow grayscale hue-rotate invert opacity saturate sepia fit-content repeat minmax counter counters attr env round mod rem abs sign pow sqrt hypot log exp sin cos tan asin acos atan atan2'.split(' '))
export function cloneStudioJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}
function hasControl(value: string, includeSpace = false) {
  return [...value].some(char => char.charCodeAt(0) < (includeSpace ? 33 : 32) || char.charCodeAt(0) === 127)
}
export function studioAssert(condition: unknown, message: string): asserts condition {
  if (!condition) {
    throw new Error(message)
  }
}
export function studioRecord(value: unknown): asserts value is Record<string, unknown> {
  studioAssert(value !== null && typeof value === 'object' && !Array.isArray(value), '必须是对象')
  studioAssert(Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null, '只接受普通 JSON 对象')
}
export function studioKeys(value: Record<string, unknown>, allowed: string[]) {
  for (const key of Object.keys(value)) {
    studioAssert(allowed.includes(key), `未知字段：${key}`)
  }
}
function string(value: unknown, name: string): asserts value is string {
  studioAssert(typeof value === 'string', `${name} 必须是字符串`)
}
export function validateStudioUrl(value: string, allowData = false) {
  string(value, 'URL')
  studioAssert(value.length > 0 && value === value.trim() && !hasControl(value, true) && !/[\\<>"`]/.test(value), 'URL 含非法字符')
  if (allowData && value.startsWith('data:')) {
    const match = /^data:([^;,]+);base64,([A-Za-z0-9+/]*={0,2})$/.exec(value)
    studioAssert(match && dataMimes.has(match[1]!) && match[2]!.length > 0 && match[2]!.length % 4 === 0, '不允许的媒体 data URL（禁止 SVG）')
    try {
      studioAssert(btoa(atob(match[2]!)) === match[2], 'base64 编码不规范')
    }
    catch {
      throw new Error('base64 编码不规范')
    }
    return
  }
  if (value.startsWith('#')) {
    return
  }
  if (value.startsWith('/') && !value.startsWith('//')) {
    studioAssert(!/%(?:0[0-9a-f]|1[0-9a-f]|5c|7f)/i.test(value), 'URL 含编码控制字符')
    return
  }
  studioAssert(/^https?:\/\//i.test(value), 'URL 仅允许 HTTP(S)、站内绝对路径或锚点')
  const url = new URL(value)
  studioAssert(!url.username && !url.password && Boolean(url.hostname), 'URL 禁止携带凭据')
}
export function validateStudioStyle(property: string, value: string) {
  studioAssert(/^(?:--[a-zA-Z][\w-]*|[a-z][a-z0-9-]*)$/.test(property) && !['behavior', '-moz-binding'].includes(property), '不安全的 CSS 属性')
  string(value, 'CSS 值')
  // Fail closed on CSS escapes/comments and declaration delimiters. PostCSS handles syntax;
  // this restricted value grammar deliberately does not claim to be a general CSS sanitizer.
  studioAssert(value.length <= STUDIO_LIMITS.bytes && !hasControl(value) && !/[\\{}<>@]/.test(value) && !value.includes('/*') && !value.includes('*/'), '不安全的 CSS 值')
  studioAssert(!/expression|javascript|vbscript|-moz-binding|behavior/i.test(value), '不安全的 CSS 表达式')
  const withoutUrls = value.replace(/url\( *(?:"([^"()]*)"|'([^'()]*)'|([^\s"'()]+)) *\)/gi, (_whole, double, single, plain) => {
    validateStudioUrl(double ?? single ?? plain, true)
    return 'none'
  })
  studioAssert(!/url\s*\(/i.test(withoutUrls) && !withoutUrls.includes(';'), '无效的 CSS URL 或声明分隔符')
  for (const match of withoutUrls.matchAll(/[a-z_-][\w-]*/gi)) {
    let cursor = (match.index ?? 0) + match[0].length
    while (withoutUrls[cursor] === ' ') {
      cursor++
    }
    if (withoutUrls[cursor] === '(') {
      studioAssert(cssFunctions.has(match[0].toLowerCase()) && match[0].toLowerCase() !== 'url', '不支持的 CSS 函数')
    }
  }
  const root = parse(`a{${property}:${value}}`)
  const rule = root.first
  studioAssert(root.nodes.length === 1 && rule?.type === 'rule' && rule.nodes.length === 1 && rule.first?.type === 'decl', 'CSS 值不是单个声明')
}
function styles(value: unknown) {
  studioRecord(value)
  for (const [key, item] of Object.entries(value)) {
    string(item, 'CSS 值')
    validateStudioStyle(key, item)
  }
}
export function validateStudioAttributes(value: unknown, nodeId: string) {
  studioRecord(value)
  for (const [key, item] of Object.entries(value)) {
    string(item, '属性值')
    studioAssert(key === key.toLowerCase() && !key.startsWith('on') && (attributes.has(key) || /^aria-[a-z-]+$/.test(key) || /^data-[a-z0-9-]+$/.test(key)), `不允许的 HTML 属性：${key}`)
    studioAssert(!['data-studio-document', 'data-studio-region', 'data-studio-type'].includes(key), '属性与编译器保留标识冲突')
    if (key === 'data-studio-id') {
      studioAssert(item === nodeId, '稳定 ID 属性与模型不一致')
    }
    if (['href', 'src', 'poster', 'cite'].includes(key)) {
      validateStudioUrl(item, key === 'src' || key === 'poster')
    }
    if (key === 'target') {
      studioAssert(['_blank', '_self'].includes(item), '不允许的链接 target')
    }
    if (key === 'crossorigin') {
      studioAssert(item === 'anonymous', '资源禁止携带跨域凭据')
    }
    studioAssert(!/\0/.test(item), '属性不能含空字符')
  }
}
export function validateStudioPath(value: unknown) {
  string(value, '页面路径')
  // 动态页面：段可为 :参数（如 /activities/:id），参数值经数据绑定传给插件数据适配器
  studioAssert(/^\/(?:(?:[\w-]+|:[a-z]\w{0,31})(?:\/(?:[\w-]+|:[a-z]\w{0,31}))*)?$/i.test(value), '页面路径必须是 / 、/about 或 /activities/:id 格式的站内绝对路径')
}
export function validateStudioDocument(value: unknown): asserts value is SiteDocument {
  studioRecord(value)
  studioKeys(value, ['schemaVersion', 'id', 'name', 'theme', 'pages', 'assets', 'features'])
  studioAssert(value.schemaVersion === 1, '不支持的文档 schemaVersion')
  let json: string
  try {
    json = JSON.stringify(value)
  }
  catch {
    throw new Error('文档不能包含环或非 JSON 数据')
  }
  studioAssert(new TextEncoder().encode(json).length <= STUDIO_LIMITS.bytes, '文档超过 2 MiB 限制')
  const ids = new Set<string>()
  const addId = (id: unknown) => {
    string(id, 'ID')
    studioAssert(/^[a-z0-9][\w-]{0,127}$/i.test(id), 'ID 必须是安全的非空字符串')
    studioAssert(!ids.has(id), `重复 ID：${id}`)
    ids.add(id)
  }
  addId(value.id)
  string(value.name, '站点名称')
  let count = 0
  const visit = (nodes: unknown, depth: number) => {
    studioAssert(Array.isArray(nodes), '节点集合必须为数组')
    studioAssert(depth <= STUDIO_LIMITS.depth, '节点深度超过 32')
    for (const node of nodes) {
      studioRecord(node)
      studioKeys(node, ['id', 'type', 'tag', 'text', 'attributes', 'styles', 'responsive', 'children'])
      studioAssert(++count <= STUDIO_LIMITS.nodes, `节点数超过 ${STUDIO_LIMITS.nodes}`)
      addId(node.id)
      string(node.type, '节点类型')
      studioAssert(node.type.trim().length > 0, '节点类型不能为空')
      string(node.tag, '标签')
      studioAssert(safeTags.has(node.tag), `不允许的 HTML 标签：${node.tag}`)
      validateStudioAttributes(node.attributes, node.id as string)
      styles(node.styles)
      studioAssert(Array.isArray(node.children), '节点 children 必须为数组')
      if (node.text !== undefined) {
        string(node.text, '节点文本')
        studioAssert(node.children.length === 0, 'text 与 children 不能同时存在；混合文本请拆成有序 span 子节点')
      }
      if (STUDIO_VOID_TAGS.has(node.tag)) {
        studioAssert(node.children.length === 0 && node.text === undefined, '空元素不能含文本或子节点')
      }
      if (node.responsive !== undefined) {
        studioRecord(node.responsive)
        studioKeys(node.responsive, ['tablet', 'mobile'])
        for (const item of Object.values(node.responsive)) {
          styles(item)
        }
      }
      if (node.children.length) {
        visit(node.children, depth + 1)
      }
    }
  }
  studioRecord(value.theme)
  studioKeys(value.theme, ['tokens', 'header', 'footer', 'classes', 'fonts', 'keyframes'])
  studioRecord(value.theme.tokens)
  for (const [key, item] of Object.entries(value.theme.tokens)) {
    studioAssert(/^(?:--)?[a-z][\w-]*$/i.test(key), '主题变量名无效')
    string(item, '主题变量值')
    validateStudioStyle(key.startsWith('--') ? key : `--${key}`, item)
  }
  const tokenNames = Object.keys(value.theme.tokens).map(key => key.replace(/^--/, ''))
  studioAssert(new Set(tokenNames).size === tokenNames.length, '主题变量名重复')
  visit(value.theme.header, 1)
  visit(value.theme.footer, 1)
  studioAssert(Array.isArray(value.pages) && value.pages.length > 0 && value.pages.length <= 200, '站点页面数量必须为 1 至 200')
  const paths = new Set<string>()
  for (const page of value.pages) {
    studioRecord(page)
    studioKeys(page, ['id', 'name', 'path', 'root', 'settings'])
    addId(page.id)
    string(page.name, '页面名称')
    validateStudioPath(page.path)
    studioAssert(!paths.has((page.path as string).toLowerCase()), '页面路径冲突')
    paths.add((page.path as string).toLowerCase())
    visit(page.root, 1)
  }
  studioAssert(Array.isArray(value.assets) && value.assets.length <= 500, '资产必须为不超过 500 项的数组')
  for (const asset of value.assets) {
    studioRecord(asset)
    studioKeys(asset, ['id', 'name', 'url', 'mimeType', 'kind'])
    addId(asset.id)
    string(asset.name, '资产名称')
    string(asset.url, '资产 URL')
    string(asset.mimeType, '资产 MIME')
    studioAssert(['image', 'video', 'audio', 'font', 'file'].includes(asset.kind as string), '无效资产种类')
    studioAssert(/^[a-z0-9.+-]+\/[a-z0-9.+-]+$/.test(asset.mimeType) && !/svg|html|javascript|xml/i.test(asset.mimeType), '不安全的资产 MIME')
    if (asset.kind !== 'file') {
      studioAssert(asset.mimeType.startsWith(`${asset.kind}/`), '资产 MIME 与种类不符')
    }
    validateStudioUrl(asset.url, asset.kind !== 'file')
    if (asset.url.startsWith('data:')) {
      studioAssert(asset.url.startsWith(`data:${asset.mimeType};base64,`), '资产 data MIME 不符')
    }
  }
  const assetKinds = new Map((value.assets as StudioAsset[]).map(asset => [asset.id, asset.kind]))
  const assetRef = (id: unknown, kind: StudioAsset['kind'], name: string) => {
    string(id, name)
    studioAssert(assetKinds.get(id) === kind, `${name}必须引用已有的${kind}资产`)
  }
  validateStudioThemeExtras(value.theme, assetRef)
  for (const page of value.pages as Record<string, unknown>[]) {
    if (page.settings !== undefined) {
      validatePageSettings(page.settings, assetRef)
    }
  }
  if (value.features !== undefined) {
    validateFeatures(value.features, assetRef, ((value.theme as SiteDocument['theme']).fonts ?? []).map(font => font.family), ((value.theme as SiteDocument['theme']).classes ?? []).map(item => item.name))
  }
}
type AssetRef = (id: unknown, kind: StudioAsset['kind'], name: string) => void
function boundedText(value: unknown, name: string, max: number, allowEmpty = false) {
  string(value, name)
  studioAssert((allowEmpty || value.trim().length > 0) && value.length <= max && !hasControl(value), `${name}须为 ${max} 字以内的文本`)
}
function stateStyles(value: unknown) {
  studioRecord(value)
  studioKeys(value, STUDIO_STYLE_STATES)
  Object.values(value).forEach(styles)
}
/** 主题扩展：共享样式类、字体、关键帧（均可缺省，旧文档不受影响）。 */
function validateStudioThemeExtras(theme: Record<string, unknown>, assetRef: AssetRef) {
  if (theme.classes !== undefined) {
    studioAssert(Array.isArray(theme.classes) && theme.classes.length <= STUDIO_LIMITS.classes, `样式类须为不超过 ${STUDIO_LIMITS.classes} 项的数组`)
    const names = new Set<string>()
    for (const item of theme.classes) {
      studioRecord(item)
      studioKeys(item, ['name', 'styles', 'responsive', 'states'])
      string(item.name, '样式类名')
      studioAssert(STUDIO_CLASS_NAME.test(item.name) && !names.has(item.name), '样式类名须为小写字母开头的唯一名称')
      names.add(item.name)
      styles(item.styles)
      if (item.responsive !== undefined) {
        studioRecord(item.responsive)
        studioKeys(item.responsive, ['tablet', 'mobile'])
        Object.values(item.responsive).forEach(styles)
      }
      if (item.states !== undefined) {
        stateStyles(item.states)
      }
    }
  }
  if (theme.fonts !== undefined) {
    studioAssert(Array.isArray(theme.fonts) && theme.fonts.length <= STUDIO_LIMITS.fonts, `字体须为不超过 ${STUDIO_LIMITS.fonts} 项的数组`)
    for (const item of theme.fonts) {
      studioRecord(item)
      studioKeys(item, ['family', 'assetId', 'weight', 'style'])
      string(item.family, '字体族名')
      studioAssert(FONT_FAMILY.test(item.family), '字体族名只能含字母、数字、空格、下划线与连字符')
      assetRef(item.assetId, 'font', '字体文件')
      if (item.weight !== undefined) {
        string(item.weight, '字重')
        studioAssert(/^(?:normal|bold|[1-9]00)$/.test(item.weight), '字重须为 normal、bold 或 100–900')
      }
      if (item.style !== undefined) {
        studioAssert(item.style === 'normal' || item.style === 'italic', '字形须为 normal 或 italic')
      }
    }
  }
  if (theme.keyframes !== undefined) {
    studioAssert(Array.isArray(theme.keyframes) && theme.keyframes.length <= STUDIO_LIMITS.keyframes, `关键帧动画须为不超过 ${STUDIO_LIMITS.keyframes} 项的数组`)
    const names = new Set<string>()
    for (const item of theme.keyframes) {
      studioRecord(item)
      studioKeys(item, ['name', 'frames'])
      string(item.name, '动画名')
      studioAssert(STUDIO_CLASS_NAME.test(item.name) && !names.has(item.name), '动画名须为小写字母开头的唯一名称')
      names.add(item.name)
      studioRecord(item.frames)
      const keys = Object.keys(item.frames)
      studioAssert(keys.length > 0 && keys.length <= 32, '关键帧数量须为 1 至 32')
      for (const key of keys) {
        studioAssert(KEYFRAME_KEY.test(key), '关键帧须为 from、to 或 0%–100%')
        styles(item.frames[key])
      }
    }
  }
}
function particles(value: unknown) {
  studioAssert(STUDIO_PARTICLE_PRESETS.includes(value as never), '无效的粒子效果')
}
function validatePageSettings(value: unknown, assetRef: AssetRef) {
  studioRecord(value)
  studioKeys(value, ['particles', 'toast', 'iconAssetId', 'hostRoutes', 'requires', 'shell'])
  if (value.shell !== undefined) {
    studioAssert(typeof value.shell === 'boolean', 'shell 须为布尔值')
    studioAssert(!value.shell || (Array.isArray(value.hostRoutes) && value.hostRoutes.length > 0), '承载外壳页须声明至少一个承载路由')
  }
  if (value.requires !== undefined) {
    validateRequires(value.requires)
  }
  if (value.hostRoutes !== undefined) {
    studioAssert(Array.isArray(value.hostRoutes) && value.hostRoutes.length <= 10, '承载路由最多 10 个')
    for (const route of value.hostRoutes) {
      string(route, '承载路由')
      studioAssert(/^\/[a-z0-9][\w/-]{0,63}$/i.test(route) && !/^\/site(?:\/|$)/i.test(route) && !route.includes('//'), '承载路由须为宿主公开页路径（如 /wiki），不能是 /site')
    }
  }
  if (value.particles !== undefined) {
    particles(value.particles)
  }
  if (value.iconAssetId !== undefined) {
    assetRef(value.iconAssetId, 'image', '页面图标')
  }
  if (value.toast !== undefined) {
    studioRecord(value.toast)
    studioKeys(value.toast, ['title', 'body', 'iconAssetId'])
    boundedText(value.toast.title, '提示标题', 80)
    boundedText(value.toast.body, '提示内容', 200, true)
    if (value.toast.iconAssetId !== undefined) {
      assetRef(value.toast.iconAssetId, 'image', '提示图标')
    }
  }
}
/** 插件公开接口路径：只允许 /api/plugins/{插件}/public/ 下的路径段与 {参数} 占位 */
export const STUDIO_ADAPTER_ENDPOINT = /^\/api\/plugins\/[a-z0-9][a-z0-9-]{0,63}\/public(?:\/(?:[\w.-]+|\{[a-z]\w{0,31}\}))+$/i
const FIELD_PATH = /^[a-z_]\w{0,31}(?:\.[a-z_]\w{0,31}){0,4}$/i
function validateDataSources(value: unknown) {
  studioAssert(Array.isArray(value) && value.length <= 20, '插件数据适配器最多 20 个')
  const ids = new Set<string>()
  for (const item of value) {
    studioRecord(item)
    studioKeys(item, ['id', 'label', 'endpoint', 'recordsPath', 'single', 'fields'])
    string(item.id, '适配器编码')
    studioAssert(/^[a-z][a-z0-9-]{0,31}$/.test(item.id) && !ids.has(item.id), '适配器编码须为小写字母开头的唯一名称')
    ids.add(item.id)
    boundedText(item.label, '适配器名称', 40)
    string(item.endpoint, '接口路径')
    studioAssert(STUDIO_ADAPTER_ENDPOINT.test(item.endpoint) && !item.endpoint.includes('..'), '接口路径须为插件公开接口 /api/plugins/{插件}/public/...')
    if (item.recordsPath !== undefined) {
      string(item.recordsPath, '列表路径')
      studioAssert(item.recordsPath === '' || FIELD_PATH.test(item.recordsPath), '列表路径须为点分字段名')
    }
    if (item.single !== undefined) {
      studioAssert(typeof item.single === 'boolean', 'single 须为布尔值')
    }
    if (item.fields !== undefined) {
      studioRecord(item.fields)
      const entries = Object.entries(item.fields)
      studioAssert(entries.length <= 30, '字段映射最多 30 项')
      for (const [target, source] of entries) {
        studioAssert(/^[a-z_]\w{0,31}$/i.test(target), '映射目标字段名无效')
        string(source, '映射源字段')
        studioAssert(FIELD_PATH.test(source), '映射源字段须为点分字段名')
      }
    }
  }
}
/** 依赖声明：plugin:插件编码 或 capability:wiki / capability:plugin-market */
export const STUDIO_REQUIREMENT = /^(?:plugin:[a-z0-9][a-z0-9-]{0,63}|capability:(?:wiki|plugin-market))$/
function validateRequires(value: unknown) {
  studioAssert(Array.isArray(value) && value.length <= 10, '依赖最多 10 项')
  for (const item of value) {
    studioAssert(typeof item === 'string' && STUDIO_REQUIREMENT.test(item), '依赖须为 plugin:插件编码 或 capability:wiki / capability:plugin-market')
  }
}
const SKIN_COLORS = ['background', 'surface', 'text', 'muted', 'border', 'primary', 'primaryText'] as const
function validatePublicSkin(value: unknown, assetRef: AssetRef, fontFamilies: string[], classNames: string[]) {
  studioRecord(value)
  studioKeys(value, ['chrome', 'dark', ...SKIN_COLORS, 'fontFamily', 'backgroundImageAssetId', 'bottomOffset', 'pixel', 'classes'])
  if (value.classes !== undefined) {
    studioRecord(value.classes)
    studioKeys(value.classes, STUDIO_SKIN_ROLES)
    for (const name of Object.values(value.classes)) {
      studioAssert(typeof name === 'string' && classNames.includes(name), '公开页沿用的样式类须为已有主题样式类')
    }
  }
  if (value.pixel !== undefined) {
    studioAssert(typeof value.pixel === 'boolean', 'pixel 须为布尔值')
  }
  studioAssert(typeof value.chrome === 'boolean' && typeof value.dark === 'boolean', 'chrome/dark 须为布尔值')
  for (const key of SKIN_COLORS) {
    string(value[key], '公开页颜色')
    studioAssert(/^#[0-9a-f]{6}$/i.test(value[key]), '公开页颜色须为 #rrggbb')
  }
  if (value.fontFamily !== undefined) {
    studioAssert(fontFamilies.includes(value.fontFamily as string), '公开页字体须为主题字体族')
  }
  if (value.backgroundImageAssetId !== undefined) {
    assetRef(value.backgroundImageAssetId, 'image', '公开页背景图')
  }
  if (value.bottomOffset !== undefined) {
    string(value.bottomOffset, '底部留白')
    studioAssert(/^(?:0|\d{1,3}(?:px|rem))$/.test(value.bottomOffset), '底部留白须为 0–999px 或 rem')
  }
}
function validateNavigation(value: unknown, assetRef: AssetRef) {
  studioRecord(value)
  studioKeys(value, ['items', 'appendAuto'])
  studioAssert(typeof value.appendAuto === 'boolean', 'appendAuto 须为布尔值')
  const ids = new Set<string>()
  const items = (list: unknown, depth: number) => {
    studioAssert(Array.isArray(list) && list.length <= 30, '每级导航最多 30 项')
    for (const item of list) {
      studioRecord(item)
      studioKeys(item, ['id', 'label', 'url', 'iconAssetId', 'requires', 'children'])
      if (item.requires !== undefined) {
        validateRequires(item.requires)
      }
      string(item.id, '导航 ID')
      studioAssert(/^[a-z0-9][\w-]{0,63}$/i.test(item.id) && !ids.has(item.id), '导航 ID 须唯一且安全')
      ids.add(item.id)
      boundedText(item.label, '导航名称', 40)
      string(item.url, '导航地址')
      validateStudioUrl(item.url)
      studioAssert(!item.url.startsWith('#'), '导航地址须为站内路径或 http(s) 链接')
      if (item.iconAssetId !== undefined) {
        assetRef(item.iconAssetId, 'image', '导航图标')
      }
      if (item.children !== undefined) {
        studioAssert(depth === 1, '导航最多两级')
        items(item.children, depth + 1)
      }
    }
  }
  items(value.items, 1)
}
/** 站点内容字段键：字母开头，可含字母数字下划线 */
export const STUDIO_CONTENT_KEY = /^[a-z]\w{0,47}$/i
export const STUDIO_CONTENT_LIMITS = { sections: 20, fields: 40, itemFields: 12, items: 50, text: 2000, textarea: 5000 } as const
/** 内容文本：允许换行与制表符，禁止其他控制字符 */
function contentText(value: unknown, name: string, max: number) {
  string(value, name)
  studioAssert(value.length <= max && ![...value].some(char => (char.charCodeAt(0) < 32 && char !== '\n' && char !== '\t') || char.charCodeAt(0) === 127), `${name}须为 ${max} 字以内的文本`)
}
/** 效果 → 字段类型；music / pageEffects 另约束条目字段 */
const EFFECT_TYPES: Record<string, string> = { particles: 'particles', music: 'list', musicVolume: 'number', effectsDefaultOn: 'switch', effectVolume: 'number', clickSound: 'audio', buttonSound: 'audio', toastSound: 'audio', pageEffects: 'list' }
const EFFECT_ITEMS: Record<string, { required: string[], types: Record<string, string[]> }> = {
  music: { required: ['src'], types: { src: ['audio'], title: ['text'] } },
  pageEffects: { required: ['path'], types: { path: ['text'], particles: ['particles'], toastTitle: ['text'], toastBody: ['text', 'textarea'], toastIcon: ['image'] } },
}
function contentEffect(value: Record<string, unknown>, effects: Set<string>) {
  const effect = value.effect as string
  studioAssert(STUDIO_CONTENT_EFFECTS.includes(effect as never) && !effects.has(effect), `站点功能字段无效或重复：${String(effect)}`)
  effects.add(effect)
  studioAssert(value.type === EFFECT_TYPES[effect], `站点功能 ${effect} 须为 ${EFFECT_TYPES[effect]} 字段`)
  const items = EFFECT_ITEMS[effect]
  if (items) {
    const fields = value.itemFields as { key: string, type: string }[]
    for (const key of items.required) {
      studioAssert(fields.some(field => field.key === key), `站点功能 ${effect} 的条目须包含 ${key} 字段`)
    }
    for (const field of fields) {
      studioAssert(items.types[field.key]?.includes(field.type), `站点功能 ${effect} 的条目字段 ${field.key} 不受支持或类型不符`)
    }
  }
}
function contentFieldSchema(value: unknown, keys: Set<string>, nested: boolean, effects = new Set<string>()) {
  studioRecord(value)
  studioKeys(value, ['key', 'label', 'type', 'description', 'placeholder', 'defaultValue', 'options', 'itemFields', 'effect'])
  string(value.key, '字段键')
  studioAssert(STUDIO_CONTENT_KEY.test(value.key) && !keys.has(value.key), `字段键须为字母开头的唯一名称：${value.key}`)
  keys.add(value.key)
  boundedText(value.label, '字段名称', 40)
  studioAssert(STUDIO_CONTENT_FIELD_TYPES.includes(value.type as never), `字段类型无效：${String(value.type)}`)
  if (value.description !== undefined) {
    boundedText(value.description, '字段说明', 200, true)
  }
  if (value.placeholder !== undefined) {
    boundedText(value.placeholder, '字段占位', 100, true)
  }
  if (value.type === 'select') {
    studioAssert(Array.isArray(value.options) && value.options.length > 0 && value.options.length <= 30, '下拉字段须有 1–30 个选项')
    for (const option of value.options) {
      studioRecord(option)
      studioKeys(option, ['label', 'value'])
      boundedText(option.label, '选项名称', 40)
      boundedText(option.value, '选项值', 64)
    }
  }
  else {
    studioAssert(value.options === undefined, '只有下拉字段可声明选项')
  }
  if (value.type === 'list') {
    studioAssert(!nested, '列表条目内不能再嵌套列表')
    studioAssert(Array.isArray(value.itemFields) && value.itemFields.length > 0 && value.itemFields.length <= STUDIO_CONTENT_LIMITS.itemFields, `列表须声明 1–${STUDIO_CONTENT_LIMITS.itemFields} 个条目字段`)
    const itemKeys = new Set<string>()
    value.itemFields.forEach(item => contentFieldSchema(item, itemKeys, true))
  }
  else {
    studioAssert(value.itemFields === undefined, '只有列表字段可声明条目字段')
  }
  if (value.effect !== undefined) {
    studioAssert(!nested, '只有顶层字段可驱动站点功能')
    contentEffect(value, effects)
  }
  if (value.defaultValue !== undefined) {
    validateContentValue(value as unknown as StudioContentField, value.defaultValue)
  }
}
/** 按字段类型校验内容值；空串表示未填写 */
export function validateContentValue(field: StudioContentField, value: unknown) {
  const name = field.label
  switch (field.type) {
    case 'number':
      studioAssert(typeof value === 'number' && Number.isFinite(value), `${name}须为数字`)
      if (field.effect === 'musicVolume' || field.effect === 'effectVolume') {
        studioAssert(Number.isInteger(value) && value >= 0 && value <= 100, `${name}须为 0–100 的整数`)
      }
      return
    case 'switch':
      studioAssert(typeof value === 'boolean', `${name}须为开关值`)
      return
    case 'list': {
      studioAssert(Array.isArray(value) && value.length <= STUDIO_CONTENT_LIMITS.items, `${name}最多 ${STUDIO_CONTENT_LIMITS.items} 条`)
      if (field.effect === 'music') {
        studioAssert(value.length <= STUDIO_LIMITS.tracks, `背景音乐最多 ${STUDIO_LIMITS.tracks} 首`)
      }
      const paths = new Set<string>()
      for (const item of value) {
        studioRecord(item)
        for (const [key, itemValue] of Object.entries(item)) {
          const itemField = field.itemFields?.find(candidate => candidate.key === key)
          studioAssert(itemField, `${name}含未声明的字段：${key}`)
          validateContentValue(itemField, itemValue)
        }
        if (field.effect === 'music') {
          studioAssert(typeof item.src === 'string' && item.src.length > 0, '背景音乐条目须填写音频')
        }
        if (field.effect === 'pageEffects') {
          studioAssert(typeof item.path === 'string' && /^\/(?:[\w-]+|:[a-z]\w*)(?:\/(?:[\w-]+|:[a-z]\w*))*$|^\/$/i.test(item.path), '页面氛围须填写不含查询参数的站内页面地址')
          const path = item.path.toLowerCase()
          studioAssert(!paths.has(path), `页面氛围地址重复：${item.path}`)
          paths.add(path)
        }
      }
      return
    }
    default:
      contentText(value, name, field.type === 'textarea' ? STUDIO_CONTENT_LIMITS.textarea : STUDIO_CONTENT_LIMITS.text)
      if (value === '') {
        return
      }
      if (field.type === 'color') {
        studioAssert(/^#[0-9a-f]{6}$/i.test(value as string), `${name}须为 #rrggbb`)
      }
      else if (field.type === 'image') {
        validateStudioUrl(value as string)
        studioAssert(!(value as string).startsWith('#'), `${name}须为图片地址`)
      }
      else if (field.type === 'link') {
        validateStudioUrl(value as string)
      }
      else if (field.type === 'audio') {
        validateStudioUrl(value as string)
        studioAssert(!(value as string).startsWith('#'), `${name}须为音频地址`)
      }
      else if (field.type === 'particles') {
        studioAssert(STUDIO_PARTICLE_PRESETS.includes(value as never), `${name}不是可用的粒子效果`)
      }
      else if (field.type === 'select') {
        studioAssert(field.options?.some(option => option.value === value), `${name}不是可选值`)
      }
  }
}
function validateContent(value: unknown) {
  studioRecord(value)
  studioKeys(value, ['sections', 'values'])
  studioAssert(Array.isArray(value.sections) && value.sections.length <= STUDIO_CONTENT_LIMITS.sections, `内容分区最多 ${STUDIO_CONTENT_LIMITS.sections} 个`)
  const codes = new Set<string>()
  const keys = new Set<string>()
  const fields = new Map<string, StudioContentField>()
  const effects = new Set<string>()
  for (const section of value.sections) {
    studioRecord(section)
    studioKeys(section, ['code', 'title', 'description', 'fields'])
    string(section.code, '分区编码')
    studioAssert(/^[a-z][a-z0-9-]{0,31}$/.test(section.code) && !codes.has(section.code), '分区编码须为小写字母开头的唯一名称')
    codes.add(section.code)
    boundedText(section.title, '分区标题', 40)
    if (section.description !== undefined) {
      boundedText(section.description, '分区说明', 200, true)
    }
    studioAssert(Array.isArray(section.fields) && section.fields.length <= STUDIO_CONTENT_LIMITS.fields, `每个分区最多 ${STUDIO_CONTENT_LIMITS.fields} 个字段`)
    for (const field of section.fields) {
      contentFieldSchema(field, keys, false, effects)
      fields.set((field as { key: string }).key, field as StudioContentField)
    }
  }
  studioRecord(value.values)
  for (const [key, fieldValue] of Object.entries(value.values)) {
    const field = fields.get(key)
    studioAssert(field, `内容值含未声明的字段：${key}`)
    validateContentValue(field, fieldValue)
  }
}
function volume(value: unknown, name: string) {
  studioAssert(Number.isInteger(value) && (value as number) >= 0 && (value as number) <= 100, `${name}须为 0–100 的整数`)
}
function validateFeatures(value: unknown, assetRef: AssetRef, fontFamilies: string[] = [], classNames: string[] = []) {
  studioRecord(value)
  studioKeys(value, ['audio', 'particles', 'navigation', 'dataSources', 'publicSkin', 'content'])
  if (value.content !== undefined) {
    validateContent(value.content)
  }
  if (value.publicSkin !== undefined) {
    validatePublicSkin(value.publicSkin, assetRef, fontFamilies, classNames)
  }
  if (value.dataSources !== undefined) {
    validateDataSources(value.dataSources)
  }
  if (value.particles !== undefined) {
    particles(value.particles)
  }
  if (value.navigation !== undefined) {
    validateNavigation(value.navigation, assetRef)
  }
  if (value.audio !== undefined) {
    const audio = value.audio
    studioRecord(audio)
    studioKeys(audio, ['tracks', 'musicVolume', 'effectVolume', 'effectsDefaultOn', 'clickSoundAssetId', 'buttonSoundAssetId', 'toastSoundAssetId'])
    studioAssert(Array.isArray(audio.tracks) && audio.tracks.length <= STUDIO_LIMITS.tracks, `背景音乐须为不超过 ${STUDIO_LIMITS.tracks} 首的数组`)
    for (const track of audio.tracks) {
      studioRecord(track)
      studioKeys(track, ['title', 'assetId'])
      boundedText(track.title, '曲名', 80)
      assetRef(track.assetId, 'audio', '曲目音频')
    }
    volume(audio.musicVolume, '音乐音量')
    volume(audio.effectVolume, '音效音量')
    studioAssert(typeof audio.effectsDefaultOn === 'boolean', 'effectsDefaultOn 须为布尔值')
    for (const key of ['clickSoundAssetId', 'buttonSoundAssetId', 'toastSoundAssetId'] as const) {
      if (audio[key] !== undefined) {
        assetRef(audio[key], 'audio', '音效')
      }
    }
  }
}
export function validateStudioNodes(nodes: StudioNode[]) {
  const document: SiteDocument = { schemaVersion: 1, id: 'validation-document', name: '', theme: { tokens: {}, header: [], footer: [] }, pages: [{ id: 'validation-page', name: '', path: '/', root: nodes }], assets: [] }
  validateStudioDocument(document)
}
export function isStudioEmbeddedAsset(asset: Pick<StudioAsset, 'url'>) {
  return asset.url.startsWith('data:')
}
