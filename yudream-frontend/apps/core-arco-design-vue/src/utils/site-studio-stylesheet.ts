import type { AtRule, Declaration, Rule } from 'postcss'
import type { SiteDocument, StudioAsset, StudioFontFace, StudioKeyframes, StudioStyleClass, StudioStyleState } from '../types/site-studio'
import { parse } from 'postcss'
import { STUDIO_STYLE_STATES } from '../types/site-studio'
import { STUDIO_BREAKPOINTS, STUDIO_CLASS_NAME, studioAssert, validateStudioStyle } from './site-studio-validation'

/**
 * 主题样式表：共享样式类、关键帧与字体的 CSS 文本形式，供工作台编辑与网页导入。
 * 只接受能无损映射到文档模型的子集：
 * - `.name{}`，状态 `.name:hover / :focus(-visible) / :active / [aria-current="page"] / [aria-pressed="true"]`
 * - `@media (max-width: 992px|768px){ .name{} }`（平板/手机断点）
 * - `@keyframes name{ from{} 50%{} to{} }`
 * - `@font-face{ font-family:"X"; src:url(资产地址); font-weight; font-style }`（地址须对应文档中的字体资产）
 * 严格模式遇到其他写法直接报错；导入模式（strict=false）跳过并记录，便于报告无法迁移的部分。
 */
export interface StudioStylesheet {
  classes: StudioStyleClass[]
  keyframes: StudioKeyframes[]
  fonts: StudioFontFace[]
}
export interface StudioStylesheetResult extends StudioStylesheet {
  skipped: string[]
}

const STATE_PATTERNS: [RegExp, StudioStyleState][] = [
  [/^:hover$/, 'hover'],
  [/^:focus(?:-visible)?$/, 'focus'],
  [/^:active$/, 'active'],
  [/^\[aria-current=(?:"page"|'page'|page)\]$/, 'current'],
  [/^\[aria-pressed=(?:"true"|'true'|true)\]$/, 'pressed'],
]
const STATE_SUFFIX: Record<StudioStyleState, string> = {
  hover: ':hover',
  focus: ':focus-visible',
  active: ':active',
  current: '[aria-current="page"]',
  pressed: '[aria-pressed="true"]',
}

/** `.name` 或 `.name<状态>` → [类名, 状态]；其余选择器返回 null */
export function parseStudioClassSelector(selector: string): [string, StudioStyleState | null] | null {
  const match = /^\.([a-z][a-z0-9-]{0,63})(.*)$/.exec(selector.trim())
  if (!match) {
    return null
  }
  const suffix = match[2]!.trim()
  if (!suffix) {
    return [match[1]!, null]
  }
  const state = STATE_PATTERNS.find(([pattern]) => pattern.test(suffix))?.[1]
  return state ? [match[1]!, state] : null
}

function declarations(nodes: Declaration[], skipped: string[], strict: boolean, where: string) {
  const styles: Record<string, string> = {}
  for (const decl of nodes) {
    const value = `${decl.value}${decl.important ? ' !important' : ''}`
    try {
      validateStudioStyle(decl.prop, value)
      styles[decl.prop] = value
    }
    catch (error) {
      const message = `${where} ${decl.prop}: ${error instanceof Error ? error.message : '不支持'}`
      studioAssert(!strict, message)
      skipped.push(message)
    }
  }
  return styles
}

