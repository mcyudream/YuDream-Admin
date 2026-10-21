import type { AtRule, Declaration, Rule } from 'postcss'
import type { StudioFontFace, StudioKeyframes, StudioNode, StudioStyleClass, StudioStyleState } from '../types/site-studio'
import { parse } from 'postcss'
import { STUDIO_BREAKPOINTS, STUDIO_CLASS_NAME, STUDIO_LIMITS, STUDIO_VOID_TAGS, validateStudioAttributes, validateStudioNodes, validateStudioStyle } from './site-studio-validation'

/**
 * 网页导入：把一份已渲染的网页（HTML + CSS + 资源）转换为站点文档的节点与主题样式。
 * 与画布解析（parseStudioHtml，逐节点内联样式）不同，这里保留网页的「类」结构：
 * - `.name` 简单类规则 → 同名主题样式类；
 * - 其余选择器（后代、属性、元素、id、页面作用域 body[data-page] 等）→ 生成样式类，按网页实际匹配结果挂到对应节点；
 * - 末尾的 :hover/:focus/:active、[aria-current]/[aria-pressed]（以及配置为“当前页”的类）→ 样式类状态；
 * - max-width 媒体查询 → 就近映射到平板/手机断点；@keyframes、@font-face、:root 变量 → 关键帧/字体/主题变量。
 * 样式类按原选择器特异度、再按源码顺序排列，尽量保留层叠结果。
 * 无法无损表达的部分（伪元素、祖先状态、min-width/偏好媒体查询、脚本、表单、未映射资源）跳过并计入报告。
 * 只依赖浏览器 DOMParser/element.matches；SVG 转图片需真实浏览器（Image + canvas）。
 */

export interface StudioWebImportRegions {
  /** 选择器：命中的元素按文档顺序成为该区域的顶层节点；未命中区域留空 */
  page: string
  header?: string
  footer?: string
}

export interface StudioWebImportOptions {
  html: string
  /** 样式表：path 用于解析其中的相对 url() */
  stylesheets: { path: string, text: string }[]
  regions: StudioWebImportRegions
  /** 站点内资源路径（以 / 开头、相对网页根）→ 文档资产 URL；返回空串表示未上传，相关声明/属性将被丢弃 */
  resolveAsset: (path: string) => string
  /** 表示“当前页”的类名（如 is-active）：选择器中的 .is-active 视为 [aria-current="page"] 状态，节点上的该类移除 */
  currentClasses?: string[]
  /** 导入前移除的元素选择器 */
  remove?: string
}

export interface StudioWebImportReport {
  rules: number
  classes: number
  generatedClasses: number
  skipped: Record<string, number>
  examples: string[]
  droppedElements: Record<string, number>
  unresolvedAssets: string[]
}

export interface StudioWebImportResult {
  page: StudioNode[]
  header: StudioNode[]
  footer: StudioNode[]
  classes: StudioStyleClass[]
  keyframes: StudioKeyframes[]
  fonts: { family: string, url: string, weight?: string, style?: 'normal' | 'italic' }[]
  tokens: Record<string, string>
  report: StudioWebImportReport
}

const STATE_SUFFIXES: [RegExp, StudioStyleState][] = [
  [/:hover$/, 'hover'],
  [/:focus-visible$/, 'focus'],
  [/:focus-within$/, 'focus'],
  [/:focus$/, 'focus'],
  [/:active$/, 'active'],
  [/\[aria-current=(?:"page"|'page'|page)\]$/, 'current'],
  [/\[aria-pressed=(?:"true"|'true'|true)\]$/, 'pressed'],
]
const DYNAMIC_PSEUDO = /:(?:hover|focus(?:-visible|-within)?|active|visited|target|checked|placeholder-shown)\b/
const PSEUDO_ELEMENT = /::?(?:before|after|selection|placeholder|marker|first-line|first-letter|backdrop|-webkit-[\w-]+|-moz-[\w-]+)\b/i
const ROOT_SELECTOR = /^(?:html|body|:root)(?:\[[^\]]*\]|\.[\w-]+)*$/i
const ALLOWED_TAGS = new Set('a abbr address article aside audio b bdi bdo blockquote br button caption cite code col colgroup dd del details dfn div dl dt em figcaption figure footer h1 h2 h3 h4 h5 h6 header hgroup hr i img ins kbd li main mark nav ol p picture pre q rp rt ruby s samp section small source span strong sub summary sup table tbody td th thead time tr track u ul var video wbr'.split(' '))
const DROP_SILENTLY = new Set(['script', 'noscript', 'style', 'link', 'meta', 'template'])
/** html/body 规则只把可继承属性带到各区域顶层节点；min-height、margin、overflow 等作用于根盒子的属性放到顶层节点上会改变布局（如撑满固定定位的顶栏） */
const INHERITED = /^(?:color|font(?:-[\w-]+)?|line-height|letter-spacing|word-spacing|text-(?:align|indent|transform|shadow|rendering)|white-space|word-break|overflow-wrap|direction|cursor|visibility|quotes|tab-size|-webkit-font-smoothing|-moz-osx-font-smoothing|-webkit-text-size-adjust|text-size-adjust|image-rendering|hyphens)$/

