import type { SiteDocument } from '../types/site-studio'
import { buildStudioSiteHtml } from './site-studio-html'

/**
 * 设计助手的渲染检查（类比代码代理改完跑 lint/测试）：把当前页面的真实编译 HTML 放进不执行脚本的隐藏 iframe，
 * 按手机/桌面宽度测量版面问题，返回可定位到节点的清单。静态渲染：不执行数据绑定与站点功能，列表显示作者示例。
 */

export type StudioCheckViewport = 'mobile' | 'desktop'
export const STUDIO_CHECK_WIDTHS: Record<StudioCheckViewport, number> = { mobile: 390, desktop: 1440 }

export interface StudioCheckIssue {
  viewport: StudioCheckViewport
  kind: 'overflow' | 'broken-image' | 'contrast' | 'tap-target' | 'clipped-text' | 'fixed-cover'
  nodeId: string | null
  detail: string
}

type Rgba = [number, number, number, number]

/** 解析计算样式颜色（rgb/rgba）；其他颜色空间返回 null（不判定） */
export function parseCssColor(value: string): Rgba | null {
  const match = /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)(?:\s*[,/]\s*([\d.]+%?))?\s*\)$/.exec(value.trim())
  if (!match) {
    return null
  }
  const alpha = match[4] === undefined ? 1 : match[4].endsWith('%') ? Number.parseFloat(match[4]) / 100 : Number(match[4])
  return [Number(match[1]), Number(match[2]), Number(match[3]), alpha]
}