function unquote(value: string) {
  return value.trim().replace(/^(['"])(.*)\1$/, '$2')
}

export function parseStudioStylesheet(css: string, assets: StudioAsset[], options: { strict?: boolean } = {}): StudioStylesheetResult {
  const strict = options.strict !== false
  const skipped: string[] = []
  const reject = (message: string) => {
    studioAssert(!strict, message)
    skipped.push(message)
  }
  studioAssert(!/[<>\\]/.test(css), '样式表不能包含 HTML 或 CSS 转义')
  const root = parse(css)
  const classes = new Map<string, StudioStyleClass>()
  const keyframes: StudioKeyframes[] = []
  const fonts: StudioFontFace[] = []
  const classOf = (name: string) => {
    let item = classes.get(name)
    if (!item) {
      item = { name, styles: {} }
      classes.set(name, item)
    }
    return item
  }
  const applyRule = (rule: Rule, breakpoint?: 'tablet' | 'mobile') => {
    const decls = rule.nodes.filter((node): node is Declaration => node.type === 'decl')
    for (const selector of rule.selectors) {
      const parsed = parseStudioClassSelector(selector)
      if (!parsed || (breakpoint && parsed[1])) {
        reject(`不支持的选择器：${selector}${breakpoint && parsed ? '（断点内只支持基础样式）' : ''}`)
        continue
      }
      const [name, state] = parsed
      const styles = declarations(decls, skipped, strict, selector)
      const item = classOf(name)
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
  }
  for (const node of root.nodes) {
    if (node.type === 'comment') {
      continue
    }
    if (node.type === 'rule') {
      applyRule(node)
      continue
    }
    if (node.type !== 'atrule') {
      continue
    }
    const at = node as AtRule
    const name = at.name.toLowerCase()
    if (name === 'media') {
      const width = /^\(\s*max-width\s*:\s*(\d+)px\s*\)$/i.exec(at.params.trim())?.[1]
      const breakpoint = width === String(STUDIO_BREAKPOINTS.tablet) ? 'tablet' : width === String(STUDIO_BREAKPOINTS.mobile) ? 'mobile' : undefined
      if (!breakpoint) {
        reject(`不支持的媒体查询：@media ${at.params}（仅 max-width ${STUDIO_BREAKPOINTS.tablet}px / ${STUDIO_BREAKPOINTS.mobile}px）`)
        continue
      }
      at.each((child) => {
        if (child.type === 'rule') {
          applyRule(child, breakpoint)
        }
      })
    }
    else if (name === 'keyframes') {
      const keyframeName = at.params.trim()
      if (!STUDIO_CLASS_NAME.test(keyframeName)) {
        reject(`动画名须为小写字母开头：@keyframes ${keyframeName}`)
        continue
      }
      const frames: Record<string, Record<string, string>> = {}
      at.each((child) => {
        if (child.type === 'rule') {
          const styles = declarations(child.nodes.filter((item): item is Declaration => item.type === 'decl'), skipped, strict, `@keyframes ${keyframeName}`)
          for (const key of child.selectors) {
            frames[key.trim()] = { ...frames[key.trim()], ...styles }
          }
        }
      })
      keyframes.push({ name: keyframeName, frames })
    }
    else if (name === 'font-face') {
      const decls = Object.fromEntries(at.nodes?.filter((item): item is Declaration => item.type === 'decl').map(decl => [decl.prop.toLowerCase(), decl.value]) ?? [])
      const family = unquote(decls['font-family'] ?? '')
      const url = /url\(\s*(['"]?)([^'")\s]+)\1\s*\)/.exec(decls.src ?? '')?.[2]
      const asset = url ? assets.find(item => item.kind === 'font' && item.url === url) : undefined
      if (!family || !asset) {
        reject(`@font-face「${family || '未命名'}」需要引用文档中已有的字体资产`)
        continue
      }
      const weight = decls['font-weight']?.trim()
      const style = decls['font-style']?.trim()
      fonts.push({ family, assetId: asset.id, ...(weight && weight !== 'normal' ? { weight } : {}), ...(style === 'italic' ? { style: 'italic' as const } : {}) })
    }
    else {
      reject(`不支持的 at-rule：@${at.name}`)
    }
  }
  return { classes: [...classes.values()], keyframes, fonts, skipped }
}

function block(styles: Record<string, string>, indent = '  ') {
  return Object.entries(styles).map(([key, value]) => `${indent}${key}: ${value};`).join('\n')
}

/** 文档主题 → 可编辑样式表文本（字体、关键帧、样式类及其状态与断点） */
export function formatStudioStylesheet(document: Pick<SiteDocument, 'theme' | 'assets'>): string {
  const assets = new Map(document.assets.map(asset => [asset.id, asset]))
  const parts: string[] = []
  for (const font of document.theme.fonts ?? []) {
    const asset = assets.get(font.assetId)
    if (asset) {
      parts.push(`@font-face {\n  font-family: "${font.family}";\n  src: url("${asset.url}");${font.weight ? `\n  font-weight: ${font.weight};` : ''}${font.style ? `\n  font-style: ${font.style};` : ''}\n}`)
    }
  }
  for (const item of document.theme.keyframes ?? []) {
    parts.push(`@keyframes ${item.name} {\n${Object.entries(item.frames).map(([key, styles]) => `  ${key} {\n${block(styles, '    ')}\n  }`).join('\n')}\n}`)
  }
  const tablet: string[] = []
  const mobile: string[] = []
  for (const item of document.theme.classes ?? []) {
    parts.push(`.${item.name} {\n${block(item.styles)}\n}`)
    for (const state of STUDIO_STYLE_STATES) {
      const styles = item.states?.[state]
      if (styles) {
        parts.push(`.${item.name}${STATE_SUFFIX[state]} {\n${block(styles)}\n}`)
      }
    }
    if (item.responsive?.tablet) {
      tablet.push(`  .${item.name} {\n${block(item.responsive.tablet, '    ')}\n  }`)
    }
    if (item.responsive?.mobile) {
      mobile.push(`  .${item.name} {\n${block(item.responsive.mobile, '    ')}\n  }`)
    }
  }
  if (tablet.length) {
    parts.push(`@media (max-width: ${STUDIO_BREAKPOINTS.tablet}px) {\n${tablet.join('\n')}\n}`)
  }
  if (mobile.length) {
    parts.push(`@media (max-width: ${STUDIO_BREAKPOINTS.mobile}px) {\n${mobile.join('\n')}\n}`)
  }
  return parts.join('\n\n')
}