function hash(value: string) {
  let h = 2166136261
  for (let i = 0; i < value.length; i++) {
    h ^= value.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0).toString(36)
}

function specificity(selector: string): [number, number, number] {
  const withoutNot = selector.replace(/:(?:not|is|where|has)\(([^()]*)\)/g, ' $1')
  const ids = (withoutNot.match(/#[\w-]+/g) ?? []).length
  const classes = (withoutNot.match(/\.[\w-]+|\[[^\]]*\]|:[\w-]+/g) ?? []).length
  const types = (withoutNot.replace(/\[[^\]]*\]/g, '').replace(/[#.:][\w-]+/g, '').match(/(?:^|[\s>+~])[a-z][\w-]*/gi) ?? []).length
  return [ids, classes, types]
}

function resolvePath(base: string, url: string) {
  if (/^(?:https?:)?\/\//i.test(url) || url.startsWith('data:') || url.startsWith('#')) {
    return url
  }
  const parts = (url.startsWith('/') ? url : `${base.replace(/[^/]*$/, '')}${url}`).split('/')
  const out: string[] = []
  for (const part of parts) {
    if (part === '..') {
      out.pop()
    }
    else if (part !== '.' && part !== '') {
      out.push(part)
    }
  }
  return `/${out.join('/')}`.replace(/[?#].*$/, '')
}

function breakpointOf(params: string): 'tablet' | 'mobile' | null | 'skip' {
  const text = params.trim().toLowerCase()
  const match = /^(?:screen\s+and\s+)?\(\s*max-width\s*:\s*(\d+(?:\.\d+)?)px\s*\)$/.exec(text)
  if (!match) {
    return 'skip'
  }
  const width = Number(match[1])
  if (width >= 900) {
    return width > STUDIO_BREAKPOINTS.tablet + 200 ? 'skip' : 'tablet'
  }
  return width >= 480 ? 'mobile' : 'skip'
}

/** 把内联 SVG 栅格化为 PNG 图片（像素图标常用 SVG 绘制，而站点文档禁止 SVG）。需在浏览器中调用。 */
export async function rasterizeInlineSvg(html: string): Promise<string> {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const svgs = [...doc.querySelectorAll('svg')]
  for (const svg of svgs) {
    const width = Number(svg.getAttribute('width')) || Number(svg.viewBox?.baseVal?.width) || 16
    const height = Number(svg.getAttribute('height')) || Number(svg.viewBox?.baseVal?.height) || 16
    if (!svg.getAttribute('xmlns')) {
      svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
    }
    try {
      const source = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg.outerHTML)}`
      const image = new Image()
      await new Promise<void>((resolve, reject) => {
        image.onload = () => resolve()
        image.onerror = () => reject(new Error('svg'))
        image.src = source
      })
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(width))
      canvas.height = Math.max(1, Math.round(height))
      const ctx = canvas.getContext('2d')!
      ctx.imageSmoothingEnabled = false
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height)
      const img = doc.createElement('img')
      img.setAttribute('src', canvas.toDataURL('image/png'))
      img.setAttribute('width', String(canvas.width))
      img.setAttribute('height', String(canvas.height))
      img.setAttribute('alt', svg.getAttribute('aria-label') || '')
      const className = svg.getAttribute('class')
      if (className) {
        img.setAttribute('class', className)
      }
      svg.replaceWith(img)
    }
    catch {
      svg.remove()
    }
  }
  return `<!doctype html>${doc.documentElement.outerHTML}`
}

interface ClassEntry {
  item: StudioStyleClass
  specificity: [number, number, number]
  order: number
  generated: boolean
}

export function importStudioWebPage(options: StudioWebImportOptions): StudioWebImportResult {
  const report: StudioWebImportReport = { rules: 0, classes: 0, generatedClasses: 0, skipped: {}, examples: [], droppedElements: {}, unresolvedAssets: [] }
  const skip = (reason: string, example?: string) => {
    report.skipped[reason] = (report.skipped[reason] ?? 0) + 1
    if (example && report.examples.length < 40) {
      report.examples.push(`${reason}：${example.slice(0, 120)}`)
    }
  }
  const unresolved = new Set<string>()
  const doc = new DOMParser().parseFromString(options.html, 'text/html')
  if (options.remove) {
    doc.querySelectorAll(options.remove).forEach(element => element.remove())
  }
  const currentClasses = new Set(options.currentClasses ?? [])

  // ===== url() 映射 =====
  const mapUrl = (raw: string, base: string) => {
    const path = resolvePath(base, raw)
    if (path.startsWith('data:') || /^https?:\/\//i.test(path)) {
      return path
    }
    const mapped = options.resolveAsset(path)
    if (!mapped) {
      unresolved.add(path)
    }
    return mapped
  }
  const mapCssValue = (value: string, base: string) => {
    let missing = false
    const next = value.replace(/url\(\s*(['"]?)([^'")\s]+)\1\s*\)/gi, (_whole, _quote, url: string) => {
      const mapped = mapUrl(url.trim(), base)
      if (!mapped) {
        missing = true
        return 'none'
      }
      return `url("${mapped}")`
    })
    return missing ? null : next
  }

  // ===== 关键帧（名称规范化） =====
  const keyframeNames = new Map<string, string>()
  const keyframes: StudioKeyframes[] = []
  const normalizeName = (name: string) => {
    const kebab = name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '')
    return STUDIO_CLASS_NAME.test(kebab) ? kebab : `kf-${hash(name)}`
  }
  const renameAnimations = (prop: string, value: string) => {
    if (prop !== 'animation' && prop !== 'animation-name') {
      return value
    }
    return value.replace(/[a-z_][\w-]*/gi, word => keyframeNames.get(word) ?? word)
  }
  const styleFrom = (decls: Declaration[], base: string, where: string) => {
    const styles: Record<string, string> = {}
    for (const decl of decls) {
      const mapped = mapCssValue(`${decl.value}${decl.important ? ' !important' : ''}`, base)
      if (mapped === null) {
        skip('资源未映射的声明', `${where} ${decl.prop}`)
        continue
      }
      const value = renameAnimations(decl.prop, mapped)
      try {
        validateStudioStyle(decl.prop, value)
        styles[decl.prop] = value
      }
      catch {
        skip('不安全或不支持的声明', `${where} ${decl.prop}: ${decl.value}`)
      }
    }
    return styles
  }

  // ===== 第一遍：收集 @keyframes/@font-face 与 :root 变量 =====
  const fonts: StudioWebImportResult['fonts'] = []
  const tokens: Record<string, string> = {}
  const sheets = options.stylesheets.map(sheet => ({ path: sheet.path, root: parse(sheet.text.replace(/\\/g, '')) }))
  for (const sheet of sheets) {
    sheet.root.walkAtRules((at) => {
      const name = at.name.toLowerCase()
      if (name === 'keyframes' || name.endsWith('-keyframes')) {
        if (name !== 'keyframes') {
          skip('厂商前缀关键帧', at.params)
          return
        }
        keyframeNames.set(at.params.trim(), normalizeName(at.params.trim()))
      }
    })
  }
  for (const sheet of sheets) {
    sheet.root.walkAtRules((at) => {
      const name = at.name.toLowerCase()
      if (name === 'keyframes') {
        const frames: Record<string, Record<string, string>> = {}
        at.each((child) => {
          if (child.type === 'rule') {
            const styles = styleFrom(child.nodes.filter((node): node is Declaration => node.type === 'decl'), sheet.path, `@keyframes ${at.params}`)
            for (const key of child.selectors) {
              frames[key.trim()] = { ...frames[key.trim()], ...styles }
            }
          }
        })
        const finalName = keyframeNames.get(at.params.trim())!
        const existing = keyframes.findIndex(item => item.name === finalName)
        if (existing >= 0) {
          keyframes.splice(existing, 1)
        }
        if (Object.keys(frames).length && keyframes.length < STUDIO_LIMITS.keyframes) {
          keyframes.push({ name: finalName, frames })
        }
      }
      else if (name === 'font-face') {
        const decls = Object.fromEntries((at.nodes ?? []).filter((node): node is Declaration => node.type === 'decl').map(decl => [decl.prop.toLowerCase(), decl.value]))
        const family = (decls['font-family'] ?? '').trim().replace(/^(['"])(.*)\1$/, '$2')
        // 取首个可映射的 url（通常 woff2 在前）
        const urls = [...(decls.src ?? '').matchAll(/url\(\s*(['"]?)([^'")\s]+)\1\s*\)/g)].map(match => match[2]!)
        const url = urls.map(item => mapUrl(item, sheet.path)).find(Boolean)
        if (!family || !url) {
          skip('字体文件未映射', family)
          return
        }
        if (!fonts.some(font => font.family === family && font.weight === decls['font-weight'])) {
          fonts.push({ family, url, ...(decls['font-weight'] && decls['font-weight'] !== 'normal' ? { weight: decls['font-weight'].trim() } : {}), ...(decls['font-style']?.trim() === 'italic' ? { style: 'italic' as const } : {}) })
        }
      }
    })
  }

  // ===== 第二遍：样式规则 → 样式类 =====
  const entries = new Map<string, ClassEntry>()
  const assignments = new Map<Element, Set<string>>()
  const rootClassNames: string[] = []
  let order = 0
  const entryFor = (name: string, spec: [number, number, number], generated: boolean) => {
    let entry = entries.get(name)
    if (!entry) {
      entry = { item: { name, styles: {} }, specificity: spec, order: order++, generated }
      entries.set(name, entry)
    }
    return entry
  }
  const assign = (element: Element, name: string) => {
    let set = assignments.get(element)
    if (!set) {
      set = new Set()
      assignments.set(element, set)
    }
    set.add(name)
  }
  const merge = (entry: ClassEntry, styles: Record<string, string>, state: StudioStyleState | null, breakpoint: 'tablet' | 'mobile' | null) => {
    if (!Object.keys(styles).length) {
      return
    }
    const item = entry.item
    if (breakpoint) {
      item.responsive ??= {}
      item.responsive[breakpoint] = { ...item.responsive[breakpoint], ...styles }
    }
    else if (state) {
      item.states ??= {}
      item.states[state] = { ...item.states[state], ...styles }
    }
    else {
      item.styles = { ...item.styles, ...styles }
    }
  }
  const handleRule = (rule: Rule, base: string, breakpoint: 'tablet' | 'mobile' | null) => {
    const decls = rule.nodes.filter((node): node is Declaration => node.type === 'decl')
    if (!decls.length) {
      return
    }
    for (const rawSelector of rule.selectors) {
      report.rules++
      let selector = rawSelector.trim()
      if (PSEUDO_ELEMENT.test(selector)) {
        skip('伪元素规则', selector)
        continue
      }
      for (const name of currentClasses) {
        selector = selector.replace(new RegExp(`\\.${name}(?![\\w-])`, 'g'), '[aria-current="page"]')
      }
      let state: StudioStyleState | null = null
      for (const [pattern, value] of STATE_SUFFIXES) {
        if (pattern.test(selector)) {
          state = value
          selector = selector.replace(pattern, '') || '*'
          break
        }
      }
      if (state && breakpoint) {
        skip('断点内的状态样式', rawSelector)
        continue
      }
      if (DYNAMIC_PSEUDO.test(selector) || /\[aria-(?:current|pressed)[^\]]*\](?=.*[\s>+~])/.test(selector)) {
        skip('祖先/中间状态选择器', rawSelector)
        continue
      }
      const spec = specificity(selector)
      // :root/html/body：变量进主题变量；其余声明作为根样式类挂到各区域顶层节点
      if (ROOT_SELECTOR.test(selector)) {
        let matches = false
        try {
          matches = doc.documentElement.matches(selector) || doc.body.matches(selector) || /^:root/i.test(selector)
        }
        catch {
          skip('无效选择器', rawSelector)
          continue
        }
        if (!matches || state) {
          continue
        }
        const styles = styleFrom(decls, base, selector)
        for (const [prop, value] of Object.entries(styles)) {
          if (prop.startsWith('--')) {
            if (breakpoint) {
              skip('断点内的变量（主题变量不分断点）', `${selector} ${prop}`)
            }
            else {
              tokens[prop.slice(2)] = value
            }
            delete styles[prop]
          }
        }
        if (styles.background || styles['background-color']) {
          if (!breakpoint) {
            tokens.background = styles['background-color'] ?? styles.background!
          }
          delete styles.background
          delete styles['background-color']
        }
        for (const prop of Object.keys(styles)) {
          if (!INHERITED.test(prop)) {
            skip('根元素的非继承样式', `${selector} ${prop}`)
            delete styles[prop]
          }
        }
        if (Object.keys(styles).length) {
          const name = `site-root-${hash(selector)}`
          const entry = entryFor(name, spec, true)
          merge(entry, styles, null, breakpoint)
          if (!rootClassNames.includes(name)) {
            rootClassNames.push(name)
          }
        }
        continue
      }
      const simple = /^\.([a-z][a-z0-9-]{0,63})$/.exec(selector)
      if (simple && !currentClasses.has(simple[1]!)) {
        const entry = entryFor(simple[1]!, spec, false)
        merge(entry, styleFrom(decls, base, rawSelector), state, breakpoint)
        continue
      }
      let matched: Element[]
      try {
        matched = [...doc.querySelectorAll(selector)]
      }
      catch {
        skip('无效选择器', rawSelector)
        continue
      }
      if (!matched.length) {
        // 状态类选择器（如 [aria-current]）在快照里可能尚未命中：去掉状态属性再匹配以挂类
        skip('当前网页未命中的规则', rawSelector)
        continue
      }
      const name = `x-${hash(selector)}`
      const entry = entryFor(name, spec, true)
      merge(entry, styleFrom(decls, base, rawSelector), state, breakpoint)
      matched.forEach(element => assign(element, name))
    }
  }
  for (const sheet of sheets) {
    for (const node of sheet.root.nodes) {
      if (node.type === 'rule') {
        handleRule(node, sheet.path, null)
      }
      else if (node.type === 'atrule') {
        const at = node as AtRule
        const name = at.name.toLowerCase()
        if (name === 'media') {
          const breakpoint = breakpointOf(at.params)
          if (breakpoint === 'skip' || breakpoint === null) {
            skip('不支持的媒体查询', `@media ${at.params}`)
            continue
          }
          at.each((child) => {
            if (child.type === 'rule') {
              handleRule(child, sheet.path, breakpoint)
            }
          })
        }
        else if (!['keyframes', 'font-face', 'charset'].includes(name) && !name.endsWith('-keyframes')) {
          skip(`不支持的 @${name}`, at.params)
        }
      }
    }
  }

  // ===== DOM → 节点 =====
  const usedClasses = new Set<string>()
  const ids = new Set<string>()
  let count = 0
  const uniqueId = () => {
    let id = crypto.randomUUID()
    while (ids.has(id)) {
      id = crypto.randomUUID()
    }
    ids.add(id)
    return id
  }
  const convert = (element: Element, depth: number, top: boolean): StudioNode | null => {
    const tag = element.tagName.toLowerCase()
    if (!ALLOWED_TAGS.has(tag)) {
      if (!DROP_SILENTLY.has(tag)) {
        report.droppedElements[tag] = (report.droppedElements[tag] ?? 0) + 1
      }
      return null
    }
    if (depth > STUDIO_LIMITS.depth || ++count > STUDIO_LIMITS.nodes) {
      report.droppedElements['超出节点上限'] = (report.droppedElements['超出节点上限'] ?? 0) + 1
      return null
    }
    const attributes: Record<string, string> = {}
    for (const attr of element.attributes) {
      const key = attr.name.toLowerCase()
      if (key === 'style' || key === 'class' || key.startsWith('data-studio-')) {
        continue
      }
      let value = attr.value
      if (['src', 'poster'].includes(key) && value && !value.startsWith('data:')) {
        const mapped = /^https?:\/\//i.test(value) ? value : mapUrl(value, '/')
        if (!mapped) {
          continue
        }
        value = mapped
      }
      try {
        validateStudioAttributes({ [key]: value }, 'import')
        attributes[key] = value
      }
      catch {
        skip('不允许的属性', `${tag}[${key}]`)
      }
    }
    const classList = [...element.classList].filter(name => !currentClasses.has(name))
    const own = classList.filter(name => STUDIO_CLASS_NAME.test(name))
    const generated = [...(assignments.get(element) ?? [])]
    const classes = [...new Set([...(top ? rootClassNames : []), ...own, ...generated])]
    if (currentClasses.size && [...element.classList].some(name => currentClasses.has(name)) && tag === 'a') {
      // 运行时会按链接与当前页面重新设置 aria-current
      delete attributes['aria-current']
    }
    if (classes.length) {
      attributes.class = classes.join(' ')
      classes.forEach(name => usedClasses.add(name))
    }
    const styles: Record<string, string> = {}
    const inline = element.getAttribute('style')
    if (inline) {
      try {
        const root = parse(`a{${inline}}`)
        const rule = root.first as Rule | undefined
        Object.assign(styles, styleFrom((rule?.nodes ?? []).filter((node): node is Declaration => node.type === 'decl'), '/', `${tag}[style]`))
      }
      catch {
        skip('无法解析的内联样式', inline)
      }
    }
    const node: StudioNode = { id: uniqueId(), type: tag === 'img' ? 'image' : tag === 'a' ? 'link' : /^h[1-6]$/.test(tag) ? 'heading' : tag === 'p' ? 'text' : tag, tag, attributes, styles, children: [] }
    if (STUDIO_VOID_TAGS.has(tag)) {
      return node
    }
    const hasElements = element.children.length > 0
    if (!hasElements) {
      node.text = element.textContent ?? ''
      return node
    }
    for (const child of element.childNodes) {
      if (child.nodeType === 1) {
        const converted = convert(child as Element, depth + 1, false)
        if (converted) {
          node.children.push(converted)
        }
      }
      else if (child.nodeType === 3 && child.textContent && child.textContent.trim()) {
        count++
        node.children.push({ id: uniqueId(), type: 'text', tag: 'span', text: child.textContent.replace(/\s+/g, ' '), attributes: {}, styles: {}, children: [] })
      }
    }
    return node
  }
  const region = (selector?: string) => {
    if (!selector) {
      return []
    }
    const nodes: StudioNode[] = []
    for (const element of doc.querySelectorAll(selector)) {
      const converted = convert(element, 1, true)
      if (converted) {
        nodes.push(converted)
      }
    }
    validateStudioNodes(nodes)
    return nodes
  }
  const header = region(options.regions.header)
  const footer = region(options.regions.footer)
  const page = region(options.regions.page)

  // 只保留被引用的样式类（简单类规则可能服务于本页没有的元素：保留，供后续页面导入复用）
  const classes = [...entries.values()]
    .filter(entry => !entry.generated || usedClasses.has(entry.item.name))
    .filter(entry => Object.keys(entry.item.styles).length || entry.item.states || entry.item.responsive)
    .sort((a, b) => a.specificity[0] - b.specificity[0] || a.specificity[1] - b.specificity[1] || a.specificity[2] - b.specificity[2] || a.order - b.order)
    .map(entry => entry.item)
  report.classes = classes.length
  report.generatedClasses = classes.filter(item => item.name.startsWith('x-') || item.name.startsWith('site-root-')).length
  report.unresolvedAssets = [...unresolved]
  return { page, header, footer, classes, keyframes, fonts, tokens, report }
}

/**
 * 合并导入的样式类到现有主题：同名类以导入结果为准，新类按导入顺序追加在已有类之后；
 * 多页导入共享网页样式时，生成类名由选择器决定，同一规则不会重复。
 */
export function mergeStudioClasses(existing: StudioStyleClass[], incoming: StudioStyleClass[]) {
  const byName = new Map(existing.map(item => [item.name, item]))
  const merged = existing.map(item => incoming.find(next => next.name === item.name) ?? item)
  for (const item of incoming) {
    if (!byName.has(item.name)) {
      merged.push(item)
    }
  }
  return merged
}

export function mergeStudioFonts(existing: StudioFontFace[], incoming: StudioFontFace[]) {
  return [...existing.filter(font => !incoming.some(next => next.family === font.family && next.weight === font.weight)), ...incoming]
}