function luminance([r, g, b]: Rgba) {
  const channel = (value: number) => {
    const s = value / 255
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/** WCAG 对比度；前景带透明度时先与背景合成 */
export function contrastRatio(foreground: Rgba, background: Rgba) {
  const a = foreground[3]
  const blended: Rgba = [0, 1, 2].map(index => foreground[index]! * a + background[index]! * (1 - a)) as unknown as Rgba
  blended[3] = 1
  const [light, dark] = [luminance(blended), luminance(background)].sort((x, y) => y - x)
  return (light! + 0.05) / (dark! + 0.05)
}

function timeout<T>(promise: Promise<T>, ms: number) {
  return Promise.race([promise, new Promise<void>(resolve => window.setTimeout(resolve, ms))])
}

async function render(html: string, width: number) {
  const frame = window.document.createElement('iframe')
  frame.setAttribute('sandbox', 'allow-same-origin')
  frame.setAttribute('aria-hidden', 'true')
  frame.tabIndex = -1
  Object.assign(frame.style, { position: 'fixed', left: '-20000px', top: '0', width: `${width}px`, height: '900px', border: '0', pointerEvents: 'none' })
  const loaded = new Promise<void>(resolve => frame.addEventListener('load', () => resolve(), { once: true }))
  frame.srcdoc = html
  window.document.body.appendChild(frame)
  await timeout(loaded, 8000)
  const doc = frame.contentDocument
  if (doc) {
    await timeout(doc.fonts?.ready ?? Promise.resolve(), 3000)
    const pending = [...doc.images].filter(image => !image.complete).map(image => new Promise<void>((resolve) => {
      image.addEventListener('load', () => resolve(), { once: true })
      image.addEventListener('error', () => resolve(), { once: true })
    }))
    await timeout(Promise.all(pending), 4000)
  }
  return frame
}

function ownText(element: Element) {
  return [...element.childNodes].some(node => node.nodeType === 3 && node.textContent?.trim())
}

function measure(frame: HTMLIFrameElement, viewport: StudioCheckViewport, width: number): StudioCheckIssue[] {
  const doc = frame.contentDocument
  const win = frame.contentWindow
  if (!doc || !win) {
    return []
  }
  const issues: StudioCheckIssue[] = []
  const add = (kind: StudioCheckIssue['kind'], element: Element | null, detail: string, cap: number) => {
    if (issues.filter(item => item.kind === kind).length < cap) {
      issues.push({ viewport, kind, nodeId: element?.closest('[data-studio-id]')?.getAttribute('data-studio-id') ?? null, detail })
    }
  }
  const elements = [...doc.querySelectorAll('[data-studio-id]')]
  const visible = (element: Element) => {
    const style = win.getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) > 0 && rect.width > 0 && rect.height > 0
  }
  // 横向溢出：只报最外层越界元素
  if (doc.documentElement.scrollWidth > width + 1) {
    for (const element of elements) {
      const rect = element.getBoundingClientRect()
      const parent = element.parentElement?.closest('[data-studio-id]')
      if (rect.right > width + 1 && (!parent || parent.getBoundingClientRect().right <= width + 1) && visible(element)) {
        add('overflow', element, `超出 ${width}px 视口 ${Math.round(rect.right - width)}px，页面出现横向滚动`, 8)
      }
    }
  }
  for (const image of doc.images) {
    if (image.getAttribute('src') && image.complete && image.naturalWidth === 0) {
      add('broken-image', image, `图片无法加载：${(image.getAttribute('src') || '').slice(0, 80)}`, 8)
    }
  }
  for (const element of elements) {
    if (!ownText(element) || !visible(element)) {
      continue
    }
    const style = win.getComputedStyle(element)
    // 文本截断：设置了 overflow 隐藏且内容更宽
    if (['hidden', 'clip'].includes(style.overflowX) && element.scrollWidth > element.clientWidth + 2) {
      add('clipped-text', element, `文本被截断（内容 ${element.scrollWidth}px > 可见 ${element.clientWidth}px）`, 8)
    }
    const foreground = parseCssColor(style.color)
    let background: Rgba | null = null
    let unknown = false
    for (let current: Element | null = element; current; current = current.parentElement) {
      const own = win.getComputedStyle(current)
      if (own.backgroundImage && own.backgroundImage !== 'none') {
        unknown = true
        break
      }
      const color = parseCssColor(own.backgroundColor)
      if (color === null && own.backgroundColor !== 'transparent') {
        unknown = true
        break
      }
      if (color && color[3] > 0.95) {
        background = color
        break
      }
    }
    // 带文字阴影/描边的文字（如像素风数字）可读性不由背景对比决定，比值会误报，跳过
    const outlined = (style.textShadow && style.textShadow !== 'none') || (style.webkitTextStroke && !style.webkitTextStroke.startsWith('0px'))
    if (!unknown && !outlined && foreground) {
      const ratio = contrastRatio(foreground, background ?? [255, 255, 255, 1])
      const size = Number.parseFloat(style.fontSize)
      const large = size >= 24 || (size >= 18.66 && Number.parseInt(style.fontWeight, 10) >= 700)
      if (ratio < (large ? 3 : 4.5)) {
        add('contrast', element, `文字对比度 ${ratio.toFixed(2)}:1 低于 ${large ? 3 : 4.5}:1（${style.color} / ${background ? `rgb(${background.slice(0, 3).join(',')})` : '默认白底'}）`, 10)
      }
    }
  }
  if (viewport === 'mobile') {
    for (const element of doc.querySelectorAll('a[data-studio-id], button[data-studio-id]')) {
      const style = win.getComputedStyle(element)
      const rect = element.getBoundingClientRect()
      if (style.display !== 'inline' && visible(element) && (rect.width < 32 || rect.height < 32)) {
        add('tap-target', element, `点击区域 ${Math.round(rect.width)}×${Math.round(rect.height)}px，手机上建议至少 32×32`, 10)
      }
    }
    const area = width * 900
    for (const element of elements) {
      const style = win.getComputedStyle(element)
      // 只报会挡住操作的固定层：装饰性背景（aria-hidden 或不接收指针）不算
      if (style.position === 'fixed' && style.pointerEvents !== 'none' && !element.closest('[aria-hidden="true"]') && visible(element)) {
        const rect = element.getBoundingClientRect()
        if (rect.width * rect.height > area * 0.35) {
          add('fixed-cover', element, `固定定位元素覆盖手机屏幕约 ${Math.round(rect.width * rect.height / area * 100)}%`, 4)
        }
      }
    }
  }
  return issues
}

export async function checkStudioPage(document: SiteDocument, pageId: string, viewports: StudioCheckViewport[] = ['mobile', 'desktop']) {
  const html = buildStudioSiteHtml(document, pageId)
  const issues: StudioCheckIssue[] = []
  for (const viewport of viewports) {
    const width = STUDIO_CHECK_WIDTHS[viewport]
    const frame = await render(html, width)
    try {
      issues.push(...measure(frame, viewport, width))
    }
    finally {
      frame.remove()
    }
  }
  const counts = issues.reduce<Record<string, number>>((all, issue) => ({ ...all, [issue.kind]: (all[issue.kind] ?? 0) + 1 }), {})
  return {
    passed: issues.length === 0,
    counts,
    issues,
    note: '静态渲染检查：不执行数据绑定与站点功能，列表显示作者示例；只报告可定位的版面问题，修正后可再次检查。',
  }
}
